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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.io.AbstractLineReader;
import us.bringardner.io.AbstractLineWriter;
import us.bringardner.io.CRLFLineReader;
import us.bringardner.io.CRLFLineWriter;
import us.bringardner.io.ContinuousInputStream;
import us.bringardner.io.IStreamMonitor;
import us.bringardner.io.IoConstants;
import us.bringardner.io.LFLineReader;
import us.bringardner.io.LFLineWriter;
import us.bringardner.io.LineTooLongException;
import us.bringardner.io.MonitoredInputStream;
import us.bringardner.io.MonitoredOutputStream;
import us.bringardner.io.TeeOutputStream;
import us.bringardner.io.TelnetInputStream;
import us.bringardner.io.TelnetOutputStream;

/**
 * Tests for the code paths the other tests don't reach (constructors, the InputStream /
 * OutputStream methods other than readLine / writeLine, and error handling).
 * Written from the JaCoCo report, see target/site/jacoco/index.html after 'mvn verify'.
 */
class TestCoverage {

	private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;

	@TempDir
	File tempDir;

	/** Records every call so the tests can check what the monitored streams reported. */
	static class RecordingMonitor implements IStreamMonitor {
		int starts;
		List<Long> updates = new ArrayList<>();
		List<Long> completes = new ArrayList<>();

		@Override
		public void start() {
			starts++;
		}

		@Override
		public void update(long total, long transfered) {
			updates.add(total);
		}

		@Override
		public void complete(long total) {
			completes.add(total);
		}
	}

	/** An OutputStream where every operation fails. */
	static class FailingOutputStream extends OutputStream {
		private final boolean runtime;

		FailingOutputStream(boolean runtime) {
			this.runtime = runtime;
		}

		private void fail(String op) throws IOException {
			if( runtime ) {
				throw new IllegalStateException(op);
			}
			throw new IOException(op);
		}

		@Override
		public void write(int b) throws IOException {
			fail("write");
		}

		@Override
		public void flush() throws IOException {
			fail("flush");
		}

		@Override
		public void close() throws IOException {
			fail("close");
		}
	}

