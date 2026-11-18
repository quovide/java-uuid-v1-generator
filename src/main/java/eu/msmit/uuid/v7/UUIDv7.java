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

import java.util.UUID;

import eu.msmit.uuid.v1.Generator;

/**
 * Entry point for version 7 (Unix time ordered) UUIDs, as described in RFC
 * 9562. The UUIDs sort in order of creation, which makes them suitable as
 * database keys.
 *
 * <pre>
 * UUID id = UUIDv7.next();
 * long createdAt = UUIDv7.timestamp(id);
 * </pre>
 *
 * @author Marijn Smit (info@msmit.eu)
 * @since Nov 18, 2026
 * @see UUIDv7Generator
 */
public final class UUIDv7 {
	private static final Generator GENERATOR = new UUIDv7Generator();

	private UUIDv7() {
	}

	/**
	 * @return the system generator
	 */
	public static Generator getGenerator() {
		return GENERATOR;
	}

	/**
	 * @return the next {@link UUID} from the system generator, which is
	 *         greater than all the UUIDs it returned before
	 */
	public static UUID next() {
		return GENERATOR.next();
	}

	/**
	 * @return true when the given UUID is a version 7 UUID
	 */
	public static boolean isV7(UUID uuid) {
		return uuid != null && uuid.version() == 7 && uuid.variant() == 2;
	}

	/**
	 * @return the creation time of the given UUID, in milliseconds since
	 *         january 1st 1970 UTC
	 * @throws IllegalArgumentException
	 *             when the UUID is not a version 7 UUID
	 */
	public static long timestamp(UUID uuid) {
		if (!isV7(uuid)) {
			throw new IllegalArgumentException("Not a version 7 UUID: " + uuid);
		}

		return uuid.getMostSignificantBits() >>> 16;
	}
}
