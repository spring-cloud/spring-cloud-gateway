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

package org.springframework.cloud.gateway.filter.cors;

import java.util.Collections;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * Dummy filter to display order and configuration in actuator endpoints.
 *
 * @author Fredrich Ombico
 */
public class CorsGatewayFilterFactory implements GatewayFilterFactory<CorsGatewayFilterConfig> {

	@Override
	public GatewayFilter apply(CorsGatewayFilterConfig config) {
		// all the work is taken care of by CorsGatewayFilterApplicationListener before we
		// reach here
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				return chain.filter(exchange);
			}

			@Override
			public String toString() {
				return filterToStringCreator(CorsGatewayFilterFactory.this).append(cleanBrackets(config.getCors()))
						.toString();
			}

			private String cleanBrackets(String corsConfiguration) {
				int begin = corsConfiguration.startsWith("[") ? 1 : 0;
				int end = corsConfiguration.length() - (corsConfiguration.endsWith("]") ? 1 : 0);
				return corsConfiguration.substring(begin, end);
			}
		};
	}

	@Override
	public Class<CorsGatewayFilterConfig> getConfigClass() {
		return CorsGatewayFilterConfig.class;
	}

	@Override
	public ShortcutType shortcutType() {
		return ShortcutType.GATHER_LIST;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("cors");
	}

}
