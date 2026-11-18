/**
 * Copyright 2015 Marijn Smit (info@msmit.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.msmit.uuid.v7.test;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import junit.framework.TestCase;

import org.junit.Test;

import eu.msmit.uuid.v7.UUIDv7;
import eu.msmit.uuid.v7.UUIDv7Generator;

/**
 * @author Marijn Smit (info@msmit.eu)
 * @since Nov 18, 2026
 */
public class TestUUIDv7 extends TestCase {

	/** Generator with a clock controlled by the test */
	private static class FixedClockGenerator extends UUIDv7Generator {
		long now_;

		FixedClockGenerator(long now) {
			now_ = now;
		}

		FixedClockGenerator(long now, SecureRandom random) {
			super(random);
			now_ = now;
		}

		@Override
		protected long currentTimeMs() {
			return now_;
		}

		@Override
		public UUID createUUID(long timestamp, long randA, long randB) {
			return super.createUUID(timestamp, randA, randB);
		}
	}

	/** Random which only returns one bits, to force overflows */
	private static class OnesRandom extends SecureRandom {
		private static final long serialVersionUID = 1L;

		@Override
		public int nextInt() {
			return -1;
		}

		@Override
		public long nextLong() {
			return -1L;
		}
	}

	/** Compare as unsigned 128 bits, like the text and byte order do */
	private static int compare(UUID a, UUID b) {
		int msb = Long.compareUnsigned(a.getMostSignificantBits(),
				b.getMostSignificantBits());
		return msb != 0 ? msb : Long.compareUnsigned(
				a.getLeastSignificantBits(), b.getLeastSignificantBits());
	}

	@Test
	public void testLayout() throws Exception {
		FixedClockGenerator gen = new FixedClockGenerator(0);

		// Test vector of RFC 9562, appendix A.6
		UUID uuid = gen.createUUID(0x017F22E279B0L, 0xCC3L,
				0x18C4DC0C0C07398FL);
		assertEquals("017f22e2-79b0-7cc3-98c4-dc0c0c07398f", uuid.toString());

		uuid = gen.createUUID(0, 0, 0);
		assertEquals("00000000-0000-7000-8000-000000000000", uuid.toString());

		uuid = gen.createUUID(-1L, -1L, -1L);
		assertEquals("ffffffff-ffff-7fff-bfff-ffffffffffff", uuid.toString());
	}

	@Test
	public void testNext() throws Exception {
		long before = System.currentTimeMillis();
		UUID uuid = UUIDv7.next();
		long after = System.currentTimeMillis();

		assertEquals(7, uuid.version());
		assertEquals(2, uuid.variant());
		assertTrue(UUIDv7.isV7(uuid));
		assertTrue(UUIDv7.timestamp(uuid) >= before);
		assertTrue(UUIDv7.timestamp(uuid) <= after);
	}

	@Test
	public void testTimestampRejectsOtherVersions() throws Exception {
		assertFalse(UUIDv7.isV7(null));
		assertFalse(UUIDv7.isV7(UUID.randomUUID()));

		try {
			UUIDv7.timestamp(UUID.randomUUID());
			fail("Expected an IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	@Test
	public void testMonotonicWithinSameMillisecond() throws Exception {
		FixedClockGenerator gen = new FixedClockGenerator(1000);
		UUID prev = gen.next();

		for (int i = 0; i < 100000; i++) {
			UUID next = gen.next();
			assertTrue(prev + " < " + next, compare(prev, next) < 0);
			assertEquals(1000, UUIDv7.timestamp(next));
			prev = next;
		}
	}

	@Test
	public void testMonotonicWhenClockMovesBackwards() throws Exception {
		FixedClockGenerator gen = new FixedClockGenerator(100000);
		UUID prev = gen.next();

		gen.now_ = 99000;
		UUID next = gen.next();

		assertTrue(compare(prev, next) < 0);
		assertEquals(100000, UUIDv7.timestamp(next));

		// The clock catches up
		gen.now_ = 100001;
		prev = next;
		next = gen.next();

		assertTrue(compare(prev, next) < 0);
		assertEquals(100001, UUIDv7.timestamp(next));
	}

	@Test
	public void testClockResetIsAccepted() throws Exception {
		FixedClockGenerator gen = new FixedClockGenerator(100000);
		gen.next();

		gen.now_ = 50000;
		assertEquals(50000, UUIDv7.timestamp(gen.next()));
	}

	@Test
	public void testOverflowBorrowsNextMillisecond() throws Exception {
		FixedClockGenerator gen = new FixedClockGenerator(1000,
				new OnesRandom());

		UUID first = gen.next();
		UUID second = gen.next();

		assertEquals(1000, UUIDv7.timestamp(first));
		assertEquals(1001, UUIDv7.timestamp(second));
		assertTrue(compare(first, second) < 0);
	}

	@Test
	public void testInvalidClock() throws Exception {
		try {
			new FixedClockGenerator(-1).next();
			fail("Expected an IllegalStateException");
		} catch (IllegalStateException e) {
			// expected
		}

		try {
			new FixedClockGenerator(1L << 48).next();
			fail("Expected an IllegalStateException");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	@Test
	public void testConcurrentUnique() throws Exception {
		final int threads = 8;
		final int amount = 50000;
		final Set<UUID> uniqCheck = Collections
				.synchronizedSet(new HashSet<UUID>());

		Thread[] pool = new Thread[threads];
		for (int t = 0; t < threads; t++) {
			pool[t] = new Thread() {
				@Override
				public void run() {
					UUID prev = UUIDv7.next();
					uniqCheck.add(prev);

					for (int i = 1; i < amount; i++) {
						UUID next = UUIDv7.next();
						if (compare(prev, next) >= 0) {
							throw new IllegalStateException(prev + " >= "
									+ next);
						}
						uniqCheck.add(next);
						prev = next;
					}
				}
			};
			pool[t].start();
		}

		for (Thread thread : pool) {
			thread.join();
		}

		assertEquals(threads * amount, uniqCheck.size());
	}
}
