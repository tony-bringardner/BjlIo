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
 *
 *
 * ~version~V000.00.00-
 */
package us.bringardner.io;

import static us.bringardner.io.IoConstants.CR;
import static us.bringardner.io.IoConstants.DEFAULT_BUFFER_SIZE;
import static us.bringardner.io.IoConstants.NL;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Common implementation for the line readers.
 * <p>
 * The reader keeps its own buffer and scans it for the line terminator, so a line
 * is read with a few bulk reads instead of one (locked) call per byte.
 * The raw bytes of each line are decoded with the reader's {@link Charset}
 * (UTF-8 unless another one is given to the constructor).
 * <p>
 * All of the InputStream methods go through the same buffer, so mixing
 * {@link #readLine()} with {@link #read()} or {@link #read(byte[], int, int)} is safe.
 */
public abstract class AbstractLineReader extends FilterInputStream implements ILineReader {

	/** The Charset used when a Charset is not provided. */
	public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

	private final Charset charset;
	private final byte [] buf;
	private int pos;
	private int limit;

	//  The bytes of the line being read (may span several buffer fills).
	private byte [] line = new byte[128];
	private int lineLen;

	//  Longest line readLine() accepts (0 = no limit).
	private volatile int maxLineLength;

	/**
	 * Guards the buffer and the line being read. A lock instead of synchronized methods
	 * (BJL-55): readers usually block on a socket while holding it, and on Java 21-23 a
	 * virtual thread blocked inside a monitor pins its carrier thread, so a server with a
	 * virtual thread per connection stopped once there were as many idle connections as CPUs.
	 */
	private final ReentrantLock lock = new ReentrantLock();

	//  Read by other threads (e.g. idle time-out monitors) so they must be volatile.
	//  bytes is only changed while holding lock, so += is safe.
	private volatile long bytes;
	private volatile long lastReadTime;

	/**
	 * @param in the InputStream to read from.
	 * @param charset used to convert the bytes of a line into a String (null means UTF-8).
	 */
	protected AbstractLineReader(InputStream in, Charset charset) {
		super(Objects.requireNonNull(in, "in is required"));
		this.charset = charset == null ? DEFAULT_CHARSET : charset;
		this.buf = new byte[DEFAULT_BUFFER_SIZE];
	}

	/**
	 * @return true if a line is terminated only by a CR LF pair (a lone LF is part of the line),
	 * false if a single LF terminates a line.
	 */
	protected abstract boolean isCrlfTerminated();

	/**
	 * Limit the length of a line. Without a limit (the default), a peer that never
	 * sends a line terminator makes {@link #readLine()} buffer everything it sends
	 * until memory runs out; use a limit whenever the input comes from the network.
	 * <p>
	 * When a line is longer, readLine() throws {@link LineTooLongException}. The
	 * rest of that line has not been read, so the reader should normally be closed.
	 * 
	 * @param maxLineLength the most bytes in a line, not counting the terminator (0 or less: no limit)
	 */
	public void setMaxLineLength(int maxLineLength) {
		this.maxLineLength = Math.max(0, maxLineLength);
	}

	/**
	 * @return the most bytes readLine() accepts in a line (0 = no limit).
	 */
	public int getMaxLineLength() {
		return maxLineLength;
	}

	/**
	 * Throw if the line read so far is over the limit.
	 * @param slack bytes allowed over the limit (a CR that may turn out to be part of the terminator).
	 */
	private void checkLength(int slack) throws LineTooLongException {
		int max = maxLineLength;
		if( max > 0 && lineLen > max + slack ) {
			lineLen = 0;
			if( line.length > DEFAULT_BUFFER_SIZE * 4 ) {
				line = new byte[128];
			}
			throw new LineTooLongException(max);
		}
	}

	/**
	 * @return the Charset used to convert the bytes of a line to a String.
	 */
	public Charset getCharset() {
		return charset;
	}

	/**
	 * Make sure there is data in the buffer.
	 * @return false if the EOF has been reached.
	 */
	private boolean fill() throws IOException {
		while( pos >= limit ) {
			int n = in.read(buf, 0, buf.length);
			if( n < 0 ) {
				return false;
			}
			pos = 0;
			limit = n;
		}
		return true;
	}

	private void appendToLine(byte [] src, int off, int len) {
		int need = lineLen + len;
		if( need > line.length ) {
			line = Arrays.copyOf(line, Math.max(need, line.length * 2));
		}
		System.arraycopy(src, off, line, lineLen, len);
		lineLen = need;
	}

	/**
	 * Read a line from the input.  The line will include all
	 * text up to (but NOT including) the line terminator.
	 * A final line that is not terminated is also returned.
	 *
	 * @return the next line or null if the EOF was reached before any data was read.
	 * @see us.bringardner.io.ILineReader#readLine()
	 */
	@Override
	public String readLine() throws IOException {
		lock.lock();
		try {
			lineLen = 0;
			boolean gotData = false;
			boolean terminated = false;
			boolean crlf = isCrlfTerminated();

			while( !terminated ) {
				if( !fill() ) {
					break;
				}
				gotData = true;
				int start = pos;
				int idx = start;
				while( idx < limit && buf[idx] != NL ) {
					idx++;
				}
				if( idx == limit ) {
					//  No LF in the buffer, keep everything and read more.
					appendToLine(buf, start, limit - start);
					pos = limit;
					bytes += limit - start;
					checkLength(crlf ? 1 : 0);
				} else {
					appendToLine(buf, start, idx - start);
					pos = idx + 1;
					//  The LF is consumed (and counted) too.
					bytes += pos - start;
					if( !crlf ) {
						terminated = true;
					} else if( lineLen > 0 && line[lineLen - 1] == CR ) {
						lineLen--;
						terminated = true;
					} else {
						//  A lone LF is part of a CRLF terminated line
						appendToLine(buf, idx, 1);
					}
					checkLength(terminated ? 0 : 1);
				}
			}

			lastReadTime = System.currentTimeMillis();
			if( !gotData ) {
				return null;
			}

			//  At EOF, ignore a trailing CR (the LF never arrived).
			if( !terminated && crlf && lineLen > 0 && line[lineLen - 1] == CR ) {
				lineLen--;
			}
			checkLength(0);

			String ret = new String(line, 0, lineLen, charset);
			if( line.length > DEFAULT_BUFFER_SIZE * 4 ) {
				//  Don't hold on to the memory used by an unusually long line.
				line = new byte[128];
			}
			return ret;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int read() throws IOException {
		lock.lock();
		try {
			if( !fill() ) {
				return -1;
			}
			bytes++;
			return buf[pos++] & 0xff;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		lock.lock();
		try {
			Objects.checkFromIndexSize(off, len, b.length);
			if( len == 0 ) {
				return 0;
			}
			if( pos >= limit && len >= buf.length ) {
				//  Nothing buffered and the caller wants a lot; read directly.
				int n = in.read(b, off, len);
				if( n > 0 ) {
					bytes += n;
				}
				return n;
			}
			if( !fill() ) {
				return -1;
			}
			int n = Math.min(len, limit - pos);
			System.arraycopy(buf, pos, b, off, n);
			pos += n;
			bytes += n;
			return n;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public long skip(long n) throws IOException {
		lock.lock();
		try {
			if( n <= 0 ) {
				return 0;
			}
			int buffered = limit - pos;
			if( buffered > 0 ) {
				int skipped = (int)Math.min(n, buffered);
				pos += skipped;
				bytes += skipped;
				return skipped;
			}
			long skipped = in.skip(n);
			if( skipped > 0 ) {
				bytes += skipped;
			}
			return skipped;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int available() throws IOException {
		lock.lock();
		try {
			long ret = (long)(limit - pos) + in.available();
			return (int)Math.min(Integer.MAX_VALUE, ret);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * mark / reset are not supported (the reader has its own buffer).
	 */
	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public void mark(int readlimit) {
		//  Not supported
	}

	@Override
	public void reset() throws IOException {
		throw new IOException("mark/reset not supported");
	}

	@Override
	public void close() throws IOException {
		super.close();
	}

	/*
	 * @see us.bringardner.io.ILineReader#getBytesIn()
	 */
	@Override
	public long getBytesIn() {
		return bytes;
	}

	@Override
	public int inputAvailable() throws IOException {
		return available();
	}

	@Override
	public long getLastReadTime() {
		return lastReadTime;
	}
}
