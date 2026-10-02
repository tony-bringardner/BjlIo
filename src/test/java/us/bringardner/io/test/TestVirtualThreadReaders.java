package us.bringardner.io.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import us.bringardner.io.CRLFLineReader;
import us.bringardner.io.LFLineReader;

/**
 * BJL-55: many virtual threads, each blocked in readLine() on its own socket, must not hold
 * all the carrier threads. With synchronized readLine, on Java 21-23 each one pinned a
 * carrier thread, so once there were as many waiting readers as CPUs no other virtual
 * thread could run: the lines below were never read. Needs Java 21+ (skipped before);
 * compiled for Java 11, so virtual threads are created by reflection.
 */
public class TestVirtualThreadReaders {

	private static final int READERS = Math.max(64, Runtime.getRuntime().availableProcessors() * 8);

	private static ExecutorService virtualThreadPerTask() throws Exception {
		return (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
	}

	@Test
	public void manyBlockedReadersDontStarveOtherVirtualThreads() throws Exception {
		Assumptions.assumeTrue(Runtime.version().feature() >= 21, "needs virtual threads (Java 21+)");
		List<Socket> serverSide = new ArrayList<>();
		List<Socket> clientSide = new ArrayList<>();
		ExecutorService exec = virtualThreadPerTask();
		try (ServerSocket listener = new ServerSocket(0, READERS, InetAddress.getLoopbackAddress())) {
			for (int i = 0; i < READERS; i++) {
				clientSide.add(new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort()));
				serverSide.add(listener.accept());
			}
			List<Future<String>> lines = new ArrayList<>();
			for (int i = 0; i < READERS; i++) {
				Socket s = serverSide.get(i);
				boolean crlf = i % 2 == 0;
				lines.add(exec.submit(() -> crlf
						? new CRLFLineReader(s.getInputStream()).readLine()
						: new LFLineReader(s.getInputStream()).readLine()));
			}
			// Let every reader block in readLine first
			Thread.sleep(500);

			// Another virtual thread must still get to run
			Future<String> other = exec.submit(() -> "ran");
			assertEquals("ran", other.get(5, TimeUnit.SECONDS), "a virtual thread could not run: carriers pinned");

			for (int i = 0; i < READERS; i++) {
				OutputStream out = clientSide.get(i).getOutputStream();
				out.write(("line " + i + (i % 2 == 0 ? "\r\n" : "\n")).getBytes(StandardCharsets.UTF_8));
				out.flush();
			}
			for (int i = 0; i < READERS; i++) {
				assertEquals("line " + i, lines.get(i).get(10, TimeUnit.SECONDS));
			}
		} finally {
			for (Socket s : clientSide) {
				s.close();
			}
			for (Socket s : serverSide) {
				s.close();
			}
			exec.shutdownNow();
			assertTrue(exec.awaitTermination(5, TimeUnit.SECONDS));
		}
	}
}
