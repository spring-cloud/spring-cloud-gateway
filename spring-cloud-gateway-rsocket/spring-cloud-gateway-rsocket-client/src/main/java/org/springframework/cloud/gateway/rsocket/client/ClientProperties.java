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

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.cloud.gateway.rsocket.common.autoconfigure.Broker;
import org.springframework.cloud.gateway.rsocket.common.metadata.WellKnownKey;
import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("spring.cloud.gateway.rsocket.client")
@Validated
public class ClientProperties {

	@NotNull
	private BigInteger routeId;

	@NotEmpty
	private String serviceName;

	private Map<WellKnownKey, String> tags = new LinkedHashMap<>();

	private Map<String, String> customTags = new LinkedHashMap<>();

	@Valid
	@NestedConfigurationProperty
	private Broker broker = new Broker();

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

	public Map<WellKnownKey, String> getTags() {
		return this.tags;
	}

	public void setTags(Map<WellKnownKey, String> tags) {
		this.tags = tags;
	}

	public Map<String, String> getCustomTags() {
		return this.customTags;
	}

	public void setCustomTags(Map<String, String> customTags) {
		this.customTags = customTags;
	}

	public Broker getBroker() {
		return this.broker;
	}

	public void setBroker(Broker broker) {
		this.broker = broker;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringCreator(this)
				.append("routeId", routeId)
				.append("serviceName", serviceName)
				.append("tags", tags)
				.append("customTags", customTags)
				.append("broker", broker)
				.toString();
		// @formatter:on
	}

}
