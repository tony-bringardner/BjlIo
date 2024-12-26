// ~version~V000.00.00-
/**
 CRLFWriter.java

 Copyright 1998-2009 Tony Bringardner

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.   

*/


package us.bringardner.io;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Tony Bringardner
 *
 */
public class LFLineWriter extends AbstractLineWriter {

	/**
	 * @param out
	 */
	public LFLineWriter(OutputStream out) {		
		super(out, new byte[] {NL});
	}

	/**
	 * @param out
	 * @param buffSize
	 * 
	 * @throws IOException 
	 */
	public LFLineWriter(OutputStream out,int buffSize) throws IOException {		
		super(out, buffSize, new byte[] {NL});
	}
	
	public LFLineWriter(File outputFile) throws IOException {		
		super(outputFile, new byte[] {NL});
	}

}
