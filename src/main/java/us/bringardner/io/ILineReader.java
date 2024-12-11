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
 * ~version~V000.01.01-V000.00.01-V000.00.00-
 */
package us.bringardner.io;

import java.io.IOException;



/**
 * @author Tony Bringardner
 * 
 * Most Internet protocols use 'line' based command structure.
 * Command lines are normally terminated with a CRLF (Carriage return / new line) pair
 * 
 * Objects that implement this Interface know how to read a line of text
 * from an InputStream. 
 */
public interface ILineReader  extends IoConstants,AutoCloseable {
	
	
	
	/**
	 * Read a line of text from an InputStream.
	 * 
	 * @return The next line of text from the InputStream or null if the EOF has been reached.
	 * @throws IOException
	 */
	public String readLine() throws IOException;
	
	
	/**
	 * Close the LineReader and the associated InputStream.
	 * 
	 * @throws IOException
	 */
	public void   close()    throws IOException;
	
	/**
	 * @return The number of bytes read via the readLine method.
	 */
	public long getBytesIn();
	
	public long getLastReadTime();
	public int inputAvailable() throws IOException ;
}
