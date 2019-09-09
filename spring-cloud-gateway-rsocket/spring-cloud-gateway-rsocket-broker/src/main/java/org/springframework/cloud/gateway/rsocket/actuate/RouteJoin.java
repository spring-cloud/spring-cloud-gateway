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

package org.springframework.cloud.gateway.rsocket.actuate;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata;
import org.springframework.cloud.gateway.rsocket.common.metadata.TagsMetadata.Key;
import org.springframework.cloud.gateway.rsocket.common.metadata.WellKnownKey;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;

public final class RouteJoin {

	private final BigInteger brokerId;

	private final BigInteger routeId;

	private final long timestamp;

	private final String serviceName;

	private final Map<Key, String> tags;

	public RouteJoin(BigInteger brokerId, BigInteger routeId, long timestamp,
			String serviceName, Map<Key, String> tags) {
		this.brokerId = brokerId;
		this.routeId = routeId;
		this.timestamp = timestamp;
		this.serviceName = serviceName;
		this.tags = tags;
	}

	public BigInteger getBrokerId() {
		return this.brokerId;
	}

	public BigInteger getRouteId() {
		return this.routeId;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public String getServiceName() {
		return this.serviceName;
	}

	public Map<Key, String> getTags() {
		return this.tags;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		RouteJoin routeJoin = (RouteJoin) o;
		return this.timestamp == routeJoin.timestamp
				&& Objects.equals(this.brokerId, routeJoin.brokerId)
				&& Objects.equals(this.routeId, routeJoin.routeId)
				&& Objects.equals(this.serviceName, routeJoin.serviceName)
				&& Objects.equals(this.tags, routeJoin.tags);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.brokerId, this.routeId, this.timestamp, this.serviceName,
				this.tags);
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("brokerId", brokerId)
				.append("routeId", routeId).append("timestamp", timestamp)
				.append("serviceName", serviceName).append("tags", tags).toString();

	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private BigInteger brokerId;

		private BigInteger routeId;

		private long timestamp = System.currentTimeMillis();

		private String serviceName;

		private TagsMetadata.Builder tagsBuilder = TagsMetadata.builder();

		public Builder brokerId(BigInteger brokerId) {
			this.brokerId = brokerId;
			return this;
		}

		public Builder brokerId(long brokerId) {
			return brokerId(BigInteger.valueOf(brokerId));
		}

		public Builder routeId(BigInteger routeId) {
			this.routeId = routeId;
			return this;
		}

		public Builder routeId(long routeId) {
			return routeId(BigInteger.valueOf(routeId));
		}

		public Builder timestamp(long timestamp) {
			this.timestamp = timestamp;
			return this;
		}

		public Builder serviceName(String serviceName) {
			this.serviceName = serviceName;
			return this;
		}

		public Builder with(String key, String value) {
			tagsBuilder.with(key, value);
			return this;
		}

		public Builder with(WellKnownKey key, String value) {
			tagsBuilder.with(key, value);
			return this;
		}

		public Builder with(Key key, String value) {
			tagsBuilder.with(key, value);
			return this;
		}

		public Builder with(TagsMetadata tagsMetadata) {
			tagsBuilder.with(tagsMetadata);
			return this;
		}

		public RouteJoin build() {
			Assert.notNull(brokerId, "brokerId may not be null");
			Assert.notNull(routeId, "brokerId may not be null");
			Assert.notNull(serviceName, "brokerId may not be null");
			Assert.isTrue(timestamp > 0, "timestamp must be > 0");
			return new RouteJoin(brokerId, routeId, timestamp, serviceName,
					tagsBuilder.build().getTags());
		}

	}

}
