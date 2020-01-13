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

package org.springframework.cloud.gateway.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.filter.factory.FallbackHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerHystrixFilterFactory;
import org.springframework.cloud.netflix.hystrix.HystrixCircuitBreakerAutoConfiguration;
import org.springframework.cloud.netflix.hystrix.ReactiveHystrixCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

/**
 * @author Ryan Baxter
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
@AutoConfigureAfter({ HystrixCircuitBreakerAutoConfiguration.class })
@ConditionalOnClass({ DispatcherHandler.class,
		HystrixCircuitBreakerAutoConfiguration.class, ReactiveCircuitBreakerFactory.class,
		ReactiveHystrixCircuitBreakerFactory.class })
public class GatewayHystrixCircuitBreakerAutoConfiguration {

	@Bean
	@ConditionalOnBean(ReactiveHystrixCircuitBreakerFactory.class)
	public SpringCloudCircuitBreakerHystrixFilterFactory springCloudCircuitBreakerHystrixFilterFactory(
			ReactiveHystrixCircuitBreakerFactory reactiveCircuitBreakerFactory,
			ObjectProvider<DispatcherHandler> dispatcherHandler) {
		return new SpringCloudCircuitBreakerHystrixFilterFactory(
				reactiveCircuitBreakerFactory, dispatcherHandler);
	}

	@Bean
	@ConditionalOnMissingBean(FallbackHeadersGatewayFilterFactory.class)
	public FallbackHeadersGatewayFilterFactory fallbackHeadersGatewayFilterFactory() {
		return new FallbackHeadersGatewayFilterFactory();
	}

}
