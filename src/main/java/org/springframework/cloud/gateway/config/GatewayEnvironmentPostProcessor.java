/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.config;

import java.util.Collections;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * @author Spencer Gibb
 */
public class GatewayEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

	@Override
	public int getOrder() {
		return DEFAULT_ORDER;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment configurableEnvironment, SpringApplication springApplication) {
		//TODO: make this configurable?
		//TODO: should I have to do this?
		final Map<String, Object> properties = Collections.singletonMap("spring.resources.add-mappings", "false");
		configurableEnvironment.getPropertySources().addLast(new MapPropertySource("gateway", properties));
	}
}

