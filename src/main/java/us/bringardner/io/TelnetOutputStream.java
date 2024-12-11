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
 * 
 * Creation date: (11/8/01 8:41:37 AM)
 * @author: Tony Bringardner
 */
public class TelnetOutputStream extends OutputStream {
	private OutputStream out;
	/**
	 * TelnetOutputStream constructor comment.
	 */
	public TelnetOutputStream(OutputStream newOut) {
		out = newOut;
	}

	
	/**
	 * Writes the specified byte to this output stream. The general 
	 * contract for <code>write</code> is that one byte is written 
	 * to the output stream. 
	 * 
	 * While the telnet protocol is more complicated (see RFC 206),
	 * but the only thing the output stream does is make sure nothing > 'Z'
	 * is output (transmitted).
	 * First the byte is restricted to the first 7 bits then if it's > 'Z' it's ignored.
	 * 
	 *
	 * @param      b   the <code>byte</code>.
	 * @exception  IOException  if an I/O error occurs. In particular, 
	 *             an <code>IOException</code> may be thrown if the 
	 *             output stream has been closed.
	 */
	public void write(int b) throws IOException {
		int b1 = b & 0b1111111;
		
		if( b1 <= 'Z') {
			out.write(b1);
		}
	}
}