	private File writeFile(String name, String content) throws IOException {
		File file = new File(tempDir, name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	private static String repeat(char c, int count) {
		char [] chars = new char[count];
		Arrays.fill(chars, c);
		return new String(chars);
	}

	// ------------------------------------------------------------------------
	//  IoConstants
	// ------------------------------------------------------------------------

	@Test
	@SuppressWarnings("deprecation")
	void testIoConstants() {
		assertArrayEquals(new byte[] {'\r','\n'}, IoConstants.CRNL);
		byte [] crlf = IoConstants.crlf();
		assertArrayEquals(new byte[] {'\r','\n'}, crlf);
		//  Every call returns a new copy, so changing one can't affect anyone else.
		crlf[0] = 'x';
		assertArrayEquals(new byte[] {'\r','\n'}, IoConstants.crlf());
		assertEquals('\r', IoConstants.CR);
		assertEquals('\n', IoConstants.NL);
	}

	// ------------------------------------------------------------------------
	//  Line readers
	// ------------------------------------------------------------------------

	@Test
	void testLineReaderConstructors() throws IOException {
		File file = writeFile("lf.txt", "caf\u00e9\nline2\n");

		try(LFLineReader r = new LFLineReader(file)) {
			assertEquals(StandardCharsets.UTF_8, r.getCharset());
			assertEquals("caf\u00e9", r.readLine());
		}
		try(LFLineReader r = new LFLineReader(file, LATIN1)) {
			assertEquals(LATIN1, r.getCharset());
			//  The two UTF-8 bytes of the e acute are decoded as two Latin-1 chars.
			assertEquals("caf\u00c3\u00a9", r.readLine());
		}
		try(LFLineReader r = new LFLineReader(new ByteArrayInputStream("a\n".getBytes(LATIN1)), null)) {
			//  null means the default Charset.
			assertEquals(AbstractLineReader.DEFAULT_CHARSET, r.getCharset());
		}

		File crlf = writeFile("crlf.txt", "one\r\ntwo\r\n");
		try(CRLFLineReader r = new CRLFLineReader(crlf)) {
			assertEquals("one", r.readLine());
			assertEquals("two", r.readLine());
			assertNull(r.readLine());
		}
		try(CRLFLineReader r = new CRLFLineReader(crlf, LATIN1)) {
			assertEquals(LATIN1, r.getCharset());
			assertEquals("one", r.readLine());
		}
		try(CRLFLineReader r = new CRLFLineReader(new ByteArrayInputStream("x\r\n".getBytes(LATIN1)), LATIN1)) {
			assertEquals("x", r.readLine());
		}
		try(CRLFLineReader r = new CRLFLineReader("s1\r\ns2")) {
			assertEquals("s1", r.readLine());
			assertEquals("s2", r.readLine());
		}

		assertThrows(NullPointerException.class, () -> new LFLineReader((InputStream)null));
	}

	@Test
	void testCrlfReaderDropsTrailingCrAtEof() throws IOException {
		try(CRLFLineReader r = new CRLFLineReader("abc\r")) {
			assertEquals("abc", r.readLine());
			assertNull(r.readLine());
		}
	}

	@Test
	void testReaderStreamMethods() throws IOException {
		byte [] data = "hello\nworld".getBytes(StandardCharsets.US_ASCII);
		try(LFLineReader r = new LFLineReader(new ByteArrayInputStream(data))) {
			assertEquals(data.length, r.available());
			assertEquals(data.length, r.inputAvailable());
			assertEquals(0L, r.getLastReadTime());

			assertEquals('h', r.read());
			assertEquals(0, r.read(new byte[4], 0, 0));
			assertEquals(0L, r.skip(0));
			assertEquals(0L, r.skip(-1));
			//  Skip from the buffer ("el")
			assertEquals(2L, r.skip(2));

			byte [] b = new byte[3];
			assertEquals(3, r.read(b));
			assertArrayEquals("lo\n".getBytes(StandardCharsets.US_ASCII), b);

			//  Skip more than is buffered, only the buffered bytes are skipped.
			assertEquals(5L, r.skip(100));
			//  Nothing buffered, the skip goes to the underlying stream (which is at EOF).
			assertEquals(0L, r.skip(10));

			assertEquals(-1, r.read(b, 0, b.length));
			assertEquals(-1, r.read());
			assertNull(r.readLine());
			assertTrue(r.getLastReadTime() > 0);
			//  Every byte that was read or skipped is counted.
			assertEquals((long)data.length, r.getBytesIn());
		}
	}

	@Test
	void testReaderLargeReadGoesDirectlyToTheStream() throws IOException {
		byte [] data = new byte[IoConstants.DEFAULT_BUFFER_SIZE * 3];
		for (int idx = 0; idx < data.length; idx++) {
			data[idx] = (byte)idx;
		}
		try(CRLFLineReader r = new CRLFLineReader(new ByteArrayInputStream(data))) {
			byte [] b = new byte[data.length];
			int n = r.read(b, 0, b.length);
			assertEquals(data.length, n);
			assertArrayEquals(data, b);
			assertEquals(-1, r.read(b, 0, b.length));
			assertEquals((long)data.length, r.getBytesIn());
		}
	}

	@Test
	void testBytesInMatchesBytesOut() throws IOException {
		String [] lines = {"one", "", "caf\u00e9", "has a lone \n LF", "last"};

		ByteArrayOutputStream crlfOut = new ByteArrayOutputStream();
		long crlfWritten;
		try(CRLFLineWriter w = new CRLFLineWriter(crlfOut)) {
			for (String line : lines) {
				w.writeLine(line);
			}
			//  A final line without a terminator
			w.write("tail");
			crlfWritten = w.getBytesOut();
		}
		assertEquals(crlfOut.size(), crlfWritten);
		try(CRLFLineReader r = new CRLFLineReader(new ByteArrayInputStream(crlfOut.toByteArray()))) {
			for (String line : lines) {
				assertEquals(line, r.readLine());
			}
			assertEquals("tail", r.readLine());
			assertNull(r.readLine());
			assertEquals(crlfWritten, r.getBytesIn());
		}

		ByteArrayOutputStream lfOut = new ByteArrayOutputStream();
		long lfWritten;
		try(LFLineWriter w = new LFLineWriter(lfOut)) {
			w.writeLine("a");
			w.writeLine("");
			w.writeLine("b\r");
			lfWritten = w.getBytesOut();
		}
		try(LFLineReader r = new LFLineReader(new ByteArrayInputStream(lfOut.toByteArray()))) {
			while( r.readLine() != null ) {
				//  read everything
			}
			assertEquals(lfWritten, r.getBytesIn());
		}
	}

	@Test
	void testBytesInCountsEveryReadMethod() throws IOException {
		try(CRLFLineReader r = new CRLFLineReader("ab\r\ncd\r\nef")) {
			assertEquals(0L, r.getBytesIn());
			assertEquals("ab", r.readLine());
			//  The terminator is counted
			assertEquals(4L, r.getBytesIn());
			assertEquals('c', r.read());
			assertEquals(5L, r.getBytesIn());
			assertEquals(1L, r.skip(1));
			assertEquals(6L, r.getBytesIn());
			assertEquals("", r.readLine());
			assertEquals(8L, r.getBytesIn());
			byte [] b = new byte[10];
			assertEquals(2, r.read(b));
			assertEquals(10L, r.getBytesIn());
			assertNull(r.readLine());
			assertEquals(10L, r.getBytesIn());
		}

		//  Skipping past the buffer goes to the underlying stream, and is counted too.
		int size = IoConstants.DEFAULT_BUFFER_SIZE * 3;
		try(LFLineReader r = new LFLineReader(new ByteArrayInputStream(new byte[size]))) {
			assertEquals(100, r.read(new byte[100]));
			//  The rest of the first buffer fill
			assertEquals(IoConstants.DEFAULT_BUFFER_SIZE - 100L, r.skip(size));
			//  Nothing buffered now, so this is skipped by the underlying stream.
			assertEquals(1000L, r.skip(1000));
			assertEquals(IoConstants.DEFAULT_BUFFER_SIZE + 1000L, r.getBytesIn());
			//  A large read goes directly to the underlying stream.
			byte [] big = new byte[size];
			int rest = size - IoConstants.DEFAULT_BUFFER_SIZE - 1000;
			assertEquals(rest, r.read(big, 0, big.length));
			assertEquals(-1, r.read(big, 0, big.length));
			assertEquals((long)size, r.getBytesIn());
		}

		//  A trailing CR at EOF is not part of the line, but it was read.
		try(CRLFLineReader r = new CRLFLineReader("abc\r")) {
			assertEquals("abc", r.readLine());
			assertEquals(4L, r.getBytesIn());
		}
	}

	@Test
	void testBytesInCountsALineThatIsTooLong() throws IOException {
		String longLine = repeat('a', 30);
		try(LFLineReader r = new LFLineReader(longLine + "\nnext\n")) {
			r.setMaxLineLength(10);
			assertThrows(LineTooLongException.class, r::readLine);
			//  The long line (and its LF) were consumed.
			assertEquals(31L, r.getBytesIn());
			assertEquals("next", r.readLine());
			assertEquals(36L, r.getBytesIn());
		}
	}

	@Test
	void testReaderMarkResetNotSupported() throws IOException {
		try(LFLineReader r = new LFLineReader("abc")) {
			assertFalse(r.markSupported());
			r.mark(10);
			assertThrows(IOException.class, r::reset);
			//  mark did not change anything
			assertEquals("abc", r.readLine());
			assertEquals(3L, r.getBytesIn());
			assertNull(r.readLine());
			assertEquals(3L, r.getBytesIn());
		}
	}

	@Test
	void testReaderLineTooLongReleasesLargeBuffer() throws IOException {
		//  Big enough that the internal line buffer grows past DEFAULT_BUFFER_SIZE * 4 before the limit is hit.
		int max = IoConstants.DEFAULT_BUFFER_SIZE * 5;
		String longLine = repeat('a', max + IoConstants.DEFAULT_BUFFER_SIZE * 2);
		try(LFLineReader r = new LFLineReader(longLine + "\nnext\n")) {
			r.setMaxLineLength(max);
			assertEquals(max, r.getMaxLineLength());
			LineTooLongException e = assertThrows(LineTooLongException.class, r::readLine);
			assertTrue(e.getMessage().contains(String.valueOf(max)));

			r.setMaxLineLength(-5);
			assertEquals(0, r.getMaxLineLength());
			//  The rest of the long line is still there, then the next line.
			String rest = r.readLine();
			assertTrue(rest.length() > 0 && rest.length() < longLine.length());
			assertEquals("next", r.readLine());
		}
	}

	// ------------------------------------------------------------------------
	//  Line writers
	// ------------------------------------------------------------------------

	@Test
	void testLineWriterConstructors() throws IOException {
		File lf = new File(tempDir, "lf-out.txt");
		try(LFLineWriter w = new LFLineWriter(lf)) {
			//  File writers are buffered and don't flush every line.
			assertFalse(w.isAutoFlush());
			assertEquals(AbstractLineWriter.DEFAULT_CHARSET, w.getCharset());
			w.writeLine("one");
		}
		assertEquals("one\n", new String(Files.readAllBytes(lf.toPath()), StandardCharsets.UTF_8));

		try(LFLineWriter w = new LFLineWriter(lf, LATIN1)) {
			assertEquals(LATIN1, w.getCharset());
			w.writeLine("\u00e9");
		}
		assertArrayEquals(new byte[] {(byte)0xe9, '\n'}, Files.readAllBytes(lf.toPath()));

		File crlf = new File(tempDir, "crlf-out.txt");
		try(CRLFLineWriter w = new CRLFLineWriter(crlf)) {
			w.writeLine("a");
		}
		try(CRLFLineWriter w = new CRLFLineWriter(crlf, LATIN1)) {
			w.writeLine("b");
		}
		assertEquals("b\r\n", new String(Files.readAllBytes(crlf.toPath()), StandardCharsets.US_ASCII));

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try(LFLineWriter w = new LFLineWriter(out, LATIN1)) {
			w.writeLine("\u00e9");
		}
		assertArrayEquals(new byte[] {(byte)0xe9, '\n'}, out.toByteArray());

		out = new ByteArrayOutputStream();
		try(CRLFLineWriter w = new CRLFLineWriter(out, (Charset)null)) {
			assertEquals(AbstractLineWriter.DEFAULT_CHARSET, w.getCharset());
			w.writeLine("x");
		}
		assertEquals("x\r\n", out.toString("US-ASCII"));

		assertThrows(NullPointerException.class, () -> new LFLineWriter((OutputStream)null));
	}

	@Test
	void testBufferedLineWriters() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try(LFLineWriter w = new LFLineWriter(out, 1024)) {
			assertTrue(w.isAutoFlush());
			w.setAutoFlush(false);
			assertFalse(w.isAutoFlush());
			w.writeLine("buffered");
			//  Not flushed yet
			assertEquals(0, out.size());
			w.flush();
			assertEquals("buffered\n", out.toString("US-ASCII"));
		}

		out = new ByteArrayOutputStream();
		try(CRLFLineWriter w = new CRLFLineWriter(out, 1024)) {
			//  auto flush is on, so every write reaches the stream
			w.writeLine("x");
			assertEquals("x\r\n", out.toString("US-ASCII"));
		}
	}

	@Test
	void testWriterStreamMethods() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try(LFLineWriter w = new LFLineWriter(out)) {
			assertEquals(0L, w.getLastWriteTime());
			w.write("ab");
			w.write('c');
			w.write("de".getBytes(StandardCharsets.US_ASCII));
			w.write("xfgx".getBytes(StandardCharsets.US_ASCII), 1, 2);
			assertThrows(IndexOutOfBoundsException.class, () -> w.write(new byte[2], 1, 5));
			assertEquals(7L, w.getBytesOut());
			assertTrue(w.getLastWriteTime() > 0);
		}
		assertEquals("abcdefg", out.toString("US-ASCII"));
	}

