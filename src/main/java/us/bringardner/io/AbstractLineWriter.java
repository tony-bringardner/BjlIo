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
// ~version~V000.00.01-V000.00.00-
package us.bringardner.io;

/**
 * @author Tony Bringardner
 */
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;


/**
 *  
 * OutputStream that writes lines terminated with an
 * predefined line terminator.
 * 
 * 
 */
public abstract class AbstractLineWriter  extends FilterOutputStream implements ILineWriter,IoConstants 
{
	private long bytes;
	private boolean autoFlush = true;
	private long lastWriteTime;
	private byte [] terminator;
	
	public AbstractLineWriter(OutputStream outputStream, int outBufSize,byte [] terminator)	throws IOException	{
		this(new BufferedOutputStream(outputStream,outBufSize),terminator);
	}
	
	public AbstractLineWriter(File outputFile, byte [] terminator)	throws IOException	{
		this(new FileOutputStream(outputFile),terminator);		
	}
	
	public AbstractLineWriter(OutputStream out,byte [] terminator) {
		super(out);
		this.terminator = terminator;
	}

	public void flush()	throws IOException	{
		out.flush();
	}

	public long getBytesOut()	{
		return bytes;
	}

	public void writeLine(String line) throws IOException	{
		byte [] data = line.getBytes();
		out.write(data);
		out.write(terminator);
		bytes+=(data.length+terminator.length);
		lastWriteTime=System.currentTimeMillis();
		if( autoFlush ) {
			flush();
		}
	}
	
	public void write(String line) throws IOException	{
		byte [] data = line.getBytes();
		out.write(data);
		bytes+=(data.length);
		lastWriteTime=System.currentTimeMillis();
		if( autoFlush ) {
			flush();
		}
	}
	
	/* (non-Javadoc)
	 * @see us.bringardner.io.LineWriter#isAutoFlush()
	 */
	public boolean isAutoFlush() {
	
		return autoFlush;
	}
	
	/* (non-Javadoc)
	 * @see us.bringardner.io.LineWriter#setAutoFlush(boolean)
	 */
	public void setAutoFlush(boolean autoFlush) {
		this.autoFlush= autoFlush;
	}

	public long getLastWriteTime() {
		return lastWriteTime;
	}
	
}
