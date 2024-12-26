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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

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


	@Test
	void testContinuosInputStream() throws FileNotFoundException, IOException {
		/**
		 * 
		 */
		File dir = new File("target").getCanonicalFile();
		assertTrue(dir.exists(),"Test dir does not exist");
		File file = new File(dir,"ContinuousIOTestFile.txt").getCanonicalFile();
		BaseThread thread = new BaseThread() {

			@Override
			public void run() {
				try {
					PrintStream out = new PrintStream(file);
					//  add data to the file every second
					started = running = true;
					int cnt = 0;
					while(!stopping) {						
						out.println("line "+(cnt++)+". ");
						Thread.sleep(100);
					}

					out.close();

				} catch (Throwable e) {
				}
				running = false;
			}

		};
		thread.start();
		try {
			// give the thread a little time to create the file
			Thread.sleep(100);
		} catch (InterruptedException e) {
			// Not implemented
			e.printStackTrace();
		}
		ContinuosInputStream in = new ContinuosInputStream(file,false);
		String line = null;
		int cnt = 0;
		try {
			while( ( line=in.readLine()) != null ) {
				//System.out.println(String.format("%d = %s", cnt,line));
				assertEquals("line "+(cnt++)+". ", line,"");
				if(cnt > 6) {
					thread.stop();
					in.close();
					break;
				}
			}
		}catch (IOException e) {
			assertEquals(6, cnt,"ContinuosInputStream did not make all it's turns. ");
		}

		in.close();
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
			String [] lines = res.split("\n");
			assertEquals(lineCount,lines.length,"output "+streamIdx+" does not have the correct number of lines");
			for (int idx = 0; idx < lines.length; idx++) {
				assertEquals(testLine,lines[idx],"Line "+idx+" of output "+streamIdx+" does not have the correct value");
			}
		}
	}

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

		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		boolean debug = false;
		class TestMonitor implements  IStreamMonitor {
			String name;
			TestMonitor(String name) {
				this.name = name;
			}

			int updateCount = 0;
			long total = 0;
			boolean hasStarted= false;

			@Override
			public void update(long total, long transfered) {
				updateCount++;
				if(debug) System.out.println(name+" update total = "+total+" tx="+transfered);				
			}

			@Override
			public void start() {
				if(debug) System.out.println(name+" Starting");
				hasStarted = true;
			}

			@Override
			public void complete(long total) {
				this.total = total;
				if(debug) System.out.println(name+" Complete ="+total);				
			}

		};

		TestMonitor om = new TestMonitor("Write");
		MonitoredOutputStream mo = new MonitoredOutputStream(bo,blockSize, om);
		mo.write(buf.toString().getBytes());		
		mo.close();
		assertTrue(om.hasStarted," Output did not call started");
		assertEquals(om.updateCount, expectedUpdates,"Output Wrong number of updates in output");
		assertEquals(om.total, expectedTotal,"Output Wrong total in output");

		ByteArrayInputStream bi = new ByteArrayInputStream(buf.toString().getBytes());

		TestMonitor im = new TestMonitor("Read");
		MonitoredInputStream mi = new MonitoredInputStream(bi,blockSize, im);
		byte data [] = new byte[buf.length()];
		int got = mi.read(data);
		while( got >= 0) {
			got = mi.read(data,got,data.length-got);
			if( got < 0 ) {
				mi.close();
			}
		}

		assertTrue(im.hasStarted,"Read Output did not call started");
		assertEquals(im.updateCount, expectedUpdates,"Read Wrong number of updates in output");
		assertEquals(im.total, expectedTotal,"Read Wrong total in output");

	}

	@Test
	public void testTelnetStreams() throws IOException {

		// Telnet output (as described in RFC206) is restricted to characters <= 'Z' (0x7A) 
		ByteArrayOutputStream bao1 =  new ByteArrayOutputStream();
		List<Byte> expected = new ArrayList<>();
		try(TelnetOutputStream tno = new TelnetOutputStream(bao1)) {
			for(int idx=0; idx < 1024; idx++ ) {
				tno.write(idx);
				//Note: This is the logic used by TelnetOutputStream
				int i = idx & 0b1111111;
				if( i <= 'Z') {
					expected.add((byte)i);
				}
			}
		}
		
		byte actualData [] = bao1.toByteArray();
		
		assertEquals(expected.size(), actualData.length,"TelnetOutputStream did not output the correct number of characters.");
		
		for(int idx=0; idx < actualData.length; idx++ ) {			
			assertEquals(expected.get(idx),actualData[idx],"TelnetOutputStream output does not match expected value.");
		}
		
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
	}

}
