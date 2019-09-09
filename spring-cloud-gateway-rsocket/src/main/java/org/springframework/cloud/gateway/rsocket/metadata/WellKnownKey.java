/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.rsocket.metadata;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum WellKnownKey {

	// CHECKSTYLE:OFF
	// @formatter:off
	UNPARSEABLE_KEY("UNPARSEABLE_KEY_DO_NOT_USE", (byte) -2),
	UNKNOWN_RESERVED_KEY("UNKNOWN_YET_RESERVED_DO_NOT_USE", (byte) -1),

	NO_TAG("NO_TAG_DO_NOT_USE", (byte) 0x00),
	SERVICE_NAME("io.rsocket.routing.ServiceName", (byte) 0x01),
	ROUTE_ID("io.rsocket.routing.RouteId", (byte) 0x02),
	INSTANCE_NAME("io.rsocket.routing.InstanceName", (byte) 0x03),
	CLUSTER_NAME("io.rsocket.routing.ClusterName", (byte) 0x04),
	PROVIDER("io.rsocket.routing.Provider", (byte) 0x05),
	REGION("io.rsocket.routing.Region", (byte) 0x06),
	ZONE("io.rsocket.routing.Zone", (byte) 0x07),
	DEVICE("io.rsocket.routing.Device", (byte) 0x08),
	OS("io.rsocket.routing.OS", (byte) 0x09),
	USER_NAME("io.rsocket.routing.UserName", (byte) 0x0A),
	USER_ID("io.rsocket.routing.UserId", (byte) 0x0B),
	MAJOR_VERSION("io.rsocket.routing.MajorVersion", (byte) 0x0C),
	MINOR_VERSION("io.rsocket.routing.MinorVersion", (byte) 0x0D),
	PATCH_VERSION("io.rsocket.routing.PatchVersion", (byte) 0x0E),
	VERSION("io.rsocket.routing.Version", (byte) 0x0F),
	ENVIRONMENT("io.rsocket.routing.Environment", (byte) 0x10),
	TESTC_ELL("io.rsocket.routing.TestCell", (byte) 0x11),
	DNS("io.rsocket.routing.DNS", (byte) 0x12),
	IPV4("io.rsocket.routing.IPv4", (byte) 0x13),
	IPV6("io.rsocket.routing.IPv6", (byte) 0x14),
	COUNTRY("io.rsocket.routing.Country", (byte) 0x15),
	TIME_ZONE("io.rsocket.routing.TimeZone", (byte) 0x1A),
	SHARD_KEY("io.rsocket.routing.ShardKey", (byte) 0x1B),
	SHARD_METHOD("io.rsocket.routing.ShardMethod", (byte) 0x1C),
	STICKY_ROUTE_KEY("io.rsocket.routing.StickyRouteKey", (byte) 0x1D),
	LB_METHOD("io.rsocket.routing.LBMethod", (byte) 0x1E),
	BROKER_EXTENSION("Broker Implementation Extension Key", (byte) 0x1E),
	WELL_KNOWN_EXTENSION("Well Known Extension Key", (byte) 0x1E);
	// @formatter:on
	// CHECKSTYLE:ON

	static final WellKnownKey[] TYPES_BY_ID;
	static final Map<String, WellKnownKey> TYPES_BY_STRING;

	static {
		// precompute an array of all valid mime ids,
		// filling the blanks with the RESERVED enum
		TYPES_BY_ID = new WellKnownKey[128]; // 0-127 inclusive
		Arrays.fill(TYPES_BY_ID, UNKNOWN_RESERVED_KEY);
		// also prepare a Map of the types by key string
		TYPES_BY_STRING = new HashMap<>(128);

		for (WellKnownKey value : values()) {
			if (value.getIdentifier() >= 0) {
				TYPES_BY_ID[value.getIdentifier()] = value;
				TYPES_BY_STRING.put(value.getString(), value);
			}
		}
	}

	private final byte identifier;

	private final String str;

	WellKnownKey(String str, byte identifier) {
		this.str = str;
		this.identifier = identifier;
	}

	public static WellKnownKey fromIdentifier(int id) {
		if (id < 0x00 || id > 0x7F) {
			return UNPARSEABLE_KEY;
		}
		return TYPES_BY_ID[id];
	}

	public static WellKnownKey fromString(String mimeType) {
		if (mimeType == null) {
			throw new IllegalArgumentException("type must be non-null");
		}

		// force UNPARSEABLE if by chance UNKNOWN_RESERVED_MIME_TYPE's text has been used
		if (mimeType.equals(UNKNOWN_RESERVED_KEY.str)) {
			return UNPARSEABLE_KEY;
		}

		return TYPES_BY_STRING.getOrDefault(mimeType, UNPARSEABLE_KEY);
	}

	/**
	 * @return the byte identifier of the mime type, guaranteed to be positive or zero.
	 */
	public byte getIdentifier() {
		return identifier;
	}

	/**
	 * @return the mime type represented as a {@link String}, which is made of US_ASCII
	 * compatible characters only
	 */
	public String getString() {
		return str;
	}

	/** @see #getString() */
	@Override
	public String toString() {
		return str;
	}

}
