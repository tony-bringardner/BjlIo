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
import java.util.Objects;
/**
 * Reads from a remote stream and echoes every byte it reads to another stream (e.g. a local screen).
 * <pre>
 * TelnetOutputStream to = new TelnetOutputStream(sock.getOutputStream());
 * TelnetInputStream  ti = new TelnetInputStream(sock.getInputStream(), System.out);
 * </pre>
 * Creation date: (11/8/01 8:31:23 AM)
 * @author: Tony Bringardner
 */
public class TelnetInputStream extends InputStream {
	private final OutputStream out;
	private final InputStream in;

	/**
	 * @param input the stream to read from.
	 * @param output every byte read is echoed (written and flushed) to this stream.
	 */
	public TelnetInputStream(InputStream input, OutputStream output) {
		super();
		in = Objects.requireNonNull(input, "input is required");
		out = Objects.requireNonNull(output, "output (the echo stream) is required");
	}

	/**
	 * Close the input and the echo output stream.
	 */
	public void close() throws IOException {
		try {
			//  Errors closing the echo stream are ignored, the input is always closed.
			try { out.close(); } catch(Exception ex) {}
		} finally {
			in.close();
		}
	}

	@Override
	public int available() throws IOException {
		return in.available();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	/**
	 * Read up to len bytes and echo them to the output stream with a single write and flush.
	 */
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		Objects.checkFromIndexSize(off, len, b.length);
		if( len == 0 ) {
			return 0;
		}
		int ret = in.read(b, off, len);
		if( ret > 0 ) {
			out.write(b, off, ret);
			out.flush();
		}
		return ret;
	}

	
	/**
	 * Reads the next byte of data from the input stream. The value byte is
	 * returned as an <code>int</code> in the range <code>0</code> to
	 * <code>255</code>. If no byte is available because the end of the stream
	 * has been reached, the value <code>-1</code> is returned. This method
	 * blocks until input data is available, the end of the stream is detected,
	 * or an exception is thrown.
	 * The byte is also written (and flushed) to the echo stream.
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
