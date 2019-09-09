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
import java.util.Objects;

import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;

public final class RouteRemove {

	private final BigInteger brokerId;

	private final BigInteger routeId;

	private final long timestamp;

	public RouteRemove(BigInteger brokerId, BigInteger routeId, long timestamp) {
		this.brokerId = brokerId;
		this.routeId = routeId;
		this.timestamp = timestamp;
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

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		RouteRemove routeJoin = (RouteRemove) o;
		return this.timestamp == routeJoin.timestamp
				&& Objects.equals(this.brokerId, routeJoin.brokerId)
				&& Objects.equals(this.routeId, routeJoin.routeId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.brokerId, this.routeId, this.timestamp);
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("brokerId", brokerId)
				.append("routeId", routeId).append("timestamp", timestamp).toString();

	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private BigInteger brokerId;

		private BigInteger routeId;

		private long timestamp = System.currentTimeMillis();

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

		public RouteRemove build() {
			Assert.notNull(brokerId, "brokerId may not be null");
			Assert.notNull(routeId, "brokerId may not be null");
			Assert.isTrue(timestamp > 0, "timestamp must be > 0");
			return new RouteRemove(brokerId, routeId, timestamp);
		}

	}

}
