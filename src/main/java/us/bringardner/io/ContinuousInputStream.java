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
 * ~version~V000.01.06-V000.00.01-V000.00.00-
 */
package us.bringardner.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * ContinuousInputStream will read data until EOF then it will wait for additional data to
 * arrive instead of ending the read.
 * It's Useful when debugging or monitoring processes. Think tail -f
 * <p>
 * The stream ends (read returns -1) only after {@link #setEof(boolean)} or {@link #close()}
 * is called, and any data that is already in the file has been read.
 * If the file is truncated (e.g. log rotation by copy / truncate), reading restarts at the beginning of the file.
 * If the reading thread is interrupted while waiting for data an {@link InterruptedIOException} is thrown.
 */
public class ContinuousInputStream extends java.io.InputStream {
	private static final int BUFFER_SIZE = 8 * 1024;

	private final RandomAccessFile in ;
	private final byte [] buf = new byte[BUFFER_SIZE];
	private int pos;
	private int limit;
	//  File position of the next byte that will be read from the file (i.e. just past buf[limit-1])
	private long filePos;

	//  Set by other threads, so they must be volatile.
	private volatile boolean eof = false;
	private volatile boolean closed = false;
	private volatile int freq=40;
	private volatile Charset charset = StandardCharsets.UTF_8;

	

	/**
	 * ContinuousInputStream constructor comment.
	 */
	public ContinuousInputStream(File file,boolean seekToEnd)	throws FileNotFoundException, IOException	{
		this(new RandomAccessFile(file,"r"),seekToEnd);
	}

	/**
	 * ContinuousInputStream constructor comment.
	 */
	public ContinuousInputStream(RandomAccessFile in, boolean seekToEnd)	throws IOException	{
		super();
		this.in = Objects.requireNonNull(in, "in is required");

		if( seekToEnd) {
			filePos = in.length();
		} else {
			filePos = in.getFilePointer();
		}
		in.seek(filePos);
	}

	/**
	 * ContinuousInputStream constructor comment.
	 */
	public ContinuousInputStream(String fileName,boolean seekToEnd)	throws FileNotFoundException, IOException	{
		this(new RandomAccessFile(fileName,"r"),seekToEnd);
	}

	/**
	 * Close the file.  A thread blocked in read() will return -1 (EOF).
	 * Note: this method is not synchronized so it can be called while another thread is waiting for data.
	 */
	public void close()	throws IOException	{
		eof=true;
		closed = true;
		in.close();
	}

	/**
	 * 
	 * Creation date: (1/14/03 7:44:33 AM)
	 * @return int the number of milliseconds to wait before checking for more data.
	 */
	public int getFreq() {
		return freq;
	}

	/**
	 * 
	 * Creation date: (1/14/03 7:44:33 AM)
	 * @return boolean
	 */
	public boolean isEof() {
		return eof;
	}

	/**
	 * @return the Charset used by readLine to convert bytes to a String (default is UTF-8).
	 */
	public Charset getCharset() {
		return charset;
	}

	/**
	 * @param charset the Charset used by readLine to convert bytes to a String.
	 */
	public void setCharset(Charset charset) {
		this.charset = Objects.requireNonNull(charset, "charset is required");
	}

	/**
	 * Make sure there is data in the buffer, waiting for more data to be written to the file if needed.
	 * 
	 * @return false if EOF (setEof(true) or close() was called and there is no more data).
	 */
	private boolean fill() throws IOException {
		while( pos >= limit ) {
			if( closed ) {
				return false;
			}
			try {
				long len = in.length();
				if( len < filePos ) {
					//  The file was truncated, start over from the beginning.
					filePos = 0;
				}
				if( len > filePos ) {
					in.seek(filePos);
					int n = in.read(buf, 0, (int)Math.min(buf.length, len - filePos));
					if( n > 0 ) {
						pos = 0;
						limit = n;
						filePos += n;
						return true;
					}
				}
			} catch (IOException e) {
				if( closed ) {
					//  Closed by another thread while we were reading.
					return false;
				}
				throw e;
			}

			if( eof ) {
				return false;
			}

			try { 
				Thread.sleep(freq); 
			} catch(InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new InterruptedIOException("Interrupted while waiting for data");
			}
		}
		return true;
	}
	
	/**
	 * Reads the next byte of data from the input stream. The value byte is
	 * returned as an <code>int</code> in the range <code>0</code> to
	 * <code>255</code>. If no byte is available because the end of the stream
	 * has been reached, the value <code>-1</code> is returned. This method
	 * blocks until input data is available, the end of the stream is detected,
	 * or an exception is thrown.
	 *
	 * @return     the next byte of data, or <code>-1</code> if the end of the
	 *             stream is reached.
	 * @exception  IOException  if an I/O error occurs.
	 */
	public synchronized int read() throws java.io.IOException	{
		if( !fill() ) {
			return -1;
		}
		return buf[pos++] & 0xff;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	/**
	 * Read up to len bytes, blocking until at least one byte is available (or EOF).
	 */
	@Override
	public synchronized int read(byte[] b, int off, int len) throws IOException {
		Objects.checkFromIndexSize(off, len, b.length);
		if( len == 0 ) {
			return 0;
		}
		if( !fill() ) {
			return -1;
		}
		int n = Math.min(len, limit - pos);
		System.arraycopy(buf, pos, b, off, n);
		pos += n;
		return n;
	}

	public long getInputLength() {
		long ret = -1;
		try {
			ret = in.length();
		} catch (IOException e) {
		}
		return ret;
	}
	
	/**
	 * Position the stream so the next read will return the last 'want' lines of the file (think tail -n).
	 * A newline at the very end of the file does not start a new line.
	 * 
	 * @param want number of lines 
	 * @throws IOException
	 */
	public synchronized void unreadLines(int want) throws IOException {
		long len = in.length();
		long start = len;

		if( want > 0 && len > 0 ) {
			long end = len;
			in.seek(len - 1);
			if( in.read() == '\n' ) {
				end--;
			}
			start = 0;
			int found = 0;
			byte [] tmp = new byte[BUFFER_SIZE];
			boolean done = false;
			//  Scan backwards, one block at a time.
			for(long blockEnd = end; blockEnd > 0 && !done; ) {
				int n = (int)Math.min(tmp.length, blockEnd);
				long blockStart = blockEnd - n;
				in.seek(blockStart);
				in.readFully(tmp, 0, n);
				for(int idx = n - 1; idx >= 0; idx--) {
					if( tmp[idx] == '\n' && ++found == want ) {
						start = blockStart + idx + 1;
						done = true;
						break;
					}
				}
				blockEnd = blockStart;
			}
		}

		//  Discard anything in the buffer and read from the new position.
		pos = limit = 0;
		filePos = start;
		in.seek(start);
	}

	/**
	 * Read a line of text (terminated by LF, a CR before the LF is removed).
	 * If EOF is reached the partial line is returned.
	 * <p>
	 * Waits for more data, so null is only returned after {@link #setEof(boolean)} or
	 * {@link #close()} has been called and all of the data has been read.
	 * 
	 * @return the next line, or null at the end of the stream.
	 * @throws IOException if there is an error reading the file (or the thread is interrupted).
	 */
	public synchronized String readLine()	throws IOException	{
		byte [] line = new byte[128];
		int sz = 0;
		boolean gotData = false;

		while( fill() ) {
			gotData = true;
			int start = pos;
			int idx = start;
			while( idx < limit && buf[idx] != '\n' ) {
				idx++;
			}
			int n = idx - start;
			if( sz + n > line.length ) {
				line = Arrays.copyOf(line, Math.max(sz + n, line.length * 2));
			}
			System.arraycopy(buf, start, line, sz, n);
			sz += n;
			if( idx < limit ) {
				pos = idx + 1;
				break;
			}
			pos = limit;
		}

		if( !gotData ) {
			return null;
		}

		if( sz > 0 && line[sz-1] == '\r') {
			sz--;
		}

		return new String(line, 0, sz, charset);
	}


	/**
	 * 
	 * Creation date: (1/14/03 7:44:33 AM)
	 * @param newEof boolean
	 */
	public void setEof(boolean newEof) {
		eof = newEof;
	}
	
	/**
	 * 
	 * Creation date: (1/14/03 7:44:33 AM)
	 * @param newFreq int number of milliseconds to wait before checking for more data (must be > 0).
	 */
	public void setFreq(int newFreq) {
		if( newFreq <= 0 ) {
			throw new IllegalArgumentException("freq must be > 0");
		}
		freq = newFreq;
	}

	
	/**
	 * @return the number of bytes that can be read without waiting (capped at Integer.MAX_VALUE).
	 * This method is not synchronized (so it doesn't wait for a blocked read), the result is an estimate
	 * if another thread is reading at the same time.
	 */
	public int available() throws IOException {		
		if( closed ) {
			return 0;
		}
		long ret = (long)(limit - pos) + Math.max(0, in.length() - filePos);
		return (int)Math.min(Integer.MAX_VALUE, ret);
	}


	
}
