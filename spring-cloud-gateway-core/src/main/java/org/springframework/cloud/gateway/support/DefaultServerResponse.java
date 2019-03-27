/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.support;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.result.view.ViewResolver;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;

public class DefaultServerResponse<T> implements ServerResponse {


	private final ServerWebExchange exchange;

	private final BodyInserter<T, ? super ServerHttpResponse> inserter;

	private final Map<String, Object> hints;

	public DefaultServerResponse(ServerWebExchange exchange,
			BodyInserter<T, ? super ServerHttpResponse> body, Map<String, Object> hints) {
		this.exchange = exchange;
		Assert.notNull(exchange, "ServerWebExchange must not be null");
		Assert.notNull(body, "BodyInserter must not be null");
		this.inserter = body;
		this.hints = hints;
	}

	private ServerHttpResponse response() {
		return exchange.getResponse();
	}

	@Override
	public final HttpStatus statusCode() {
		//TODO: non standard status code
		return HttpStatus.valueOf(response().getStatusCode().value());
	}

	@Override
	public final HttpHeaders headers() {
		return response().getHeaders();
	}

	@Override
	public MultiValueMap<String, ResponseCookie> cookies() {
		return response().getCookies();
	}

	@Override
	public final Mono<Void> writeTo(ServerWebExchange exchange, Context context) {
		return this.inserter.insert(exchange.getResponse(), new BodyInserter.Context() {
			@Override
			public List<HttpMessageWriter<?>> messageWriters() {
				return context.messageWriters();
			}
			@Override
			public Optional<ServerHttpRequest> serverRequest() {
				return Optional.of(exchange.getRequest());
			}
			@Override
			public Map<String, Object> hints() {
				return hints;
			}
		});
	}


	public static class HandlerStrategiesResponseContext implements ServerResponse.Context {

		private HandlerStrategies strategies = HandlerStrategies.withDefaults();

		public HandlerStrategiesResponseContext() {
		}

		public HandlerStrategiesResponseContext(HandlerStrategies strategies) {
			this.strategies = strategies;
		}

		@Override
		public List<HttpMessageWriter<?>> messageWriters() {
			return this.strategies.messageWriters();
		}

		@Override
		public List<ViewResolver> viewResolvers() {
			return this.strategies.viewResolvers();
		}
	}
}
