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
package us.bringardner.io;

import java.io.IOException;
import java.io.InputStream;

public class MonitoredInputStream extends InputStream {

	
	
	InputStream target;
	IStreamMonitor monitor;
	long totalRead=0;
	long blockSize = (4*1024);
	boolean completed=false;
	
	public MonitoredInputStream(InputStream input, IStreamMonitor monitor) {
		if( input == null || monitor == null ) {
			throw new RuntimeException("Both input and monitor are required");
		}
		this.target = input;
		this.monitor = monitor;
	}
	
	public MonitoredInputStream(InputStream input,long blockSize, IStreamMonitor monitor) {
		this(input, monitor);
		this.blockSize = blockSize;
	}
	
	@Override
	public int available() throws IOException {
		return target.available();
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
	public synchronized void mark(int readlimit) {
		target.mark(readlimit);
	}
	@Override
	public boolean markSupported() {
		return target.markSupported();
	}
	
	@Override
	public synchronized void reset() throws IOException {
		target.reset();
	}
	
	@Override
	public long skip(long n) throws IOException {
		return target.skip(n);
	}
	
	@Override
	public String toString() {
		return target.toString();
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		int ret = read(b,0,b.length);
		return ret;
	}
	
	private void addToTotal(int count) {
		if(totalRead==0) {
			monitor.start();			
		}
		if(count >=0 && (totalRead+= count) % blockSize == 0l) {
			monitor.update(totalRead, blockSize);
		}		
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int ret = 0;
		for (int idx = off; idx < len; idx++) {
			int i = read();
			if( i < 0) {
				if( idx == off) {
					return -1;
				}
				break;
			} else {
				b[idx] = (byte)i;
				ret++;
			}
		}
		
		return ret;
	}
	
	@Override
	public int read() throws IOException {
		int ret = target.read();
		if( ret >= 0 ) {
			addToTotal(1);
		} else {
			if( !completed) {
				monitor.complete(totalRead);
				completed = true;
			}
		}
		return ret;
	}
	
	@Override
	public void close() throws IOException {
		target.close();
		if( !completed) {
			monitor.complete(totalRead);
			completed = true;
		}
	}


}
