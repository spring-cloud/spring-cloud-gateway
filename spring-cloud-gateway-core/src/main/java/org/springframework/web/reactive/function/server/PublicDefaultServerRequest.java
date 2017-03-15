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

package org.springframework.web.reactive.function.server;

import org.springframework.http.HttpCookie;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class PublicDefaultServerRequest extends DefaultServerRequest {
	private ServerWebExchange exchange;

	public PublicDefaultServerRequest(ServerWebExchange exchange) {
		this(exchange, HandlerStrategies.withDefaults());
	}

	public PublicDefaultServerRequest(ServerWebExchange exchange, HandlerStrategies strategies) {
		super(exchange, strategies);
		this.exchange = exchange;
	}

	public MultiValueMap<String, HttpCookie> getCookies() {
		return this.exchange.getRequest().getCookies();
	}

	public ServerWebExchange getExchange() {
		return exchange;
	}
}
