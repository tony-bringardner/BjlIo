// ~version~V000.01.01-V000.01.00-V000.00.00-
/**
 * Copyright 1998-2009 Tony Bringardner
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *   
 *   
 *	@author Tony Bringardner   
 */

package us.bringardner.io;

import java.io.*;
/**
 * This is really just a utility to read from a remote stream and echo on a local screen
 * Creation date: (11/8/01 8:31:23 AM)
 * @author: Tony Bringardner
 */
public class TelnetInputStream extends InputStream {
	private OutputStream out;
	private InputStream in;
	

	/**
	 * TelnetInputStream constructor comment.
	 * USAGE : 
	 * 	
	 * TelnetOutputStream to = new TelnetOutputStream(sock.getOutputStream());
   TelnetInputStream  ti = new TelnetInputStream(sock.getInputStream(),to);

	in = new DataInputStream(ti);
	out = new PrintStream(to);


	 */
	public TelnetInputStream(InputStream input, OutputStream output) {
		super();
		in = input;
		out = output;	
	}

	public void close() throws IOException {
		if( out != null ) {
			try { out.close(); } catch(Exception ex) {}
		}
		in.close();
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
	public int read() throws IOException {
		int ret = in.read();
		
		if( ret >= 0  ) {
			out.write(ret);
			out.flush();
		}

		return ret;
	}
}
