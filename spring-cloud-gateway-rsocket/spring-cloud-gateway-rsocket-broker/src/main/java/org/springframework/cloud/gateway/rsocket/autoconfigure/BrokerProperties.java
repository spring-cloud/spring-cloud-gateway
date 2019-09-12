/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.autoconfigure;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.rsocket.common.autoconfigure.Broker;
import org.springframework.core.style.ToStringCreator;

@ConfigurationProperties("spring.cloud.gateway.rsocket")
public class BrokerProperties {

	/**
	 * Enable Gateway RSocket.
	 */
	private boolean enabled = true;

	private String id = "gateway"; // TODO: + UUID?

	private BigInteger routeId;

	private String serviceName = "gateway";

	private List<Broker> brokers = new ArrayList<>();

	/**
	 * Tag names and values to be supplied to Micrometer Interceptor.
	 */
	private List<String> micrometerTags = new ArrayList<>();

	public BrokerProperties() {
		micrometerTags.add("component");
		micrometerTags.add("gateway");
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<String> getMicrometerTags() {
		return micrometerTags;
	}

	public void setMicrometerTags(List<String> micrometerTags) {
		this.micrometerTags = micrometerTags;
	}

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

	public List<Broker> getBrokers() {
		return this.brokers;
	}

	public void setBrokers(List<Broker> brokers) {
		this.brokers = brokers;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringCreator(this)
				.append("enabled", enabled)
				.append("id", id)
				.append("micrometerTags", micrometerTags)
				.append("routeId", routeId)
				.append("serviceName", serviceName)
				.append("brokers", brokers)
				.toString();
		// @formatter:on
	}

}
