/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Collections;
import java.util.function.Function;

import io.github.bucket4j.BucketConfiguration;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.gateway.server.mvc.config.RouteProperties;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctionDefinition;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

@AutoConfiguration
public class FilterAutoConfiguration {

	@Bean
	public FilterBeanFactoryDiscoverer filterBeanFactoryDiscoverer(BeanFactory beanFactory) {
		return new FilterBeanFactoryDiscoverer(beanFactory);
	}

	@Bean
	public FilterFunctions.FilterSupplier filterFunctionsSupplier() {
		return new FilterFunctions.FilterSupplier();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(BucketConfiguration.class)
	static class Bucket4jFilterConfiguration {

		@Bean
		public Bucket4jFilterFunctions.FilterSupplier bucket4jFilterFunctionsSupplier() {
			return new Bucket4jFilterFunctions.FilterSupplier();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(CircuitBreaker.class)
	static class CircuitBreakerFilterConfiguration {

		@Bean
		public CircuitBreakerFilterFunctions.FilterSupplier circuitBreakerFilterFunctionsSupplier() {
			return new CircuitBreakerFilterFunctions.FilterSupplier();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(LoadBalancerClient.class)
	public static class LoadBalancerHandlerConfiguration {

		@Bean
		public Function<RouteProperties, HandlerFunctionDefinition> lbHandlerFunctionDefinition() {
			return routeProperties -> new HandlerFunctionDefinition.Default("lb", HandlerFunctions.http(),
					Collections.emptyList(),
					Collections.singletonList(LoadBalancerFilterFunctions.lb(routeProperties.getUri().getHost())));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(RetryTemplate.class)
	static class RetryFilterConfiguration {

		@Bean
		public RetryFilterFunctions.FilterSupplier retryFilterFunctionsSupplier() {
			return new RetryFilterFunctions.FilterSupplier();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(OAuth2AuthorizedClient.class)
	static class TokenRelayFilterConfiguration {

		@Bean
		public TokenRelayFilterFunctions.FilterSupplier tokenRelayFilterFunctionsSupplier() {
			return new TokenRelayFilterFunctions.FilterSupplier();
		}

	}

}
