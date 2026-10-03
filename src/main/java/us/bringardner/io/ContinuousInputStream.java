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
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ContinuousInputStream will read data until EOF then it will wait for additional data to
 * arrive instead of ending the read.
 * It's Useful when debugging or monitoring processes. Think tail -f
 * <p>
 * The stream ends (read returns -1) only after {@link #setEof(boolean)} or {@link #close()}
 * is called, and any data that is already in the file has been read.
 * If the file is truncated (e.g. log rotation by copy / truncate), reading restarts at the beginning of the file.
 * If the file is moved or deleted and a new file is created with the same name (log rotation by
 * renaming, logrotate's default), the rest of the old file is read and then the new file is read from
 * its beginning, like {@code tail -F}. That needs the file's name, so it isn't done for a stream made
 * from a RandomAccessFile, or where the file system doesn't identify files (Windows, which doesn't
 * allow an open file to be moved anyway).
 * If the reading thread is interrupted while waiting for data an {@link InterruptedIOException} is thrown.
 */
public class ContinuousInputStream extends java.io.InputStream {
	private static final int BUFFER_SIZE = 8 * 1024;

	//  The file's name, to follow it when it is replaced (null when made from a RandomAccessFile).
	private final File file;
	//  Replaced when the file is, so read without the lock by close() and available().
	private volatile RandomAccessFile in;
	//  The file system's identity of the open file (null if unknown).
	private Object fileKey;

	private final byte [] buf = new byte[BUFFER_SIZE];
	private int pos;
	private int limit;
	//  File position of the next byte that will be read from the file (i.e. just past buf[limit-1])
	private long filePos;

	//  The line being read by readLine(). Kept when readLine() throws (it was interrupted, say),
	//  so the next call carries on with the same line.
	private byte [] line = new byte[128];
	private int lineLen;
	//  Longest line readLine() accepts (0 = no limit).
	private volatile int maxLineLength;

	/**
	 * Guards the buffer, the line and the file position. A lock rather than synchronized methods:
	 * a read can wait a long time for data, and on Java 21-23 a virtual thread waiting inside a
	 * synchronized method holds on to its carrier thread (see AbstractLineReader, BJL-55).
	 */
	private final ReentrantLock lock = new ReentrantLock();

	//  Set by other threads, so they must be volatile.
	private volatile boolean eof = false;
	private volatile boolean closed = false;
	private volatile int freq=40;
	private volatile Charset charset = StandardCharsets.UTF_8;

	/**
	 * ContinuousInputStream constructor comment.
	 */
	public ContinuousInputStream(File file,boolean seekToEnd)	throws FileNotFoundException, IOException	{
		this(Objects.requireNonNull(file, "file is required"), new RandomAccessFile(file,"r"), seekToEnd);
	}

	/**
	 * ContinuousInputStream constructor comment.
	 * <p>
	 * Made from a RandomAccessFile the stream doesn't know the file's name, so it can't follow
	 * the file when it is moved and replaced (it can when it is truncated).
	 */
	public ContinuousInputStream(RandomAccessFile in, boolean seekToEnd)	throws IOException	{
		this(null, in, seekToEnd);
	}

	/**
	 * ContinuousInputStream constructor comment.
	 */
	public ContinuousInputStream(String fileName,boolean seekToEnd)	throws FileNotFoundException, IOException	{
		this(new File(Objects.requireNonNull(fileName, "fileName is required")), seekToEnd);
	}

	private ContinuousInputStream(File file, RandomAccessFile in, boolean seekToEnd) throws IOException {
		super();
		this.file = file;
		this.in = Objects.requireNonNull(in, "in is required");
		this.fileKey = fileKey();

		if( seekToEnd) {
			filePos = in.length();
		} else {
			filePos = in.getFilePointer();
		}
		in.seek(filePos);
	}

	/**
	 * @return the file system's identity of the file now at our file name, or null if there is
	 * no such file, no name, or the file system doesn't have one.
	 */
	private Object fileKey() {
		if( file == null ) {
			return null;
		}
		try {
			return Files.readAttributes(file.toPath(), BasicFileAttributes.class).fileKey();
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/**
	 * @return true if a different file now has our file name (the one we have open was moved or
	 * deleted and a new one created). A moved file without a new one isn't replaced: whatever still
	 * writes to it can, and its data is read.
	 */
	private boolean isReplaced() {
		Object open = fileKey;
		if( open == null ) {
			return false;
		}
		Object now = fileKey();
		return now != null && !now.equals(open);
	}

	/**
	 * Switch to the file that now has our name, from its beginning.
	 */
	private void reopen() throws IOException {
		RandomAccessFile next;
		try {
			next = new RandomAccessFile(file, "r");
		} catch (FileNotFoundException e) {
			//  Gone again, keep reading (waiting on) the old one.
			return;
		}
		RandomAccessFile old = in;
		in = next;
		fileKey = fileKey();
		filePos = 0;
		pos = limit = 0;
		try {
			old.close();
		} catch (IOException e) {
			//  We are done with it
		}
		if( closed ) {
			//  close() was called while we were switching, and may have closed the old file only.
			next.close();
		}
	}

	/**
	 * Close the file.  A thread blocked in read() will return -1 (EOF).
	 * Note: this method doesn't take the lock so it can be called while another thread is waiting for data.
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
	 * Limit the length of a line returned by {@link #readLine()}. Without a limit (the default), a file
	 * without line terminators (a binary file, say) is read into memory until it runs out.
	 * <p>
	 * When a line is longer, readLine() throws {@link LineTooLongException}. If the line is longer than
	 * the stream's buffer, the next call returns the rest of it.
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
				RandomAccessFile f = in;
				long len = f.length();
				if( len < filePos ) {
					//  The file was truncated, start over from the beginning.
					filePos = 0;
				}
				if( len > filePos ) {
					f.seek(filePos);
					int n = f.read(buf, 0, (int)Math.min(buf.length, len - filePos));
					if( n > 0 ) {
						pos = 0;
						limit = n;
						filePos += n;
						return true;
					}
				} else if( isReplaced() ) {
					//  All of the old file has been read, carry on with the new one.
					reopen();
					continue;
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
	public int read() throws java.io.IOException	{
		lock.lock();
		try {
			if( !fill() ) {
				return -1;
			}
			return buf[pos++] & 0xff;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	/**
	 * Read up to len bytes, blocking until at least one byte is available (or EOF).
	 */
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		Objects.checkFromIndexSize(off, len, b.length);
		if( len == 0 ) {
			return 0;
		}
		lock.lock();
		try {
			if( !fill() ) {
				return -1;
			}
			int n = Math.min(len, limit - pos);
			System.arraycopy(buf, pos, b, off, n);
			pos += n;
			return n;
		} finally {
			lock.unlock();
		}
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
	public void unreadLines(int want) throws IOException {
		lock.lock();
		try {
			RandomAccessFile f = in;
			long len = f.length();
			long start = len;

			if( want > 0 && len > 0 ) {
				long end = len;
				f.seek(len - 1);
				if( f.read() == '\n' ) {
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
					f.seek(blockStart);
					f.readFully(tmp, 0, n);
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

			//  Discard anything in the buffer (and a line in progress) and read from the new position.
			pos = limit = 0;
			lineLen = 0;
			filePos = start;
			f.seek(start);
		} finally {
			lock.unlock();
		}
	}

	private void appendToLine(int off, int len) {
		int need = lineLen + len;
		if( need > line.length ) {
			line = Arrays.copyOf(line, Math.max(need, line.length * 2));
		}
		System.arraycopy(buf, off, line, lineLen, len);
		lineLen = need;
	}

	/**
	 * Throw if the line read so far is over the limit.
	 * @param slack bytes allowed over the limit (a CR that may turn out to be part of the terminator).
	 */
	private void checkLength(int slack) throws LineTooLongException {
		int max = maxLineLength;
		if( max > 0 && lineLen > max + slack ) {
			lineLen = 0;
			if( line.length > BUFFER_SIZE * 4 ) {
				line = new byte[128];
			}
			throw new LineTooLongException(max);
		}
	}

	/**
	 * Read a line of text (terminated by LF, a CR before the LF is removed).
	 * If EOF is reached the partial line is returned.
	 * <p>
	 * Waits for more data, so null is only returned after {@link #setEof(boolean)} or
	 * {@link #close()} has been called and all of the data has been read.
	 * If it is interrupted (or the read fails) part way through a line, the part already read is kept
	 * and the next call carries on with the same line.
	 * 
	 * @return the next line, or null at the end of the stream.
	 * @throws IOException if there is an error reading the file (or the thread is interrupted).
	 * @throws LineTooLongException if the line is longer than {@link #setMaxLineLength(int)} allows.
	 */
	public String readLine()	throws IOException	{
		lock.lock();
		try {
			boolean gotData = lineLen > 0;

			while( fill() ) {
				gotData = true;
				int start = pos;
				int idx = start;
				while( idx < limit && buf[idx] != '\n' ) {
					idx++;
				}
				appendToLine(start, idx - start);
				if( idx < limit ) {
					pos = idx + 1;
					break;
				}
				pos = limit;
				checkLength(1);
			}

			if( !gotData ) {
				return null;
			}

			if( lineLen > 0 && line[lineLen-1] == '\r') {
				lineLen--;
			}
			checkLength(0);

			String ret = new String(line, 0, lineLen, charset);
			lineLen = 0;
			if( line.length > BUFFER_SIZE * 4 ) {
				//  Don't hold on to the memory used by an unusually long line.
				line = new byte[128];
			}
			return ret;
		} finally {
			lock.unlock();
		}
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
	 * This method doesn't take the lock (so it doesn't wait for a blocked read), the result is an estimate
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
