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
 * ~version~V000.01.03-V000.00.01-V000.00.00-
 */
/**
 * 
 */
package us.bringardner.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author tony
 *  Monitored
 */
public class MonitoredOutputStream extends OutputStream {

	OutputStream target;
	IStreamMonitor monitor;
	long total=0;
	long blockSize=(4*1024);
	
	public MonitoredOutputStream(OutputStream output, IStreamMonitor monitor) {
		if( output == null || monitor == null ) {
			throw new RuntimeException("Both output and moniotor are required");
		}
		target = output;
		this.monitor = monitor;
	}
	
	public MonitoredOutputStream(OutputStream output, long bloackSize, IStreamMonitor monitor) {
		this(output, monitor);
		this.blockSize = bloackSize;		
	}
	
	private void addToTotal(int count) {
		if( total == 0 ) {
			monitor.start();
		}
		if(count >=0 && (total+= count) % blockSize == 0l) {
			monitor.update(total, blockSize);
		}		
	}
	
	@Override
	public void write(int arg0) throws IOException {
		target.write(arg0);
		addToTotal(1);

	}
	
	@Override
	public void close() throws IOException {
		target.close();
		monitor.complete(total);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof MonitoredInputStream) {
			MonitoredInputStream mis = (MonitoredInputStream) obj;
			return target.equals(mis.target);
		} else {
			return false;
		}
	}
	
	@Override
	public void flush() throws IOException {
		target.flush();
	}
	
	@Override
	public String toString() {
		return target.toString();
	}
	
	@Override
	public void write(byte[] b) throws IOException {		
		this.write(b, 0, b.length);
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		for (int idx = off; idx < len; idx++) {
			this.write(b[idx]);
		}
	}
	
}
