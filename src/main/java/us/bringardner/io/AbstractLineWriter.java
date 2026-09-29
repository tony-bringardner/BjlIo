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
 * ~version~V000.00.01-V000.00.00-
 */
// ~version~V000.00.01-V000.00.00-
package us.bringardner.io;

/**
 * @author Tony Bringardner
 */
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;


/**
 *  
 * OutputStream that writes lines terminated with an
 * predefined line terminator.
 * <p>
 * Strings are converted to bytes with the writer's Charset (UTF-8 unless another one
 * is given to the constructor), NOT the platform default, so the output is the same on every OS.
 * 
 */
public abstract class AbstractLineWriter  extends FilterOutputStream implements ILineWriter,IoConstants 
{
	/** The Charset used when a Charset is not provided. */
	public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

	//  Read by other threads (e.g. idle time-out monitors) so they must be volatile / atomic.
	//  AtomicLong so no counts are lost when several threads write at the same time.
	private final AtomicLong bytes = new AtomicLong();
	private volatile boolean autoFlush = true;
	private volatile long lastWriteTime;
	private final byte [] terminator;
	private final Charset charset;
	
	public AbstractLineWriter(OutputStream outputStream, int outBufSize,byte [] terminator)	throws IOException	{
		this(new BufferedOutputStream(outputStream,outBufSize),terminator);
	}
	
	/**
	 * Write to a File.  The output is buffered and 'Auto Flush' is OFF, 
	 * call flush() or close() to make sure all data is written.
	 */
	public AbstractLineWriter(File outputFile, byte [] terminator)	throws IOException	{
		this(outputFile, terminator, DEFAULT_CHARSET);
	}

	/**
	 * Write to a File.  The output is buffered and 'Auto Flush' is OFF, 
	 * call flush() or close() to make sure all data is written.
	 */
	public AbstractLineWriter(File outputFile, byte [] terminator, Charset charset)	throws IOException	{
		this(new BufferedOutputStream(new FileOutputStream(outputFile),DEFAULT_BUFFER_SIZE),terminator,charset);
		autoFlush = false;
	}
	
	public AbstractLineWriter(OutputStream out,byte [] terminator) {
		this(out, terminator, DEFAULT_CHARSET);
	}

	/**
	 * @param out the OutputStream to write to.
	 * @param terminator the line terminator (it is copied).
	 * @param charset used to convert Strings to bytes (null means UTF-8).
	 */
	public AbstractLineWriter(OutputStream out,byte [] terminator, Charset charset) {
		super(Objects.requireNonNull(out, "out is required"));
		this.terminator = Objects.requireNonNull(terminator, "terminator is required").clone();
		this.charset = charset == null ? DEFAULT_CHARSET : charset;
	}

	/**
	 * @return the Charset used to convert Strings to bytes.
	 */
	public Charset getCharset() {
		return charset;
	}

	@Override
	public void flush()	throws IOException	{
		out.flush();
	}

	@Override
	public long getBytesOut()	{
		return bytes.get();
	}

	private void written(long count) throws IOException {
		bytes.addAndGet(count);
		lastWriteTime=System.currentTimeMillis();
		if( autoFlush ) {
			flush();
		}
	}

	@Override
	public void writeLine(String line) throws IOException	{
		byte [] data = line.getBytes(charset);
		//  One write for the line and terminator (one packet / system call when the stream is not buffered).
		byte [] all = new byte[data.length+terminator.length];
		System.arraycopy(data, 0, all, 0, data.length);
		System.arraycopy(terminator, 0, all, data.length, terminator.length);
		out.write(all);
		written(all.length);
	}
	
	@Override
	public void write(String line) throws IOException	{
		byte [] data = line.getBytes(charset);
		out.write(data);
		written(data.length);
	}

	/*
	 * FilterOutputStream would write the array one byte at a time and 
	 * the bytes would not be counted.
	 */
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		Objects.checkFromIndexSize(off, len, b.length);
		out.write(b, off, len);
		written(len);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(int b) throws IOException {
		out.write(b);
		written(1);
	}
	
	/* (non-Javadoc)
	 * @see us.bringardner.io.LineWriter#isAutoFlush()
	 */
	@Override
	public boolean isAutoFlush() {
	
		return autoFlush;
	}
	
	/* (non-Javadoc)
	 * @see us.bringardner.io.LineWriter#setAutoFlush(boolean)
	 */
	@Override
	public void setAutoFlush(boolean autoFlush) {
		this.autoFlush= autoFlush;
	}

	@Override
	public long getLastWriteTime() {
		return lastWriteTime;
	}
	
}
