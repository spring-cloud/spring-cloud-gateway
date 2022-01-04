/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.config;

import java.util.HashMap;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.annotation.Validated;

/**
 * @author Ingyu Hwang
 */
@ConfigurationProperties("spring.cloud.gateway.metrics")
@Validated
public class GatewayMetricsProperties {

	/**
	 * Default metrics prefix.
	 */
	public static final String DEFAULT_PREFIX = "spring.cloud.gateway";

	/**
	 * Enables the collection of metrics data.
	 */
	private boolean enabled;

	/**
	 * The prefix of all metrics emitted by gateway.
	 */
	private String prefix = DEFAULT_PREFIX;

	/**
	 * Tags map that added to metrics.
	 */
	@NotNull
	private Map<String, String> tags = new HashMap<>();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public Map<String, String> getTags() {
		return tags;
	}

	public void setTags(Map<String, String> tags) {
		this.tags = tags;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("enabled", enabled).append("prefix", prefix).append("tags", tags)
				.toString();

	}

}
