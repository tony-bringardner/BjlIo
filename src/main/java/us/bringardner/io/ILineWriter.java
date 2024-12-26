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
 * Command lines are normally terminated with a CRLF (Carriage Return / Line feed) pair.
 * 
 * Objects that implement this Interface know how to write a line of text
 * from to OutputStream. 
 */
public interface ILineWriter extends IoConstants,AutoCloseable {
	
	
	
	/**
	 * Close this LineWriter and it's associated OutputStream.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException;

	
	/**
	 * Flush the outputStream.  This ensures that all lines previously written
	 * are sent to it's destination.
	 * 
	 * @throws IOException
	 */
	public void flush() throws IOException;

	
	/**
	 * Set the 'Auto Flush' flag. 
	 * 
	 * @param true if each line should be flushed as it is written.
	 */
	public void setAutoFlush(boolean trueOrFalse);
	
	
	/**
	 * Current value of the 'Auto Flush' flag.
	 * 
	 * @return true if the 'Auto Flush' flag is currently 'on'
	 */
	public boolean isAutoFlush();
	
	
	/**
	 * Write a line of text to the OutputStream.  The appropriate 
	 * End-Of-Line will be added.
	 * 
	 * @param line
	 * @throws IOException
	 */
	public void writeLine(String line) throws IOException;
	
	/**
	 * Write a line of text to the OutputStream.   
	 * No End-Of-Line will be added.
	 * 
	 * @param line
	 * @throws IOException
	 */
	
	public void write(String line) throws IOException;
	
	public long getBytesOut();
	
	public long getLastWriteTime();
}