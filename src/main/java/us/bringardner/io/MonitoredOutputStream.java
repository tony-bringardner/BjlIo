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
import java.io.OutputStream;
import java.util.Objects;

/**
 * @author tony
 *  An OutputStream that reports progress to an {@link IStreamMonitor}.
 *  <p>
 *  start() is called before the first byte is counted, update(total, blockSize) is called each time
 *  the total crosses a multiple of blockSize and complete(total) is called once, on close.
 */
public class MonitoredOutputStream extends OutputStream {

	private final OutputStream target;
	private final IStreamMonitor monitor;
	private long total=0;
	private long blockSize=(4*1024);
	private boolean started = false;
	private boolean completed = false;
	
	public MonitoredOutputStream(OutputStream output, IStreamMonitor monitor) {
		if( output == null || monitor == null ) {
			throw new IllegalArgumentException("Both output and monitor are required");
		}
		target = output;
		this.monitor = monitor;
	}
	
	public MonitoredOutputStream(OutputStream output, long blockSize, IStreamMonitor monitor) {
		this(output, monitor);
		if( blockSize <= 0 ) {
			throw new IllegalArgumentException("blockSize must be > 0");
		}
		this.blockSize = blockSize;		
	}
	
	private void addToTotal(long count) {
		if( count <= 0 ) {
			return;
		}
		if( !started ) {
			started = true;
			monitor.start();
		}
		long before = total;
		total += count;
		//  Report every block boundary that was crossed (same calls as when data is written one byte at a time).
		for(long block = (before / blockSize + 1) * blockSize; block <= total; block += blockSize) {
			monitor.update(block, blockSize);
		}
	}
	
	@Override
	public void write(int arg0) throws IOException {
		target.write(arg0);
		addToTotal(1);

	}
	
	@Override
	public void close() throws IOException {
		try {
			target.close();
		} finally {
			if( !completed ) {
				completed = true;
				monitor.complete(total);
			}
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
		Objects.checkFromIndexSize(off, len, b.length);
		target.write(b, off, len);
		addToTotal(len);
	}
	
}
