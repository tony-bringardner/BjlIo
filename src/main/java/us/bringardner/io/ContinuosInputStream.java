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

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * ContinuosInputStream will read data until EOF then it will wait for addition data to
 * arrive instead of ending the read.
 * It's Useful when debugging or monitoring processes. Think tail -f
 */
public class ContinuosInputStream extends java.io.InputStream {
	private RandomAccessFile in ;
	private long fileSize;
	private boolean eof = false;
	private int freq=40;

	

	/**
	 * ContinuosInputStream constructor comment.
	 */
	public ContinuosInputStream(File file,boolean seekToEnd)	throws FileNotFoundException, IOException	{
		this(new RandomAccessFile(file,"r"),seekToEnd);
	}

	/**
	 * ContinuosInputStream constructor comment.
	 */
	public ContinuosInputStream(RandomAccessFile in, boolean seekToEnd)	throws IOException	{
		super();
		this.in = in;

		if( seekToEnd) {
			fileSize = in.length();
			in.seek(fileSize);
		}

	}

	/**
	 * ContinuosInputStream constructor comment.
	 */
	public ContinuosInputStream(String fileName,boolean seekToEnd)	throws FileNotFoundException, IOException	{
		this(new RandomAccessFile(fileName,"r"),seekToEnd);
	}

	public void close()	throws IOException	{
		eof=true;
		in.close();
	}

	/**
	 * 
	 * Creation date: (1/14/03 7:44:33 AM)
	 * @return int
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
	 * Starts the application.
	 * @param args an array of command-line arguments
	 */
	public static void main(java.lang.String[] args)	throws Exception {

		String fileName = null;
		if( args.length > 0 ) {
			fileName = args[0];
		} else {
			fileName = System.getProperty("fileName");
		}
		if( fileName == null ) {
			System.out.println("fileName is required");
			System.exit(-1);
		}

		ContinuosInputStream buf = new ContinuosInputStream(fileName,true);


		String line = null;


		while( ( line=buf.readLine()) != null ) {
			System.out.println("line='"+line+"'");
		}

		buf.close();


	}
	
	/**
	 * Reads the next byte of data from the input stream. The value byte is
	 * returned as an <code>int</code> in the range <code>0</code> to
	 * <code>255</code>. If no byte is available because the end of the stream
	 * has been reached, the value <code>-1</code> is returned. This method
	 * blocks until input data is available, the end of the stream is detected,
	 * or an exception is thrown.
	 *
	 * <p> A subclass must provide an implementation of this method.
	 *
	 * @return     the next byte of data, or <code>-1</code> if the end of the
	 *             stream is reached.
	 * @exception  IOException  if an I/O error occurs.
	 */
	public int read() throws java.io.IOException	{

		while( !eof && in.getFilePointer() == in.length() ) {
			try { Thread.sleep(freq); } catch(Exception ex) {}
			if( eof ) {
				return -1;
			}
		}

		return in.read();
	}

	public long getInputLength() {
		long ret = -1;
		try {
			ret = in.length();
		} catch (IOException e) {
		}
		return ret;
	}
	
	public synchronized void unreadLines(int want) throws IOException {

		long pos = in.length();
		int found = 0;
		while(pos > 0 && found < want) {
			in.seek(pos--);
			if(in.read() == '\n'){
				found++;
			}
		}
	}

	public synchronized String readLine()	throws EOFException	{
		StringBuffer ret = new StringBuffer();
		int sz = 0;
		int c = 0;

		try {

			while( (c = read()) != -1 ) {
				if(c == '\n' ) {
					break;
				} else {
					ret.append((char)c);
					sz++;
				}
			}
		} catch(IOException ex) {
			ex.printStackTrace();
		}

		if( c == -1 && sz == 0 ) {
			throw new EOFException();
		}

		if( sz > 0 && ret.charAt(sz-1) == '\r') {
			ret.deleteCharAt(sz-1);
		}


		return ret.toString();
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
	 * @param newFreq int
	 */
	public void setFreq(int newFreq) {
		freq = newFreq;
	}

	
	public int available() throws IOException {		
		return (int)(in.length()-in.getFilePointer());
	}


	
}
