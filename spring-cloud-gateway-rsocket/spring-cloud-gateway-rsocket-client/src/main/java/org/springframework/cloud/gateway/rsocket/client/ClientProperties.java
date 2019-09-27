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

package org.springframework.cloud.gateway.rsocket.client;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.cloud.gateway.rsocket.common.autoconfigure.Broker;
import org.springframework.cloud.gateway.rsocket.common.metadata.WellKnownKey;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("spring.cloud.gateway.rsocket.client")
@Validated
public class ClientProperties {

	@NotNull
	private BigInteger routeId;

	@NotEmpty
	private String serviceName;

	private Map<TagKey, String> tags = new LinkedHashMap<>();

	@Valid
	@NestedConfigurationProperty
	private Broker broker = new Broker();

	private Map<String, Map<TagKey, String>> forwarding = new LinkedHashMap<>();

	public BigInteger getRouteId() {
		return this.routeId;
	}

	public void setRouteId(BigInteger routeId) {
		this.routeId = routeId;
	}

	public String getServiceName() {
		return this.serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public Map<TagKey, String> getTags() {
		return tags;
	}

	public Broker getBroker() {
		return this.broker;
	}

	public void setBroker(Broker broker) {
		this.broker = broker;
	}

	public Map<String, Map<TagKey, String>> getForwarding() {
		return forwarding;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringCreator(this)
				.append("routeId", routeId)
				.append("serviceName", serviceName)
				.append("tags", tags)
				.append("broker", broker)
				.append("forwarding", forwarding)
				.toString();
		// @formatter:on
	}

	public static class TagKey {

		private WellKnownKey wellKnownKey;

		private String customKey;

		public TagKey() {
			System.out.println("here");
		}

		public TagKey(String text) {
			if (!StringUtils.isEmpty(text)) {
				try {
					wellKnownKey = WellKnownKey.valueOf(text.toUpperCase());
				}
				catch (IllegalArgumentException e) {
					// NOT a valid well know key
					customKey = text;
				}
			}
		}

		public static TagKey of(WellKnownKey key) {
			TagKey tagKey = new TagKey();
			tagKey.setWellKnownKey(key);
			return tagKey;
		}

		public static TagKey of(String key) {
			return new TagKey(key);
		}

		public WellKnownKey getWellKnownKey() {
			return wellKnownKey;
		}

		public void setWellKnownKey(WellKnownKey wellKnownKey) {
			this.wellKnownKey = wellKnownKey;
		}

		public String getCustomKey() {
			return customKey;
		}

		public void setCustomKey(String customKey) {
			this.customKey = customKey;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			TagKey tag = (TagKey) o;
			return wellKnownKey == tag.wellKnownKey
					&& Objects.equals(customKey, tag.customKey);
		}

		@Override
		public int hashCode() {
			return Objects.hash(wellKnownKey, customKey);
		}

		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner(", ", "[", "]");
			if (wellKnownKey != null) {
				joiner.add(wellKnownKey.name());
			}
			if (customKey != null) {
				joiner.add("'" + customKey + "'");
			}
			return joiner.toString();
		}

	}

}
