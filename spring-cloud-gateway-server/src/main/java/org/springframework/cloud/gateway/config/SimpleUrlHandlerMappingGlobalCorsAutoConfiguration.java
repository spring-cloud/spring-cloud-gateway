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

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

/**
 * This is useful for PreFlight CORS requests. We can add a "global" configuration here so
 * we don't have to modify existing predicates to allow the "options" HTTP method.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(SimpleUrlHandlerMapping.class)
@ConditionalOnProperty(name = "spring.cloud.gateway.globalcors.add-to-simple-url-handler-mapping",
		matchIfMissing = false)
public class SimpleUrlHandlerMappingGlobalCorsAutoConfiguration {

	@Autowired
	private GlobalCorsProperties globalCorsProperties;

	@Autowired
	private SimpleUrlHandlerMapping simpleUrlHandlerMapping;

	@PostConstruct
	void config() {
		simpleUrlHandlerMapping.setCorsConfigurations(globalCorsProperties.getCorsConfigurations());
	}

}
