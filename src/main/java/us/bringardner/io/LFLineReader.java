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
 * InputStream that reads lines terminated by a LF
 * 
 *
 */
public class LFLineReader extends FilterInputStream implements ILineReader, IoConstants {

	
	private long bytes;
	private long lastReadTime;

	public LFLineReader(File inputFile) throws FileNotFoundException {
		this(new FileInputStream(inputFile));
	}

	public LFLineReader(InputStream in) {
		super((in instanceof BufferedInputStream) ? in	: new BufferedInputStream(in));
	}

	public LFLineReader(String str) {
		this(new ByteArrayInputStream(str.getBytes()));
	}

	public void close() throws IOException {
		super.close();
	}

	public long getBytesIn() {
		return bytes;
	}

	public String readLine() throws IOException {
		StringBuffer bf = new StringBuffer();
		int i = 0;
		boolean done = false;
		int cnt = 0;
		
		while (!done && (i = read()) != -1) {
			if (i == NL) {
				done = true;
			} else {
				bf.append((char)i);
				cnt++;
			}	
		}

		String ret = null;
		if (i >= 0) {
			ret = bf.toString();
			bytes += cnt;
		}
		
		lastReadTime = System.currentTimeMillis();
		return ret;

	}

	public int inputAvailable() throws IOException {
		
		return super.available();
	}

	public long getLastReadTime() {
		
		return lastReadTime;
	}
}