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

public final class BrokerInfo {

	private final BigInteger brokerId;

	private final long timestamp;

	private final Map<Key, String> tags;

	private BrokerInfo(BigInteger brokerId, long timestamp, Map<Key, String> tags) {
		this.brokerId = brokerId;
		this.timestamp = timestamp;
		this.tags = tags;
	}

	public BigInteger getBrokerId() {
		return this.brokerId;
	}

	public long getTimestamp() {
		return this.timestamp;
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
		BrokerInfo that = (BrokerInfo) o;
		return this.timestamp == that.timestamp
				&& Objects.equals(this.brokerId, that.brokerId)
				&& Objects.equals(this.tags, that.tags);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.brokerId, this.timestamp, this.tags);
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringCreator(this)
				.append("id", brokerId)
				.append("timestamp", timestamp)
				.append("tags", getTags())
				.toString();
		// @formatter:on
	}

	public static Builder of(BigInteger brokerId) {
		return new Builder(brokerId);
	}

	public static Builder of(Long brokerId) {
		return of(BigInteger.valueOf(brokerId));
	}

	public static final class Builder {

		private final BigInteger brokerId;

		private long timestamp = System.currentTimeMillis();

		private final TagsMetadata.Builder tagsBuilder = TagsMetadata.builder();

		private Builder(BigInteger brokerId) {
			Assert.notNull(brokerId, "brokerId may not be null");
			this.brokerId = brokerId;
		}

		public Builder timestamp(long timestamp) {
			this.timestamp = timestamp;
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

		public BrokerInfo build() {
			Assert.isTrue(timestamp > 0, "timestamp must be > 0");
			return new BrokerInfo(brokerId, timestamp, tagsBuilder.build().getTags());
		}

	}

}
