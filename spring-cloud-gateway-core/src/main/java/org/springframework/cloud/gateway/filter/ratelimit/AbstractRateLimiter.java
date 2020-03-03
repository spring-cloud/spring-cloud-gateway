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

package org.springframework.cloud.gateway.filter.ratelimit;

import java.util.Map;

import org.springframework.cloud.gateway.event.FilterArgsEvent;
import org.springframework.cloud.gateway.support.AbstractStatefulConfigurable;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationListener;
import org.springframework.core.style.ToStringCreator;

public abstract class AbstractRateLimiter<C> extends AbstractStatefulConfigurable<C>
		implements RateLimiter<C>, ApplicationListener<FilterArgsEvent> {

	private String configurationPropertyName;

	private ConfigurationService configurationService;

	protected AbstractRateLimiter(Class<C> configClass, String configurationPropertyName,
			ConfigurationService configurationService) {
		super(configClass);
		this.configurationPropertyName = configurationPropertyName;
		this.configurationService = configurationService;
	}

	protected String getConfigurationPropertyName() {
		return configurationPropertyName;
	}

	protected void setConfigurationService(ConfigurationService configurationService) {
		this.configurationService = configurationService;
	}

	@Override
	public void onApplicationEvent(FilterArgsEvent event) {
		Map<String, Object> args = event.getArgs();

		if (args.isEmpty() || !hasRelevantKey(args)) {
			return;
		}

		String routeId = event.getRouteId();

		C routeConfig = newConfig();
		if (this.configurationService != null) {
			this.configurationService.with(routeConfig)
					.name(this.configurationPropertyName).normalizedProperties(args)
					.bind();
		}
		getConfig().put(routeId, routeConfig);
	}

	private boolean hasRelevantKey(Map<String, Object> args) {
		return args.keySet().stream()
				.anyMatch(key -> key.startsWith(configurationPropertyName + "."));
	}

	@Override
	public String toString() {
		return new ToStringCreator(this)
				.append("configurationPropertyName", configurationPropertyName)
				.append("config", getConfig()).append("configClass", getConfigClass())
				.toString();
	}

}