	@Test
	void testWriterCountsBytesFromSeveralThreads() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int threads = 8;
		int lines = 2000;
		try(LFLineWriter w = new LFLineWriter(out)) {
			w.setAutoFlush(false);
			Thread [] workers = new Thread[threads];
			for (int t = 0; t < threads; t++) {
				workers[t] = new Thread(() -> {
					try {
						for (int idx = 0; idx < lines; idx++) {
							w.writeLine("0123456789");
						}
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				});
				workers[t].start();
			}
			for (Thread worker : workers) {
				worker.join();
			}
			//  No counts are lost when several threads write at once.
			assertEquals((long)threads * lines * 11, w.getBytesOut());
			assertEquals(out.size(), w.getBytesOut());
		}
	}

	// ------------------------------------------------------------------------
	//  Monitored streams
	// ------------------------------------------------------------------------

	@Test
	void testMonitoredInputStreamArguments() {
		RecordingMonitor mon = new RecordingMonitor();
		InputStream in = new ByteArrayInputStream(new byte[1]);
		assertThrows(IllegalArgumentException.class, () -> new MonitoredInputStream(null, mon));
		assertThrows(IllegalArgumentException.class, () -> new MonitoredInputStream(in, null));
		assertThrows(IllegalArgumentException.class, () -> new MonitoredInputStream(in, 0, mon));
	}

