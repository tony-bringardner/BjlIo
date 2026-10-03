/**
 * <PRE>
 *
 * Copyright Tony Bringarder 1998, 2025 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 *
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *
 *
 *	@author Tony Bringardner
 */
package us.bringardner.io.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.io.ContinuousInputStream;
import us.bringardner.io.LineTooLongException;

/**
 * ContinuousInputStream follows a file that is moved (or deleted) and replaced, limits the length
 * of a line, and keeps a line interrupted part way through.
 */
public class TestContinuousFollow {

	@TempDir
	File tempDir;

	private static void write(File file, String text) throws IOException {
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private static void append(File file, String text) throws IOException {
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
	}

	/** Whether this file system identifies files (needed to notice that a file was replaced). */
	private boolean hasFileKeys() throws IOException {
		Path p = Files.createTempFile(tempDir.toPath(), "key", ".tmp");
		return Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class).fileKey() != null;
	}

	@Test
	public void testFollowsAMovedAndReplacedFile() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(hasFileKeys(), "The file system doesn't identify files");
		File log = new File(tempDir, "app.log");
		write(log, "one\n");
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
			try(ContinuousInputStream in = new ContinuousInputStream(log, false)) {
				in.setFreq(5);
				assertEquals("one", in.readLine());

				//  Written just before the rotation: still read from the old file
				append(log, "two\n");
				//  logrotate's default: rename the file, then create a new one
				Files.move(log.toPath(), new File(tempDir, "app.log.1").toPath());
				write(log, "three\n");

				assertEquals("two", in.readLine(), "The rest of the old file is read first");
				assertEquals("three", in.readLine(), "Then the new file, from its beginning");
				append(log, "four\n");
				assertEquals("four", in.readLine());
			}
		});
	}

	@Test
	public void testFollowsADeletedAndRecreatedFile() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(hasFileKeys(), "The file system doesn't identify files");
		File log = new File(tempDir, "app.log");
		write(log, "old\n");
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
			try(ContinuousInputStream in = new ContinuousInputStream(log.getPath(), false)) {
				in.setFreq(5);
				assertEquals("old", in.readLine());
				Files.delete(log.toPath());
				//  Nothing there for a while: keep waiting
				AtomicReference<Object> result = new AtomicReference<>();
				Thread reader = new Thread(() -> {
					try {
						result.set(in.readLine());
					} catch (Throwable e) {
						result.set(e);
					}
				});
				reader.start();
				Thread.sleep(100);
				assertTrue(reader.isAlive(), "Should wait for the file to come back");
				write(log, "new\n");
				reader.join(5000);
				assertEquals("new", result.get());
			}
		});
	}

	@Test
	public void testMovedFileWithoutAReplacementIsStillRead() throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(hasFileKeys(), "The file system doesn't identify files");
		File log = new File(tempDir, "app.log");
		write(log, "a\n");
		File moved = new File(tempDir, "moved.log");
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
			try(ContinuousInputStream in = new ContinuousInputStream(log, false)) {
				in.setFreq(5);
				assertEquals("a", in.readLine());
				//  Moved but not replaced: whatever still writes to it (by its open handle) is read
				Files.move(log.toPath(), moved.toPath());
				append(moved, "b\n");
				assertEquals("b", in.readLine());
			}
		});
	}

	@Test
	public void testMaxLineLength() throws IOException {
		File file = new File(tempDir, "lines.txt");
		write(file, "12345\r\n123456\nok\n");
		try(ContinuousInputStream in = new ContinuousInputStream(file, false)) {
			in.setEof(true);
			assertEquals(0, in.getMaxLineLength());
			in.setMaxLineLength(5);
			assertEquals(5, in.getMaxLineLength());
			assertEquals("12345", in.readLine(), "A line of exactly the limit (and its CR) is fine");
			LineTooLongException e = assertThrows(LineTooLongException.class, in::readLine);
			assertEquals(5, e.getMaxLineLength());
			assertEquals("ok", in.readLine(), "The reader can go on after a long line");
			assertNull(in.readLine());
		}

		//  A line with no terminator at all (a binary file, say) is stopped at the limit
		File binary = new File(tempDir, "binary.dat");
		Files.write(binary.toPath(), new byte[100_000]);
		try(ContinuousInputStream in = new ContinuousInputStream(binary, false)) {
			in.setEof(true);
			in.setMaxLineLength(1000);
			assertThrows(LineTooLongException.class, in::readLine);
		}
	}

	@Test
	public void testInterruptedLineIsKept() throws Exception {
		File file = new File(tempDir, "partial.txt");
		write(file, "par");
		try(ContinuousInputStream in = new ContinuousInputStream(file, false)) {
			in.setFreq(5);
			AtomicReference<Object> result = new AtomicReference<>();
			Thread reader = new Thread(() -> {
				try {
					result.set(in.readLine());
				} catch (Throwable e) {
					result.set(e);
				}
			});
			reader.start();
			Thread.sleep(100);
			reader.interrupt();
			reader.join(5000);
			assertTrue(result.get() instanceof InterruptedIOException, "Expected InterruptedIOException but got "+result.get());

			append(file, "tial\n");
			in.setEof(true);
			assertEquals("partial", in.readLine(), "The part read before the interrupt should not be lost");
		}
	}

	@Test
	public void testNoSynchronizedMethods() {
		//  A read waits a long time; on Java 21-23 a virtual thread waiting in a synchronized
		//  method holds on to its carrier thread (BJL-55)
		for(Method m : ContinuousInputStream.class.getDeclaredMethods()) {
			assertFalse(Modifier.isSynchronized(m.getModifiers()), m+" should not be synchronized");
		}
	}
}
