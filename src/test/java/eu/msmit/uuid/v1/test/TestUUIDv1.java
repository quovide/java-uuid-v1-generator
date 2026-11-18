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
package eu.msmit.uuid.v1.test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import junit.framework.TestCase;

import org.junit.Test;

import eu.msmit.uuid.v1.DefaultGenerator;
import eu.msmit.uuid.v1.Generator;
import eu.msmit.uuid.v1.ParallelGenerator;
import eu.msmit.uuid.v1.UUIDv1;

/**
 * @author Marijn Smit (info@msmit.eu)
 * @since Nov 18, 2026
 */
public class TestUUIDv1 extends TestCase {

	@Test
	public void testDefaultGenerator() throws Exception {
		assertTrue(UUIDv1.getGenerator() instanceof ParallelGenerator);
		assertTrue(UUIDv1.createGenerator(null) instanceof ParallelGenerator);
		assertTrue(UUIDv1.createGenerator(" ") instanceof ParallelGenerator);
		assertEquals(1, UUIDv1.next().version());
	}

	@Test
	public void testConfiguredGenerator() throws Exception {
		Generator generator = UUIDv1.createGenerator(DefaultGenerator.class
				.getName());
		assertEquals(DefaultGenerator.class, generator.getClass());
	}

	@Test
	public void testInvalidGenerator() throws Exception {
		String[] invalid = new String[] { "does.not.Exist",
				String.class.getName() };

		for (String className : invalid) {
			try {
				UUIDv1.createGenerator(className);
				fail("Expected an IllegalStateException for " + className);
			} catch (IllegalStateException e) {
				// expected
			}
		}
	}

	@Test
	public void testParallelGeneratorUsesUniqueNodes() throws Exception {
		int concurrency = 16;
		Generator generator = new ParallelGenerator(concurrency);
		Set<Long> nodes = new HashSet<Long>();

		for (int i = 0; i < concurrency; i++) {
			nodes.add(generator.next().node());
		}

		assertEquals(concurrency, nodes.size());
	}

	@Test
	public void testInterruptedThreadKeepsOrder() throws Exception {
		DefaultGenerator generator = new DefaultGenerator();
		Thread.currentThread().interrupt();

		try {
			long now = (System.currentTimeMillis() + 0xB1D069B5400L) * 10000L;
			UUID prev = generator.next();

			for (int i = 0; i < 50000; i++) {
				UUID next = generator.next();
				assertTrue(next.timestamp() > prev.timestamp());
				assertEquals(prev.clockSequence(), next.clockSequence());
				prev = next;
			}

			// Timestamps are never issued ahead of the clock
			long after = (System.currentTimeMillis() + 0xB1D069B5400L) * 10000L;
			assertTrue(prev.timestamp() >= now - 10000L);
			assertTrue(prev.timestamp() <= after);
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}
}