	@Test
	void testMonitoredInputStreamMethods() throws IOException {
		RecordingMonitor mon = new RecordingMonitor();
		ByteArrayInputStream target = new ByteArrayInputStream("0123456789".getBytes(StandardCharsets.US_ASCII));
		MonitoredInputStream in = new MonitoredInputStream(target, 4, mon);

		assertEquals(10, in.available());
		assertTrue(in.markSupported());
		assertEquals(target.toString(), in.toString());
		//  Like other streams, a monitored stream is only equal to itself.
		assertFalse(in.equals(new MonitoredInputStream(target, mon)));

		in.mark(100);
		byte [] b = new byte[3];
		assertEquals(3, in.read(b));
		assertEquals(0, in.read(b, 0, 0));
		//  Re-reading after reset must not count the bytes twice.
		in.reset();
		//  Nothing skipped, nothing reported.
		assertEquals(0L, in.skip(0));
		assertEquals(2L, in.skip(2));
		assertEquals('2', in.read());
		assertEquals(7, in.read(new byte[20], 0, 20));
		assertEquals(-1, in.read());
		assertEquals(-1, in.read(b));
		in.close();

		assertEquals(1, mon.starts);
		//  10 bytes with a block size of 4 crosses the 4 and 8 boundaries (the bytes read again after the reset are not counted twice).
		assertEquals(Arrays.asList(4L, 8L), mon.updates);
		//  complete is only reported once
		assertEquals(Arrays.asList(10L), mon.completes);
	}

