/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.filter.ratelimit;

import java.util.Map;

import org.springframework.cloud.gateway.event.FilterArgsEvent;
import org.springframework.cloud.gateway.support.AbstractStatefulConfigurable;
import org.springframework.cloud.gateway.support.ConfigurationUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.Validator;

public abstract class AbstractRateLimiter<C> extends AbstractStatefulConfigurable<C> implements RateLimiter<C>, ApplicationListener<FilterArgsEvent> {
	private String configurationPropertyName;
	private Validator validator;

	protected AbstractRateLimiter(Class<C> configClass, String configurationPropertyName, Validator validator) {
		super(configClass);
		this.configurationPropertyName = configurationPropertyName;
		this.validator = validator;
	}

	protected String getConfigurationPropertyName() {
		return configurationPropertyName;
	}

	protected Validator getValidator() {
		return validator;
	}

	public void setValidator(Validator validator) {
		this.validator = validator;
	}

	@Override
	public void onApplicationEvent(FilterArgsEvent event) {
		Map<String, Object> args = event.getArgs();

		if (args.isEmpty() || !hasRelevantKey(args)) {
			return;
		}

		String routeId = event.getRouteId();
		C routeConfig = newConfig();
		ConfigurationUtils.bind(routeConfig, args,
				configurationPropertyName, configurationPropertyName, validator);
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
				.append("config", getConfig())
				.append("configClass", getConfigClass())
				.toString();
	}

}
