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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Arrays;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

import static java.util.Collections.singletonList;
import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.gateway.set-status")
public class SetStatusGatewayFilterFactory extends AbstractGatewayFilterFactory<SetStatusGatewayFilterFactory.Config> {

	/**
	 * Status key.
	 */
	public static final String STATUS_KEY = "status";

	/**
	 * The name of the header which contains http code of the proxied request.
	 */
	private String originalStatusHeaderName;

	public SetStatusGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(STATUS_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		HttpStatusHolder statusHolder = HttpStatusHolder.parse(config.status);

		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// option 1 (runs in filter order)
				/*
				 * exchange.getResponse().beforeCommit(() -> {
				 * exchange.getResponse().setStatusCode(finalStatus); return Mono.empty();
				 * }); return chain.filter(exchange);
				 */

				// option 2 (runs in reverse filter order)
				return chain.filter(exchange).then(Mono.fromRunnable(() -> {
					// check not really needed, since it is guarded in setStatusCode,
					// but it's a good example
					HttpStatus statusCode = exchange.getResponse().getStatusCode();
					boolean isStatusCodeUpdated = setResponseStatus(exchange, statusHolder);
					if (isStatusCodeUpdated && originalStatusHeaderName != null) {
						exchange.getResponse().getHeaders().set(originalStatusHeaderName,
								singletonList(statusCode.value()).toString());
					}
				}));
			}

			@Override
			public String toString() {
				return filterToStringCreator(SetStatusGatewayFilterFactory.this).append("status", config.getStatus())
						.toString();
			}
		};
	}

	public String getOriginalStatusHeaderName() {
		return originalStatusHeaderName;
	}

	public void setOriginalStatusHeaderName(String originalStatusHeaderName) {
		this.originalStatusHeaderName = originalStatusHeaderName;
	}

	public static class Config {

		// TODO: relaxed HttpStatus converter
		private String status;

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

	}

}