	@Test
	void testMonitoredInputStreamResetWithoutMark() throws IOException {
		RecordingMonitor mon = new RecordingMonitor();
		try(MonitoredInputStream in = new MonitoredInputStream(new ByteArrayInputStream(new byte[5]), mon)) {
			assertEquals(2, in.read(new byte[2]));
			//  ByteArrayInputStream resets to 0, but without a mark the total is not changed.
			in.reset();
			assertEquals(5, in.read(new byte[5]));
		}
		assertEquals(Arrays.asList(7L), mon.completes);
	}

	@Test
	void testMonitoredOutputStreamArguments() {
		RecordingMonitor mon = new RecordingMonitor();
		OutputStream out = new ByteArrayOutputStream();
		assertThrows(IllegalArgumentException.class, () -> new MonitoredOutputStream(null, mon));
		assertThrows(IllegalArgumentException.class, () -> new MonitoredOutputStream(out, null));
		assertThrows(IllegalArgumentException.class, () -> new MonitoredOutputStream(out, -1, mon));
	}

	@Test
	void testMonitoredOutputStreamMethods() throws IOException {
		RecordingMonitor mon = new RecordingMonitor();
		ByteArrayOutputStream target = new ByteArrayOutputStream();
		MonitoredOutputStream out = new MonitoredOutputStream(target, 3, mon);

		assertEquals(target.toString(), out.toString());
		//  Like other streams, a monitored stream is only equal to itself.
		assertFalse(out.equals(new MonitoredOutputStream(target, mon)));

		//  An empty write is not reported (and does not start the monitor).
		out.write(new byte[4], 0, 0);
		assertEquals(0, mon.starts);

		out.write("abcd".getBytes(StandardCharsets.US_ASCII));
		out.write('e');
		out.write("xfx".getBytes(StandardCharsets.US_ASCII), 1, 1);
		assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[1], 0, 2));
		out.flush();
		out.close();
		out.close();

		assertEquals("abcdef", target.toString("US-ASCII"));
		assertEquals(1, mon.starts);
		assertEquals(Arrays.asList(3L, 6L), mon.updates);
		assertEquals(Arrays.asList(6L), mon.completes);
	}

	// ------------------------------------------------------------------------
	//  TeeOutputStream
	// ------------------------------------------------------------------------

	@Test
	void testTeeOutputStreamArguments() {
		assertThrows(NullPointerException.class, () -> new TeeOutputStream((OutputStream[])null));
		assertThrows(NullPointerException.class, () -> new TeeOutputStream(new ByteArrayOutputStream(), null));
	}

	@Test
	void testTeeOutputStreamArrayWrites() throws IOException {
		ByteArrayOutputStream o1 = new ByteArrayOutputStream();
		ByteArrayOutputStream o2 = new ByteArrayOutputStream();
		try(TeeOutputStream tee = new TeeOutputStream(o1, o2)) {
			tee.write("abc".getBytes(StandardCharsets.US_ASCII));
			tee.write("xdex".getBytes(StandardCharsets.US_ASCII), 1, 2);
			tee.write('f');
		}
		assertEquals("abcdef", o1.toString("US-ASCII"));
		assertEquals("abcdef", o2.toString("US-ASCII"));
	}

	@Test
	void testTeeOutputStreamRuntimeExceptionsAreWrapped() throws IOException {
		ByteArrayOutputStream good = new ByteArrayOutputStream();
		TeeOutputStream tee = new TeeOutputStream(new FailingOutputStream(true), good, new FailingOutputStream(false));

		IOException e = assertThrows(IOException.class, () -> tee.write('a'));
		//  The first error is thrown, the second is added as suppressed.
		assertTrue(e.getCause() instanceof IllegalStateException);
		assertEquals(1, e.getSuppressed().length);
		//  The good stream still got the data.
		assertEquals("a", good.toString("US-ASCII"));
	}

	@Test
	void testTeeOutputStreamCloseReportsFlushAndCloseErrors() throws IOException {
		ByteArrayOutputStream good = new ByteArrayOutputStream() {
			boolean closed;
			@Override
			public void close() throws IOException {
				closed = true;
				super.close();
			}
			@Override
			public String toString() {
				return String.valueOf(closed);
			}
		};
		TeeOutputStream tee = new TeeOutputStream(new FailingOutputStream(false), good);
		IOException e = assertThrows(IOException.class, tee::close);
		assertEquals("flush", e.getMessage());
		//  The close error is attached to the flush error.
		assertEquals(1, e.getSuppressed().length);
		assertEquals("close", e.getSuppressed()[0].getMessage());
		//  The good stream was still closed.
		assertEquals("true", good.toString());
	}

	@Test
	void testTeeOutputStreamCloseErrorWithoutFlushError() {
		OutputStream closeFails = new OutputStream() {
			@Override
			public void write(int b) {
			}
			@Override
			public void close() throws IOException {
				throw new IOException("close");
			}
		};
		TeeOutputStream tee = new TeeOutputStream(closeFails);
		IOException e = assertThrows(IOException.class, tee::close);
		assertEquals("close", e.getMessage());
		assertEquals(0, e.getSuppressed().length);
	}

	// ------------------------------------------------------------------------
	//  Telnet streams
	// ------------------------------------------------------------------------

	@Test
	void testTelnetInputStreamMethods() throws IOException {
		ByteArrayOutputStream echo = new ByteArrayOutputStream();
		try(TelnetInputStream in = new TelnetInputStream(new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII)), echo)) {
			assertEquals(3, in.available());
			assertEquals(0, in.read(new byte[2], 0, 0));
			byte [] b = new byte[10];
			assertEquals(3, in.read(b));
			assertEquals(-1, in.read(b));
			assertEquals(-1, in.read());
		}
		//  Every byte read is echoed.
		assertEquals("abc", echo.toString("US-ASCII"));

		//  Both streams are required.
		assertThrows(NullPointerException.class, () -> new TelnetInputStream(new ByteArrayInputStream(new byte[0]), null));
		assertThrows(NullPointerException.class, () -> new TelnetInputStream(null, echo));
	}

	@Test
	void testTelnetOutputStreamFiltersCharacters() throws IOException {
		ByteArrayOutputStream target = new ByteArrayOutputStream();
		try(TelnetOutputStream out = new TelnetOutputStream(target)) {
			//  The high bit is removed: 0xC1 -> 'A'
			out.write(0xC1);
			//  All printable ASCII is transmitted, including the characters after 'z'
			out.write('{');
			//  DEL (0x7F, and 0xFF once the high bit is removed) is dropped
			out.write(0x7F);
			out.write(0xFF);
			out.write("|}~".getBytes(StandardCharsets.US_ASCII), 0, 3);
			//  All of these are dropped, so nothing is written.
			out.write(new byte[] {0x7F, (byte)0xFF}, 0, 2);
			out.write(new byte[] {'x', 'b', (byte)('c' | 0x80), 0x7F}, 1, 3);
			out.flush();
		}
		assertEquals("A{|}~bc", target.toString("US-ASCII"));
		assertThrows(NullPointerException.class, () -> new TelnetOutputStream(null));
	}

	// ------------------------------------------------------------------------
	//  ContinuousInputStream
	// ------------------------------------------------------------------------

	@Test
	void testContinuousInputStreamProperties() throws IOException {
		File file = writeFile("props.txt", "abc");
		try(ContinuousInputStream in = new ContinuousInputStream(file.getAbsolutePath(), false)) {
			assertThrows(IllegalArgumentException.class, () -> in.setFreq(0));
			in.setFreq(5);
			assertEquals(5, in.getFreq());

			assertEquals(StandardCharsets.UTF_8, in.getCharset());
			assertThrows(NullPointerException.class, () -> in.setCharset(null));
			in.setCharset(LATIN1);
			assertSame(LATIN1, in.getCharset());

			assertFalse(in.isEof());
			assertEquals(3L, in.getInputLength());
			assertEquals(3, in.available());
			//  Closing early (try-with-resources closes it again, which is allowed).
			((java.io.Closeable)in).close();
			assertTrue(in.isEof());
			assertEquals(0, in.available());
			//  The length of a closed file can't be read.
			assertEquals(-1L, in.getInputLength());
			assertEquals(-1, in.read());
		}
	}

	@Test
	void testContinuousInputStreamArrayReads() throws IOException {
		File file = writeFile("array.txt", "abcdef");
		try(ContinuousInputStream in = new ContinuousInputStream(file, false)) {
			in.setFreq(1);
			byte [] b = new byte[4];
			assertEquals(0, in.read(b, 0, 0));
			assertEquals('a', in.read());
			assertEquals(3, in.read(b, 0, 3));
			assertEquals('e', in.read());
			//  Back to the start of the file
			in.unreadLines(10);
			assertEquals(4, in.read(b));
			assertArrayEquals("abcd".getBytes(StandardCharsets.US_ASCII), b);
			assertEquals(2, in.available());
			assertEquals(2, in.read(b, 1, 3));
			assertEquals('e', b[1]);
			assertThrows(IndexOutOfBoundsException.class, () -> in.read(b, 3, 2));

			in.setEof(true);
			assertEquals(-1, in.read(b));
		}
	}

	@Test
	void testContinuousInputStreamLongLinesAndCharset() throws IOException {
		String longLine = repeat('x', 300);
		File file = new File(tempDir, "long.txt");
		Files.write(file.toPath(), (longLine + "\r\n\n\u00e9").getBytes(LATIN1));
		try(ContinuousInputStream in = new ContinuousInputStream(file, false)) {
			in.setCharset(LATIN1);
			in.setEof(true);
			assertEquals(longLine, in.readLine());
			assertEquals("", in.readLine());
			//  The last line has no terminator, it's returned at EOF.
			assertEquals("\u00e9", in.readLine());
			assertThrows(EOFException.class, in::readLine);
		}
	}

	@Test
	void testContinuousInputStreamReadErrorIsThrown() throws IOException {
		File file = writeFile("error.txt", "abc");
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		ContinuousInputStream in = new ContinuousInputStream(raf, false);
		//  Close the file under the stream (not through the stream) so the read fails.
		raf.close();
		assertThrows(IOException.class, in::read);
		in.close();
	}

	@Test
	void testContinuousInputStreamUnreadLinesEdgeCases() throws IOException {
		File file = writeFile("unread.txt", "a\nb\nc");
		try(ContinuousInputStream in = new ContinuousInputStream(file, false)) {
			in.setEof(true);

			//  0 lines: positioned at the end of the file.
			in.unreadLines(0);
			assertEquals(-1, in.read());

			//  More lines than the file has: positioned at the start.
			in.unreadLines(10);
			assertEquals("a", in.readLine());

			//  No trailing newline, so "c" is the last line.
			in.unreadLines(1);
			assertEquals("c", in.readLine());
		}

		File empty = writeFile("empty.txt", "");
		try(ContinuousInputStream in = new ContinuousInputStream(empty, false)) {
			in.setEof(true);
			in.unreadLines(3);
			assertEquals(-1, in.read());
		}
	}

	@Test
	void testContinuousInputStreamUnreadLinesAcrossBlocks() throws IOException {
		StringBuilder buf = new StringBuilder();
		int lines = 3000;
		for (int idx = 0; idx < lines; idx++) {
			buf.append(String.format("line %04d\n", idx));
		}
		File file = writeFile("blocks.txt", buf.toString());
		try(ContinuousInputStream in = new ContinuousInputStream(file, true)) {
			in.setEof(true);
			//  10 bytes per line, so 2500 lines spans several 8K blocks.
			in.unreadLines(2500);
			assertEquals("line 0500", in.readLine());
			assertNotEquals(0, in.available());
		}
	}
}
