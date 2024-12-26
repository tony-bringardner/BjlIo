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
package us.bringardner.io;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Tony Bringardner
 * InputStream that reads lines terminated by CRLF pair.
 * 
 * Sub-classing FilterInputStream allows this class to be used
 * as an InputStream.  However, it's probably not a good idea since 
 * that would violate the basic assumptions of the protocol.
 *   
 */
public class CRLFLineReader extends FilterInputStream implements ILineReader,IoConstants {

	private long bytes;
	private long lastReadTime;

	/**
	 * Construct a CRLFInputStream from a File.
	 * 
	 * @param File to read.
	 * @throws FileNotFoundException
	 */
	public CRLFLineReader(File inputFile) throws FileNotFoundException {
		this(new FileInputStream(inputFile));
	}

	/**
	 * Construct a CRLFInputStream from the provided InputStream.
	 * 
	 * @param in
	 */
	public CRLFLineReader(InputStream in) {
		super((in instanceof BufferedInputStream) ? in : new BufferedInputStream(in));
	}

	
	/**
	 * Construct a CRLFInputStream that will read lines
	 * from the provided String.
	 * 
	 * @param str
	 */
	public CRLFLineReader(String str) {
		this(new ByteArrayInputStream(str.getBytes()));
	}

	
	/* 
	 * Close the Stream
	 * 
	 * @see java.io.FilterInputStream#close()
	 */
	public void close() throws IOException {
		super.close();
	}

	
	/* 
	 * @see us.bringardner.io.LineReader#getBytesIn()
	 */
	public long getBytesIn() {
		return bytes;
	}

	
	/* 
	 * Read a line from the input.  The line will include all
	 * text up to (but NOT including) the next CRLF pair.
	 * 
	 * @see us.bringardner.io.LineReader#readLine()
	 */
	public String readLine() throws IOException {
		
		StringBuffer bf = new StringBuffer();
		int i = 0;
		int lst = (int)'\0';
		boolean done = false;
		int cnt = 0;

		//  Read until we reach the EOF or a CRLF pair.
		while (!done && (i = read()) != -1) {
			if (i == NL && lst == CR) {
				done = true;
			} else {
				cnt++;
				bf.append((char)i);
				lst = i;
			}
		}

		String ret = null;
		//  If nothing is read, ret is null.
		if (cnt > 0) {
			//  Ignore the CR is we read one (could be EOF and none was read).
			if( lst == CR ){
				cnt --;
			}
			ret = bf.substring(0,cnt);
			bytes+=cnt;
		}
		
		
		lastReadTime=System.currentTimeMillis();
		return ret;
	}
	
	public int inputAvailable() throws IOException {
		return super.available();
	}

	public long getLastReadTime() {
		
		return lastReadTime;
	}
	
}