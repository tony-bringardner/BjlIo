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
 * ~version~V000.01.04-V000.01.01-V000.00.01-V000.00.00-
 */
package us.bringardner.io.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.core.BaseThread;
import us.bringardner.io.CRLFLineReader;
import us.bringardner.io.CRLFLineWriter;
import us.bringardner.io.ContinuosInputStream;
import us.bringardner.io.ILineReader;
import us.bringardner.io.ILineWriter;
import us.bringardner.io.IStreamMonitor;
import us.bringardner.io.LFLineReader;
import us.bringardner.io.LFLineWriter;
import us.bringardner.io.MonitoredInputStream;
import us.bringardner.io.MonitoredOutputStream;
import us.bringardner.io.TeeOutputStream;
import us.bringardner.io.TelnetInputStream;
import us.bringardner.io.TelnetOutputStream;

class TestIo {

	String testLine = "Test line";
	int lineCount = 10;

	@TempDir
	File tempDir;

	@Test
	void testContinuosInputStream() throws Exception {
		File file = new File(tempDir,"ContinuousIOTestFile.txt");
		//  Create the file before the reader opens it.
		PrintStream out = new PrintStream(file);
		BaseThread thread = new BaseThread() {

			@Override
			public void run() {
				//  add data to the file every 50ms
				started = running = true;
				try {
					int cnt = 0;
					while(!stopping) {						
						out.print("line "+(cnt++)+". \n");
						out.flush();
						Thread.sleep(50);
					}
				} catch (InterruptedException e) {
					//  stop(timeout,true) interrupts the sleep
				} finally {
					out.close();
					running = false;
				}
			}

		};
		thread.start();
		
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
			try(ContinuosInputStream in = new ContinuosInputStream(file,false)) {
				for(int cnt=0; cnt < 7; cnt++ ) {
					assertEquals("line "+cnt+". ", in.readLine());
				}
			} finally {
				assertTrue(thread.stop(5000, true),"Writer thread did not stop");
			}
		});
	}

	@Test
	void testContinuosInputStreamEofAfterDataIsRead() throws IOException {
		File file = new File(tempDir,"eof.txt");
		Files.write(file.toPath(), "a\r\nb\nlast".getBytes(StandardCharsets.UTF_8));
		try(ContinuosInputStream in = new ContinuosInputStream(file,false)) {
			in.setEof(true);
			assertEquals("a", in.readLine());
			assertEquals("b", in.readLine());
			assertEquals("last", in.readLine(),"Partial last line should be returned at EOF");
			assertThrows(EOFException.class, in::readLine);
		}
	}

	@Test
	void testContinuosInputStreamUnreadLines() throws IOException {
		File file = new File(tempDir,"tail.txt");
		Files.write(file.toPath(), "l1\nl2\nl3\n".getBytes(StandardCharsets.UTF_8));
		try(ContinuosInputStream in = new ContinuosInputStream(file,true)) {
			in.setEof(true);
			in.unreadLines(2);
			assertEquals("l2", in.readLine());
			assertEquals("l3", in.readLine());
			assertThrows(EOFException.class, in::readLine);

			in.unreadLines(10);
			assertEquals("l1", in.readLine(),"Asking for more lines than the file has should start at the beginning");
		}

		//  Larger than the internal buffer, lines spanning block boundaries
		StringBuilder big = new StringBuilder();
		for(int idx=0; idx < 5000; idx++ ) {
			big.append("line number ").append(idx).append('\n');
		}
		Files.write(file.toPath(), big.toString().getBytes(StandardCharsets.UTF_8));
		try(ContinuosInputStream in = new ContinuosInputStream(file,true)) {
			in.setEof(true);
			in.unreadLines(3000);
			for(int idx=2000; idx < 5000; idx++ ) {
				assertEquals("line number "+idx, in.readLine());
			}
			assertThrows(EOFException.class, in::readLine);
		}
	}

	@Test
	void testContinuosInputStreamTruncatedFile() throws Exception {
		File file = new File(tempDir,"truncate.txt");
		Files.write(file.toPath(), "old line 1\nold line 2\n".getBytes(StandardCharsets.UTF_8));
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
			try(ContinuosInputStream in = new ContinuosInputStream(file,false)) {
				in.setFreq(10);
				assertEquals("old line 1", in.readLine());
				assertEquals("old line 2", in.readLine());
				//  truncate (log rotation) and write new data
				try(RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
					raf.setLength(0);
					raf.write("new\n".getBytes(StandardCharsets.UTF_8));
				}
				assertEquals("new", in.readLine());
			}
		});
	}

	@Test
	void testContinuosInputStreamCloseAndInterrupt() throws Exception {
		File file = new File(tempDir,"block.txt");
		Files.write(file.toPath(), new byte[0]);

		//  close() from another thread ends a blocked read
		try(ContinuosInputStream in = new ContinuosInputStream(file,false)) {
			AtomicReference<Object> result = new AtomicReference<>();
			Thread reader = new Thread(() -> {
				try {
					result.set(in.read());
				} catch (Throwable e) {
					result.set(e);
				}
			});
			reader.start();
			Thread.sleep(200);
			in.close();
			reader.join(5000);
			assertTrue(!reader.isAlive(),"close did not end the blocked read");
			assertEquals(-1, result.get());
		}

		//  interrupt ends a blocked read with InterruptedIOException
		try(ContinuosInputStream in = new ContinuosInputStream(file,false)) {
			AtomicReference<Object> result = new AtomicReference<>();
			Thread reader = new Thread(() -> {
				try {
					result.set(in.readLine());
				} catch (Throwable e) {
					result.set(e);
				}
			});
			reader.start();
			Thread.sleep(200);
			reader.interrupt();
			reader.join(5000);
			assertTrue(!reader.isAlive(),"interrupt did not end the blocked read");
			assertTrue(result.get() instanceof InterruptedIOException,"Expected InterruptedIOException but got "+result.get());
		}
	}


	@Test
	public void testAllILineWriteAndILineReaders() throws IOException{

		testCRLFLineReadAndWrite(CRLFLineWriter.class, CRLFLineReader.class, "\r\n");
		testCRLFLineReadAndWrite(LFLineWriter.class, LFLineReader.class, "\n");

	}

	public void testCRLFLineReadAndWrite (Class<? extends ILineWriter> writeClass,Class<? extends ILineReader> readerClass,String lineTerminator) throws IOException {

		ByteArrayOutputStream bo = new ByteArrayOutputStream();

		try {
			ILineWriter 	lfw = writeClass.getConstructor(OutputStream.class).newInstance(bo);
			for(int idx=0; idx < lineCount; idx++ ) {
				lfw.writeLine(testLine);
			}
			lfw.flush();
			lfw.close();

			String res = bo.toString();
			String [] lines = res.split(lineTerminator);
			assertEquals(lineCount, lines.length,"Line count is wrong for "+writeClass);
			for (int idx = 0; idx < lines.length; idx++) {
				assertEquals(testLine, lines[idx],writeClass.getName()+" did not write the correct value");
			}

			ByteArrayInputStream bi = new ByteArrayInputStream(res.getBytes());
			ILineReader lfr = readerClass.getConstructor(InputStream.class).newInstance(bi);
			String line = lfr.readLine();
			int cnt = 0;
			while(line != null ) {
				cnt++;
				assertEquals(testLine,line,readerClass.getName()+" did not read the correct value");
				line = lfr.readLine();
			}
			lfr.close();
			assertEquals(lineCount, cnt,readerClass.getName()+ " did not read the correct number of lines");

		} catch (Throwable e) {
			e.printStackTrace();
			throw new IOException(e);
		}

	}


	@Test
	public void testLineReadAndWrite () throws IOException {


		ByteArrayOutputStream bo = new ByteArrayOutputStream();

		try(ILineWriter lfw = new LFLineWriter(bo)) {
			for(int idx=0; idx < lineCount; idx++ ) {
				lfw.writeLine(testLine);
			}
			lfw.flush();
		}

		String res = bo.toString();
		String [] lines = res.split("\n");
		assertEquals(lineCount, lines.length,"Line count is wrong for LF Writer");
		for (int idx = 0; idx < lines.length; idx++) {
			assertEquals(testLine, lines[idx]);
		}

		ByteArrayInputStream bi = new ByteArrayInputStream(res.getBytes());

		try(ILineReader lfr = new LFLineReader(bi)) {
			String line = lfr.readLine();
			int cnt = 0;
			while(line != null ) {
				cnt++;
				assertEquals(testLine,line,"LF Reader di not read the correct value");
				line = lfr.readLine();
			}
			assertEquals(lineCount, cnt,"LF Reader di not read the correct number of lines");
		}

	}

	@Test
	public void testLastLineWithoutTerminator() throws IOException {
		try(LFLineReader r = new LFLineReader("first\nlast-no-newline")) {
			assertEquals("first", r.readLine());
			assertEquals("last-no-newline", r.readLine());
			assertNull(r.readLine());
		}
		try(CRLFLineReader r = new CRLFLineReader("first\r\nlast-no-newline")) {
			assertEquals("first", r.readLine());
			assertEquals("last-no-newline", r.readLine());
			assertNull(r.readLine());
		}
		//  Empty lines are returned as "", EOF as null
		try(LFLineReader r = new LFLineReader("\n\n")) {
			assertEquals("", r.readLine());
			assertEquals("", r.readLine());
			assertNull(r.readLine());
		}
		try(CRLFLineReader r = new CRLFLineReader("")) {
			assertNull(r.readLine());
		}
	}

	@Test
	public void testCrlfReaderKeepsLoneCrAndLf() throws IOException {
		try(CRLFLineReader r = new CRLFLineReader("a\nb\rc\r\nd\r\r\n\r\n")) {
			assertEquals("a\nb\rc", r.readLine());
			assertEquals("d\r", r.readLine());
			assertEquals("", r.readLine());
			assertNull(r.readLine());
		}
	}

	@Test
	public void testLongLinesAcrossBufferBoundaries() throws IOException {
		//  Put the CR / LF at every possible position around the 4K buffer boundaries.
		for(int len = 4090; len < 4100; len++ ) {
			char [] chars = new char[len];
			java.util.Arrays.fill(chars, 'x');
			String longLine = new String(chars);
			String data = longLine+"\r\n"+longLine+"\r\nend";
			try(CRLFLineReader r = new CRLFLineReader(data)) {
				assertEquals(longLine, r.readLine(),"len="+len);
				assertEquals(longLine, r.readLine(),"len="+len);
				assertEquals("end", r.readLine());
				assertNull(r.readLine());
			}
		}
	}

	@Test
	public void testNonAsciiRoundTrip() throws IOException {
		String text = "café über 日本";
		for(boolean crlf : new boolean[] {true,false}) {
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			try(ILineWriter w = crlf ? new CRLFLineWriter(bo) : new LFLineWriter(bo)) {
				w.writeLine(text);
			}
			assertArrayEquals((text+(crlf ? "\r\n" : "\n")).getBytes(StandardCharsets.UTF_8), bo.toByteArray(),"Writer must use UTF-8");
			InputStream bi = new ByteArrayInputStream(bo.toByteArray());
			try(ILineReader r = crlf ? new CRLFLineReader(bi) : new LFLineReader(bi)) {
				assertEquals(text, r.readLine());
			}
		}

		//  A different charset can be used
		byte [] latin1 = "café\r\n".getBytes(StandardCharsets.ISO_8859_1);
		try(CRLFLineReader r = new CRLFLineReader(new ByteArrayInputStream(latin1), StandardCharsets.ISO_8859_1)) {
			assertEquals("café", r.readLine());
		}
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		try(CRLFLineWriter w = new CRLFLineWriter(bo, StandardCharsets.ISO_8859_1)) {
			w.writeLine("café");
		}
		assertArrayEquals(latin1, bo.toByteArray());
	}

	@Test
	public void testReaderMixedReadAndReadLine() throws IOException {
		try(LFLineReader r = new LFLineReader("abc\ndef\nghi")) {
			assertEquals('a', r.read());
			assertEquals("bc", r.readLine());
			byte [] b = new byte[10];
			int n = r.read(b, 2, 3);
			assertEquals(3, n);
			assertEquals("def", new String(b, 2, 3, StandardCharsets.UTF_8));
			assertEquals("", r.readLine());
			assertEquals("ghi", r.readLine());
			assertEquals(-1, r.read());
		}
	}

	@Test
	public void testWriterCountsAllBytes() throws IOException {
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		try(CRLFLineWriter w = new CRLFLineWriter(bo)) {
			w.writeLine("abc");			// 5
			w.write("de");				// 2
			w.write("0123456789".getBytes(), 5, 3);	// 3
			w.write('x');				// 1
			assertEquals(11, w.getBytesOut());
			assertTrue(w.getLastWriteTime() > 0);
		}
		assertEquals("abc\r\nde567x", bo.toString());
	}

	@Test
	public void testFileWriterIsBuffered() throws IOException {
		File file = new File(tempDir,"lines.txt");
		try(LFLineWriter w = new LFLineWriter(file)) {
			assertTrue(!w.isAutoFlush(),"File output should not auto flush");
			for(int idx=0; idx < 1000; idx++ ) {
				w.writeLine("line "+idx);
			}
		}
		List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
		assertEquals(1000, lines.size());
		assertEquals("line 999", lines.get(999));
	}


	@Test
	public void testTeeOutputStream() throws IOException {
		ByteArrayOutputStream streams [] = {
				new ByteArrayOutputStream(),
				new ByteArrayOutputStream(),
				new ByteArrayOutputStream(),
				new ByteArrayOutputStream(),
		};

		TeeOutputStream  tos =	new TeeOutputStream(streams);

		PrintWriter      pw  =	new PrintWriter(new OutputStreamWriter(tos));
		for(int line =0; line < lineCount; line++) {
			pw.println(testLine);
		}
		pw.flush();
		pw.close();


		for (int streamIdx = 0; streamIdx < streams.length; streamIdx++) {			
			String res = streams[streamIdx].toString();
			res = res.replaceAll("\r", "");
			String [] lines = res.split("\n");
			assertEquals(lineCount,lines.length,"output "+streamIdx+" does not have the correct number of lines");
			for (int idx = 0; idx < lines.length; idx++) {
				assertEquals(testLine,lines[idx],"Line "+idx+" of output "+streamIdx+" does not have the correct value");
			}
		}
	}

	@Test
	public void testTeeOutputStreamOneStreamFails() throws IOException {
		class Failing extends OutputStream {
			boolean closed;
			@Override
			public void write(int b) throws IOException {
				throw new IOException("write failed");
			}
			@Override
			public void close() throws IOException {
				closed = true;
				throw new IOException("close failed");
			}
		}
		Failing bad = new Failing();
		ByteArrayOutputStream good = new ByteArrayOutputStream();
		File file = new File(tempDir,"tee.txt");
		FileOutputStream fos = new FileOutputStream(file);
		TeeOutputStream tee = new TeeOutputStream(bad, good, fos);

		IOException e = assertThrows(IOException.class, () -> tee.write("data".getBytes()));
		assertEquals("write failed", e.getMessage());
		assertEquals("data", good.toString(),"Other streams must still be written");

		assertThrows(IOException.class, tee::close);
		assertTrue(bad.closed);
		//  The FileOutputStream was closed (and flushed) even though an earlier stream failed.
		assertEquals("data", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		assertThrows(IOException.class, () -> fos.write(1),"FileOutputStream should be closed");
	}

	class TestMonitor implements  IStreamMonitor {
		String name;
		boolean debug = false;
		TestMonitor(String name) {
			this.name = name;
		}

		int updateCount = 0;
		int startCount = 0;
		int completeCount = 0;
		long total = 0;
		List<Long> updates = new ArrayList<>();

		@Override
		public void update(long total, long transfered) {
			updateCount++;
			updates.add(total);
			if(debug) System.out.println(name+" update total = "+total+" tx="+transfered);				
		}

		@Override
		public void start() {
			if(debug) System.out.println(name+" Starting");
			startCount++;
		}

		@Override
		public void complete(long total) {
			this.total = total;
			completeCount++;
			if(debug) System.out.println(name+" Complete ="+total);				
		}

	};

	@Test
	public void testMoniteredStreams() throws IOException {
		int expectedTotal=1024;
		long blockSize = 20;
		int expectedUpdates = (int) (expectedTotal/blockSize);
		Random r = new Random();

		StringBuilder buf = new StringBuilder();
		while(buf.length()<expectedTotal) {
			int c = (char)r.nextInt(127);
			while( c < 10) {
				c = (char)r.nextInt(127);
			}
			buf.append((char)c);
		}
		byte [] expected = buf.toString().getBytes(StandardCharsets.US_ASCII);

		ByteArrayOutputStream bo = new ByteArrayOutputStream();

		TestMonitor om = new TestMonitor("Write");
		MonitoredOutputStream mo = new MonitoredOutputStream(bo,blockSize, om);
		//  Write in odd sized chunks with non zero offsets
		for(int off=0; off < expected.length; off+=37) {
			mo.write(expected, off, Math.min(37, expected.length-off));
		}
		mo.close();
		mo.close();
		assertArrayEquals(expected, bo.toByteArray(),"Output data is wrong");
		assertEquals(1, om.startCount," Output did not call started once");
		assertEquals(expectedUpdates, om.updateCount,"Output Wrong number of updates in output");
		assertEquals(Long.valueOf(blockSize), om.updates.get(0));
		assertEquals(Long.valueOf(blockSize*expectedUpdates), om.updates.get(expectedUpdates-1));
		assertEquals(expectedTotal, om.total,"Output Wrong total in output");
		assertEquals(1, om.completeCount,"complete should only be called once");

		ByteArrayInputStream bi = new ByteArrayInputStream(expected);

		TestMonitor im = new TestMonitor("Read");
		MonitoredInputStream mi = new MonitoredInputStream(bi,blockSize, im);
		byte data [] = new byte[expected.length+100];
		int total = 0;
		int got;
		//  Read in odd sized chunks with a non zero offset
		while( (got = mi.read(data, 100+total, Math.min(33, data.length-100-total))) > 0 ) {
			total += got;
		}
		mi.close();

		assertEquals(expectedTotal, total);
		assertArrayEquals(expected, java.util.Arrays.copyOfRange(data, 100, data.length),"Input data is wrong");
		assertEquals(1, im.startCount,"Read Output did not call started once");
		assertEquals(expectedUpdates, im.updateCount,"Read Wrong number of updates in output");
		assertEquals(expectedTotal, im.total,"Read Wrong total in output");
		assertEquals(1, im.completeCount,"complete should only be called once");
	}

	@Test
	public void testMonitoredStreamsOffsets() throws IOException {
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		try(MonitoredOutputStream mo = new MonitoredOutputStream(bo, new TestMonitor("w"))) {
			mo.write("0123456789ABCDEF".getBytes(), 10, 6);
		}
		assertEquals("ABCDEF", bo.toString());

		try(MonitoredInputStream mi = new MonitoredInputStream(new ByteArrayInputStream("hello world".getBytes()), new TestMonitor("r"))) {
			byte [] b = new byte[20];
			assertEquals(5, mi.read(b, 10, 5));
			assertEquals("hello", new String(b, 10, 5, StandardCharsets.US_ASCII));
		}
	}

	@Test
	public void testTelnetStreams() throws IOException {

		// Telnet output (as described in RFC206) is restricted to characters <= 'z' (0x7A) 
		ByteArrayOutputStream bao1 =  new ByteArrayOutputStream();
		List<Byte> expected = new ArrayList<>();
		try(TelnetOutputStream tno = new TelnetOutputStream(bao1)) {
			for(int idx=0; idx < 1024; idx++ ) {
				tno.write(idx);
				//Note: This is the logic used by TelnetOutputStream
				int i = idx & 0x7F;
				if( i <= 'z') {
					expected.add((byte)i);
				}
			}
		}
		
		byte actualData [] = bao1.toByteArray();
		
		assertEquals(expected.size(), actualData.length,"TelnetOutputStream did not output the correct number of characters.");
		
		for(int idx=0; idx < actualData.length; idx++ ) {			
			assertEquals(expected.get(idx),actualData[idx],"TelnetOutputStream output does not match expected value.");
		}

		//  Lower case letters are transmitted, the bulk write gives the same result as single bytes
		ByteArrayOutputStream bao2 =  new ByteArrayOutputStream();
		try(TelnetOutputStream tno = new TelnetOutputStream(bao2)) {
			tno.write("xxHello World\r\n~".getBytes(StandardCharsets.US_ASCII), 2, 13);
		}
		assertEquals("Hello World\r\n", bao2.toString());

		//  close flushes and closes the underlying stream
		ByteArrayOutputStream bao3 =  new ByteArrayOutputStream();
		TelnetOutputStream tno = new TelnetOutputStream(new java.io.BufferedOutputStream(bao3));
		tno.write("ABC".getBytes());
		tno.close();
		assertEquals("ABC", bao3.toString());
		
		StringBuilder buf = new StringBuilder();
		
		for(int idx=0; idx < 2; idx++ ) {
			buf.append(testLine+"\r\n");
		}
		
		
		ByteArrayInputStream bai = new ByteArrayInputStream(buf.toString().getBytes());

		/*
		 * TelnetInputStream is just intended to echo data received from
		 * a remote system onto a display in the local system.
		 */
		StringBuilder buf2 = new StringBuilder();
		ByteArrayOutputStream bao =  new ByteArrayOutputStream();
		try(TelnetInputStream in = new TelnetInputStream(bai, bao)){
			int i = in.read();
			while( i>=0 ) {
				buf2.append((char)i);
				i = in.read();
			}			
		}

		String actualEcho = new String(bao.toByteArray());		
		assertEquals(buf.toString(),buf2.toString(),"TelnetInputStream input does not match expected value.");
		assertEquals(buf.toString(),actualEcho,"TelnetInputStream ECHO does not match expected value.");

		//  Bulk reads are echoed too
		ByteArrayOutputStream echo =  new ByteArrayOutputStream();
		try(TelnetInputStream in = new TelnetInputStream(new ByteArrayInputStream(buf.toString().getBytes()), echo)){
			byte [] b = in.readAllBytes();
			assertEquals(buf.toString(), new String(b));
		}
		assertEquals(buf.toString(), echo.toString());
	}

}
