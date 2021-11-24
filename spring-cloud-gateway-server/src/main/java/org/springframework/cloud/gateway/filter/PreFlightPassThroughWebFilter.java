/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.gateway.filter;

import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.cloud.gateway.handler.PassThroughPreFlightRequestHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

/**
 * @author mouxhun
 */
public class PreFlightPassThroughWebFilter implements WebFilter, ApplicationContextAware {

	private final PassThroughPreFlightRequestHandler handler;

	public PreFlightPassThroughWebFilter() {
		this.handler = new PassThroughPreFlightRequestHandler();
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		handler.setApplicationContext(applicationContext);
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		return (CorsUtils.isPreFlightRequest(exchange.getRequest())
				? this.handler.handlePreFlight(exchange) : chain.filter(exchange));
	}

}
