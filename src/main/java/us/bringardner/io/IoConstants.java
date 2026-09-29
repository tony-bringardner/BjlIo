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

/**
 * Constants used by the classes in this package.
 * <p>
 * Refer to them as {@code IoConstants.CR} (or with {@code import static us.bringardner.io.IoConstants.*;}).
 */
public final class IoConstants {

	private IoConstants() {
		//  Constants only, never instantiated.
	}
	
	/**
	 * The default buffer size for the line readers and writers.
	 */
	public static final int DEFAULT_BUFFER_SIZE = 1024*4;
	
	/**
	 * An ASCII return character(\r == ASCII(13) )
	 */
	public static final int CR = (int)'\r';
	
	/**
	 * An ASCII new line character (\n == ASCII(10) )
	 */

	public static final int NL = (int)'\n';
	
	
	/**
	 * A Carriage Return / Line Feed pair.
	 * <p>
	 * NOTE: Java arrays can't be made read-only, so any code can change this array.
	 * The classes in this package use their own private copies.
	 * @deprecated use {@link #crlf()}, which returns a new copy each time.
	 */
	@Deprecated
	public static final byte [] CRNL = new byte [] { '\r','\n'};

	/**
	 * @return a new array holding a Carriage Return / Line Feed pair.
	 */
	public static byte [] crlf() {
		return new byte [] { '\r','\n'};
	}
}
