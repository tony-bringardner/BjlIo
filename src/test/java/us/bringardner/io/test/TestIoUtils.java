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
 */
package us.bringardner.io.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import us.bringardner.io.IoUtils;

public class TestIoUtils {

	@Test
	public void testCloseQuietly() {
		AtomicInteger closed = new AtomicInteger();
		AutoCloseable ok = closed::incrementAndGet;
		AutoCloseable failsIo = () -> { closed.incrementAndGet(); throw new IOException("close failed"); };
		AutoCloseable failsRuntime = () -> { closed.incrementAndGet(); throw new IllegalStateException("close failed"); };

		IoUtils.closeQuietly(ok);
		IoUtils.closeQuietly(failsIo);
		IoUtils.closeQuietly(failsRuntime);
		IoUtils.closeQuietly((AutoCloseable) null);
		assertEquals(3, closed.get(), "Each one is closed and nothing is thrown");

		closed.set(0);
		IoUtils.closeQuietly(failsIo, null, ok, failsRuntime, ok);
		assertEquals(4, closed.get(), "A failure doesn't stop the rest being closed");
		IoUtils.closeQuietly((AutoCloseable[]) null);
	}
}
