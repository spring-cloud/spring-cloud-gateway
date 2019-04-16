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

package org.springframework.cloud.gateway.rsocket.autoconfigure;

import java.util.Collections;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class GatewayRSocketEnvironmentPostProcessor implements EnvironmentPostProcessor {

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment env,
			SpringApplication application) {
		Boolean enabled = env.getProperty("spring.cloud.gateway.rsocket.enabled",
				Boolean.class, true);
		if (enabled && !env.containsProperty("spring.rsocket.server.port")) {
			Map<String, Object> map = Collections
					.singletonMap("spring.rsocket.server.port", 7002);
			env.getPropertySources()
					.addLast(new MapPropertySource("Default Gateway RSocket Port", map));
		}
	}

}
