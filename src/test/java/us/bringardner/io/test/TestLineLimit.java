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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import us.bringardner.io.AbstractLineReader;
import us.bringardner.io.CRLFLineReader;
import us.bringardner.io.LFLineReader;
import us.bringardner.io.LineTooLongException;

/**
 * AbstractLineReader.setMaxLineLength
 */
public class TestLineLimit {

	private static InputStream bytes(String s) {
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}

	private static <T extends AbstractLineReader> T limit(T r, int max) {
		r.setMaxLineLength(max);
		return r;
	}

	@Test
	public void crlfLinesUpToTheLimit() throws Exception {
		try(CRLFLineReader r = limit(new CRLFLineReader(bytes("0123456789\r\nabc\r\n0123456789")), 10)) {
			assertEquals("0123456789", r.readLine());
			assertEquals("abc", r.readLine());
			assertEquals("0123456789", r.readLine(), "unterminated last line");
			assertNull(r.readLine());
		}
	}

	@Test
	public void crlfLineOverTheLimit() throws Exception {
		try(CRLFLineReader r = limit(new CRLFLineReader(bytes("ok\r\n0123456789A\r\n")), 10)) {
			assertEquals("ok", r.readLine());
			LineTooLongException ex = assertThrows(LineTooLongException.class, r::readLine);
			assertEquals(10, ex.getMaxLineLength());
		}
		//  A lone LF is part of a CRLF line and counts
		try(CRLFLineReader r = limit(new CRLFLineReader(bytes("01234\n67890\r\n")), 10)) {
			assertThrows(LineTooLongException.class, r::readLine);
		}
	}

	@Test
	public void lfLines() throws Exception {
		try(LFLineReader r = limit(new LFLineReader(bytes("0123456789\n0123456789A\n")), 10)) {
			assertEquals("0123456789", r.readLine());
			assertThrows(LineTooLongException.class, r::readLine);
		}
	}

	@Test
	public void terminatorSplitAcrossReads() throws Exception {
		//  The CR arrives in one read and the LF in the next: exactly max bytes is still fine
		InputStream in = new InputStream() {
			final byte [][] parts = {"0123456789\r".getBytes(StandardCharsets.UTF_8), "\n".getBytes(StandardCharsets.UTF_8)};
			int part;
			@Override
			public int read() throws IOException {
				throw new UnsupportedOperationException();
			}
			@Override
			public int read(byte[] b, int off, int len) {
				if( part >= parts.length ) {
					return -1;
				}
				byte [] p = parts[part++];
				System.arraycopy(p, 0, b, off, p.length);
				return p.length;
			}
		};
		try(CRLFLineReader r = limit(new CRLFLineReader(in), 10)) {
			assertEquals("0123456789", r.readLine());
			assertNull(r.readLine());
		}
	}

	@Test
	public void endlessInputStopsAtTheLimit() throws Exception {
		//  Without a limit this would buffer until OutOfMemoryError
		final long [] served = new long[1];
		InputStream endless = new InputStream() {
			@Override
			public int read() {
				served[0]++;
				return 'x';
			}
			@Override
			public int read(byte[] b, int off, int len) {
				Arrays.fill(b, off, off+len, (byte)'x');
				served[0] += len;
				return len;
			}
		};
		try(CRLFLineReader r = limit(new CRLFLineReader(endless), 8192)) {
			assertThrows(LineTooLongException.class, r::readLine);
		}
		assertTrue(served[0] < 8192 + 2*AbstractLineReader.DEFAULT_BUFFER_SIZE, "read "+served[0]+" bytes");
	}

	@Test
	public void noLimitByDefault() throws Exception {
		char [] big = new char[100_000];
		Arrays.fill(big, 'y');
		try(CRLFLineReader r = new CRLFLineReader(bytes(new String(big)+"\r\n"))) {
			assertEquals(0, r.getMaxLineLength());
			assertEquals(100_000, r.readLine().length());
		}
	}
}
