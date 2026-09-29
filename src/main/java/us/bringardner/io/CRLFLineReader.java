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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * @author Tony Bringardner
 * InputStream that reads lines terminated by CRLF pair.
 * A lone CR or LF is considered part of the line.
 * 
 * Sub-classing FilterInputStream allows this class to be used
 * as an InputStream.  However, it's probably not a good idea since 
 * that would violate the basic assumptions of the protocol.
 *   
 */
public class CRLFLineReader extends AbstractLineReader {

	/**
	 * Construct a CRLFInputStream from a File.
	 * 
	 * @param inputFile the file to read.
	 * @throws FileNotFoundException
	 */
	public CRLFLineReader(File inputFile) throws FileNotFoundException {
		this(new FileInputStream(inputFile));
	}

	/**
	 * Construct a CRLFInputStream from a File.
	 * 
	 * @param inputFile the file to read.
	 * @param charset used to convert bytes to a String.
	 * @throws FileNotFoundException
	 */
	public CRLFLineReader(File inputFile, Charset charset) throws FileNotFoundException {
		this(new FileInputStream(inputFile), charset);
	}

	/**
	 * Construct a CRLFInputStream from the provided InputStream (lines are decoded as UTF-8).
	 * 
	 * @param in
	 */
	public CRLFLineReader(InputStream in) {
		this(in, DEFAULT_CHARSET);
	}

	/**
	 * Construct a CRLFInputStream from the provided InputStream.
	 * 
	 * @param in
	 * @param charset used to convert bytes to a String.
	 */
	public CRLFLineReader(InputStream in, Charset charset) {
		super(in, charset);
	}

	/**
	 * Construct a CRLFInputStream that will read lines
	 * from the provided String.
	 * 
	 * @param str
	 */
	public CRLFLineReader(String str) {
		this(new ByteArrayInputStream(str.getBytes(DEFAULT_CHARSET)));
	}

	@Override
	protected boolean isCrlfTerminated() {
		return true;
	}
}
