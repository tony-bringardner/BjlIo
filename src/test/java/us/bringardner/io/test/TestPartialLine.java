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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import org.junit.jupiter.api.Test;

import us.bringardner.io.AbstractLineReader;
import us.bringardner.io.CRLFLineReader;
import us.bringardner.io.LFLineReader;

/**
 * A line interrupted by a read timeout is not lost: the next readLine() carries on with it.
 * And getLastReadTime() is updated by every way of reading.
 */
public class TestPartialLine {

	/** A stream that returns the given pieces one read at a time; null stands for a read timeout. */
	static class Pieces extends InputStream {
		private final Deque<Object> pieces = new ArrayDeque<>();

		Pieces(String ... parts) {
			for(String p : parts) {
				pieces.add(p == null ? (Object) Boolean.TRUE : p.getBytes(StandardCharsets.UTF_8));
			}
		}

		@Override
		public int read() throws IOException {
			byte [] b = new byte[1];
			int n = read(b, 0, 1);
			return n < 0 ? -1 : b[0] & 0xff;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			Object next = pieces.poll();
			if( next == null ) {
				return -1;
			}
			if( next == Boolean.TRUE ) {
				throw new SocketTimeoutException("Read timed out");
			}
			byte [] data = (byte[]) next;
			int n = Math.min(len, data.length);
			System.arraycopy(data, 0, b, off, n);
			if( n < data.length ) {
				pieces.addFirst(Arrays.copyOfRange(data, n, data.length));
			}
			return n;
		}
	}

	/** readLine() as the server's command loop calls it: a timeout means try again. */
	private static String readLineRetrying(AbstractLineReader reader) throws IOException {
		for(int tries=0; tries < 10; tries++ ) {
			try {
				return reader.readLine();
			} catch (SocketTimeoutException e) {
				//  try again
			}
		}
		throw new AssertionError("Too many timeouts");
	}

	@Test
	public void testTimeoutInTheMiddleOfALine() throws IOException {
		CRLFLineReader reader = new CRLFLineReader(new Pieces("USER al", null, "ice\r\n", "PASS x\r\n"));
		assertThrows(SocketTimeoutException.class, reader::readLine);
		assertEquals("USER alice", reader.readLine(), "The start of the line should not be lost");
		assertEquals("PASS x", reader.readLine());
		assertNull(reader.readLine());
		assertEquals(20, reader.getBytesIn());
	}

	@Test
	public void testTimeoutBetweenCrAndLf() throws IOException {
		CRLFLineReader reader = new CRLFLineReader(new Pieces("USER alice\r", null, "\nQUIT\r\n"));
		assertEquals("USER alice", readLineRetrying(reader));
		assertEquals("QUIT", readLineRetrying(reader));
		assertNull(readLineRetrying(reader));
	}

	@Test
	public void testSeveralTimeoutsInOneLine() throws IOException {
		LFLineReader reader = new LFLineReader(new Pieces("a", null, "b", null, null, "c\n"));
		assertEquals("abc", readLineRetrying(reader));
		assertNull(readLineRetrying(reader));
	}

	@Test
	public void testTimeoutThenEndOfStream() throws IOException {
		//  The part read before the timeout is returned as the last (unterminated) line
		CRLFLineReader reader = new CRLFLineReader(new Pieces("partial", null));
		assertEquals("partial", readLineRetrying(reader));
		assertNull(reader.readLine());
	}

	@Test
	public void testLastReadTimeForEveryKindOfRead() throws IOException {
		byte [] big = new byte[10000];
		Arrays.fill(big, (byte) 'x');

		CRLFLineReader reader = new CRLFLineReader(new Pieces("ab"));
		assertEquals(0, reader.getLastReadTime(), "Nothing read yet");
		assertEquals('a', reader.read());
		assertTrue(reader.getLastReadTime() > 0, "read() should update it");

		CRLFLineReader direct = new CRLFLineReader(new Pieces(new String(big, StandardCharsets.UTF_8)));
		assertEquals(big.length, direct.read(new byte[big.length], 0, big.length));
		assertTrue(direct.getLastReadTime() > 0, "A large read(byte[]) (which bypasses the buffer) should update it");

		CRLFLineReader skipper = new CRLFLineReader(new java.io.ByteArrayInputStream(big));
		assertTrue(skipper.skip(100) > 0);
		assertTrue(skipper.getLastReadTime() > 0, "skip() should update it");
	}
}
