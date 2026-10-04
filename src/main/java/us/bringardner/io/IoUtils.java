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
 * Small I/O helpers shared by the BJL projects.
 */
public final class IoUtils {

	private IoUtils() {
		//  Static methods only, never instantiated.
	}

	/**
	 * Close something when a failure to close doesn't matter: cleaning up after an error that
	 * is already being reported, or a socket whose session is over anyway. Never use it where a
	 * failed close means lost data (an output stream that still has to be flushed, say).
	 * 
	 * @param c the thing to close; null is ignored
	 */
	public static void closeQuietly(AutoCloseable c) {
		if( c != null ) {
			try {
				c.close();
			} catch (Exception e) {
				//  Ignored, see the method comment
			}
		}
	}

	/**
	 * Close each of them, ignoring nulls and failures, see {@link #closeQuietly(AutoCloseable)}.
	 * A failure closing one doesn't stop the others being closed.
	 * 
	 * @param all the things to close
	 */
	public static void closeQuietly(AutoCloseable... all) {
		if( all != null ) {
			for(AutoCloseable c : all) {
				closeQuietly(c);
			}
		}
	}
}
