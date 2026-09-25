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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * Write the same data to several OutputStreams.
 * <p>
 * Every operation is attempted on every stream, even if one of them fails.
 * The first IOException is thrown after all streams have been processed, 
 * with any later ones added as suppressed exceptions.
 */
public class   TeeOutputStream extends OutputStream {
	OutputStream [] streams;
	
	public TeeOutputStream(OutputStream ... args) {
		Objects.requireNonNull(args, "streams are required");
		streams = args.clone();
		for (int idx = 0; idx < streams.length; idx++) {
			Objects.requireNonNull(streams[idx], "stream "+idx+" is null");
		}
	}

	private interface StreamOp {
		void apply(OutputStream out) throws IOException;
	}

	private void forEach(StreamOp op) throws IOException {
		IOException error = null;
		for(OutputStream out : streams) {
			try {
				op.apply(out);
			} catch (IOException | RuntimeException e) {
				IOException ioe = e instanceof IOException ? (IOException)e : new IOException(e);
				if( error == null ) {
					error = ioe;
				} else {
					error.addSuppressed(ioe);
				}
			}
		}
		if( error != null ) {
			throw error;
		}
	}


	/**
	 * Write a single byte to all streams.
	 * 
	 * @param c
	 * @throws IOException
	 */

	public void write(int c) throws IOException	{
		forEach(out -> out.write(c));
	}
	
	@Override
	public void write(byte[] b) throws IOException {
		forEach(out -> out.write(b));
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		forEach(out -> out.write(b,off,len));
	}

	/**
	 * Flush and close all of the streams.
	 */
	public void close() throws IOException	{
		IOException error = null;
		try {
			flush();
		} catch (IOException e) {
			error = e;
		}
		try {
			forEach(OutputStream::close);
		} catch (IOException e) {
			if( error == null ) {
				error = e;
			} else {
				error.addSuppressed(e);
			}
		}
		if( error != null ) {
			throw error;
		}
	}


	/**
	 * Flush all of the streams.
	 */
	public void flush() throws IOException	{
		forEach(OutputStream::flush);
	}



	/** Test driver */
	public static void main(String args[]) throws Exception		{
		FileOutputStream fos =	new FileOutputStream("test.out");
		TeeOutputStream  tos =	new TeeOutputStream(fos, System.out);
		PrintWriter      pw  =	new PrintWriter(new OutputStreamWriter(tos));

		pw.println("Testing line 1");
		pw.println("Testing line 2");

		pw.close();
	}
}
