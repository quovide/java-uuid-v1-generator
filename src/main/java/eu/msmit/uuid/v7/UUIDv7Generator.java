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
package eu.msmit.uuid.v7;

import java.security.SecureRandom;
import java.util.UUID;

import eu.msmit.uuid.v1.Generator;

/**
 * Generates version 7 {@link UUID}s as described in RFC 9562, section 5.7.
 *
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           unix_ts_ms                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          unix_ts_ms           |  ver  |       rand_a          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |var|                        rand_b                             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                            rand_b                             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * Every UUID of a generator instance is strictly greater than the previous
 * one. Monotonicity within a millisecond follows method 2 of section 6.2
 * ("Monotonic Random"): the 74 random bits are treated as one counter, which
 * is seeded with fresh random data on every new millisecond and incremented
 * with a random amount for every next UUID in the same millisecond.
 *
 * When the system clock moves backwards, the generator keeps using its last
 * timestamp, so the order is preserved. Only when the clock moves back more
 * than {@link #MAX_ROLLBACK_MS} the new time is accepted.
 *
 * Note that a UUID is an identifier, not a secret: it exposes its creation
 * time. Do not use it as a session token or password reset key.
 *
 * @author Marijn Smit (info@msmit.eu)
 * @since Nov 18, 2026
 */
public class UUIDv7Generator implements Generator {

	/**
	 * The timestamp is 48 bits
	 */
	private static final long MAX_TIMESTAMP = 0xFFFFFFFFFFFFL;

	/**
	 * The rand_a field is 12 bits
	 */
	private static final long MAX_RAND_A = 0xFFFL;

	/**
	 * The rand_b field is 62 bits
	 */
	private static final long MAX_RAND_B = 0x3FFFFFFFFFFFFFFFL;

	/**
	 * When the clock moves back more than this amount of ms, it is considered
	 * to be a clock reset instead of time skew, and the new time is accepted.
	 */
	private static final long MAX_ROLLBACK_MS = 10000L;

	private final SecureRandom random_;

	private long timestamp_ = -1;
	private long randA_;
	private long randB_;

	public UUIDv7Generator() {
		this(new SecureRandom());
	}

	public UUIDv7Generator(SecureRandom random) {
		if (random == null) {
			throw new IllegalArgumentException("random is required");
		}
		random_ = random;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see eu.msmit.uuid.v1.Generator#next()
	 */
	public UUID next() {
		synchronized (this) {
			long now = currentTimeMs();

			if (now < 0 || now > MAX_TIMESTAMP) {
				throw new IllegalStateException(
						"System time does not fit in 48 bits: " + now);
			}

			if (now > timestamp_ || now < timestamp_ - MAX_ROLLBACK_MS) {
				timestamp_ = now;
				reseed();
			} else {
				increment();
			}

			return createUUID(timestamp_, randA_, randB_);
		}
	}

	/**
	 * Fill the 74 random bits with fresh random data.
	 */
	private void reseed() {
		randA_ = random_.nextInt() & MAX_RAND_A;
		randB_ = random_.nextLong() & MAX_RAND_B;
	}

	/**
	 * Increment the 74 random bits with a random amount between 1 and 2^32,
	 * so the next value is greater, but can not be guessed. When the bits
	 * overflow, time is borrowed from the next millisecond.
	 */
	private void increment() {
		long step = (random_.nextInt() & 0xFFFFFFFFL) + 1;

		randB_ += step;

		if (randB_ > MAX_RAND_B) {
			randB_ &= MAX_RAND_B;
			randA_++;
		}

		if (randA_ > MAX_RAND_A) {
			if (timestamp_ >= MAX_TIMESTAMP) {
				throw new IllegalStateException("Timestamp overflow");
			}
			timestamp_++;
			reseed();
		}
	}

	/**
	 * Create a new UUID from the given (valid) components.
	 */
	protected UUID createUUID(long timestamp, long randA, long randB) {
		long msb = (timestamp & MAX_TIMESTAMP) << 16;
		msb |= 0x7000L;
		msb |= randA & MAX_RAND_A;

		long lsb = 0x8000000000000000L;
		lsb |= randB & MAX_RAND_B;

		return new UUID(msb, lsb);
	}

	/** Override for testing **/
	protected long currentTimeMs() {
		return System.currentTimeMillis();
	}
}
