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
import java.util.Objects;

/**
 * An InputStream that reports progress to an {@link IStreamMonitor}.
 * <p>
 * start() is called before the first byte is counted, update(total, blockSize) is called each time
 * the total crosses a multiple of blockSize and complete(total) is called once, at EOF or close.
 */
public class MonitoredInputStream extends InputStream {

	
	
	private final InputStream target;
	private final IStreamMonitor monitor;
	private long totalRead=0;
	private long blockSize = (4*1024);
	private boolean completed=false;
	private boolean started=false;
	private long markedTotal = -1;
	
	public MonitoredInputStream(InputStream input, IStreamMonitor monitor) {
		if( input == null || monitor == null ) {
			throw new IllegalArgumentException("Both input and monitor are required");
		}
		this.target = input;
		this.monitor = monitor;
	}
	
	public MonitoredInputStream(InputStream input,long blockSize, IStreamMonitor monitor) {
		this(input, monitor);
		if( blockSize <= 0 ) {
			throw new IllegalArgumentException("blockSize must be > 0");
		}
		this.blockSize = blockSize;
	}
	
	@Override
	public int available() throws IOException {
		return target.available();
	}
	
	@Override
	public synchronized void mark(int readlimit) {
		target.mark(readlimit);
		markedTotal = totalRead;
	}
	@Override
	public boolean markSupported() {
		return target.markSupported();
	}
	
	@Override
	public synchronized void reset() throws IOException {
		target.reset();
		//  Bytes read after the mark will be read again, don't count them twice.
		if( markedTotal >= 0 ) {
			totalRead = markedTotal;
		}
	}
	
	@Override
	public long skip(long n) throws IOException {
		long ret = target.skip(n);
		addToTotal(ret);
		return ret;
	}
	
	@Override
	public String toString() {
		return target.toString();
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		return read(b,0,b.length);
	}
	
	private void addToTotal(long count) {
		if( count <= 0 ) {
			return;
		}
		if(!started) {
			started = true;
			monitor.start();			
		}
		long before = totalRead;
		totalRead += count;
		//  Report every block boundary that was crossed (same calls as when data is read one byte at a time).
		for(long block = (before / blockSize + 1) * blockSize; block <= totalRead; block += blockSize) {
			monitor.update(block, blockSize);
		}
	}

	private void complete() {
		if( !completed) {
			completed = true;
			monitor.complete(totalRead);
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		Objects.checkFromIndexSize(off, len, b.length);
		if( len == 0 ) {
			return 0;
		}
		int ret = target.read(b, off, len);
		if( ret < 0 ) {
			complete();
		} else {
			addToTotal(ret);
		}
		return ret;
	}
	
	@Override
	public int read() throws IOException {
		int ret = target.read();
		if( ret >= 0 ) {
			addToTotal(1);
		} else {
			complete();
		}
		return ret;
	}
	
	@Override
	public void close() throws IOException {
		try {
			target.close();
		} finally {
			complete();
		}
	}


}
