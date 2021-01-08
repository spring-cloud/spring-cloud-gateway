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

import java.net.URI;

import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;

/**
 * @author Spencer Gibb
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingClass("org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer")
@ConditionalOnMissingBean(ReactiveLoadBalancer.class)
@EnableConfigurationProperties(GatewayLoadBalancerProperties.class)
@AutoConfigureAfter(GatewayReactiveLoadBalancerClientAutoConfiguration.class)
public class GatewayNoLoadBalancerClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(ReactiveLoadBalancerClientFilter.class)
	public NoLoadBalancerClientFilter noLoadBalancerClientFilter(GatewayLoadBalancerProperties properties) {
		return new NoLoadBalancerClientFilter(properties.isUse404());
	}

	protected static class NoLoadBalancerClientFilter implements GlobalFilter, Ordered {

		private final boolean use404;

		public NoLoadBalancerClientFilter(boolean use404) {
			this.use404 = use404;
		}

		@Override
		public int getOrder() {
			return LOAD_BALANCER_CLIENT_FILTER_ORDER;
		}

		@Override
		@SuppressWarnings("Duplicates")
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			URI url = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
			String schemePrefix = exchange.getAttribute(GATEWAY_SCHEME_PREFIX_ATTR);
			if (url == null || (!"lb".equals(url.getScheme()) && !"lb".equals(schemePrefix))) {
				return chain.filter(exchange);
			}

			throw NotFoundException.create(use404, "Unable to find instance for " + url.getHost());
		}

	}

}
