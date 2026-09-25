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
 */
package us.bringardner.io;

import java.io.IOException;

/**
 * Thrown by {@link AbstractLineReader#readLine()} when a line is longer than
 * {@link AbstractLineReader#setMaxLineLength(int)} allows.
 */
public class LineTooLongException extends IOException {

	private static final long serialVersionUID = 1L;
	private final int maxLineLength;

	public LineTooLongException(int maxLineLength) {
		super("line longer than "+maxLineLength+" bytes");
		this.maxLineLength = maxLineLength;
	}

	/**
	 * @return the limit that was exceeded.
	 */
	public int getMaxLineLength() {
		return maxLineLength;
	}
}
