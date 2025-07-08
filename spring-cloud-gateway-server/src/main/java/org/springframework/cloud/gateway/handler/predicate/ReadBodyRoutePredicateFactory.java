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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;

import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * Predicate that reads the body and applies a user provided predicate to run on the body.
 * The body is cached in memory so that possible subsequent calls to the predicate do not
 * need to deserialize again.
 */
public class ReadBodyRoutePredicateFactory extends AbstractRoutePredicateFactory<ReadBodyRoutePredicateFactory.Config> {

	protected static final Log log = LogFactory.getLog(ReadBodyRoutePredicateFactory.class);

	private static final String TEST_ATTRIBUTE = "read_body_predicate_test_attribute";

	private static final String CACHE_REQUEST_BODY_OBJECT_KEY = "cachedRequestBodyObject";

	private final List<HttpMessageReader<?>> messageReaders;

	public ReadBodyRoutePredicateFactory() {
		super(Config.class);
		this.messageReaders = HandlerStrategies.withDefaults().messageReaders();
	}

	public ReadBodyRoutePredicateFactory(List<HttpMessageReader<?>> messageReaders) {
		super(Config.class);
		this.messageReaders = messageReaders;
	}

	@Override
	@SuppressWarnings("unchecked")
	public AsyncPredicate<ServerWebExchange> applyAsync(Config config) {
		return new AsyncPredicate<ServerWebExchange>() {
			@Override
			public Publisher<Boolean> apply(ServerWebExchange exchange) {
				exchange.getAttributes().put(TEST_ATTRIBUTE, false);

				ServerHttpRequest mutableRequest = exchange
					.getAttribute(ServerWebExchangeUtils.CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR);
				if (mutableRequest != null) {
					return ServerRequest.create(exchange.mutate().request(mutableRequest).build(), messageReaders)
						.bodyToMono(config.getInClass())
						.doOnNext(objectValue -> exchange.getAttributes()
							.put(CACHE_REQUEST_BODY_OBJECT_KEY, objectValue))
						.map(objectValue -> {
							boolean test = config.getPredicate().test(objectValue);
							exchange.getAttributes().put(TEST_ATTRIBUTE, test);
							return test;
						});
				}
				else {
					return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange,
							(serverHttpRequest) -> ServerRequest
								.create(exchange.mutate().request(serverHttpRequest).build(), messageReaders)
								.bodyToMono(config.getInClass())
								.doOnNext(objectValue -> exchange.getAttributes()
									.put(CACHE_REQUEST_BODY_OBJECT_KEY, objectValue))
								.map(objectValue -> {
									boolean test = config.getPredicate().test(objectValue);
									exchange.getAttributes().put(TEST_ATTRIBUTE, test);
									return test;
								}));
				}
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("ReadBody: %s", config.getInClass());
			}
		};
	}

	@Override
	@SuppressWarnings("unchecked")
	public Predicate<ServerWebExchange> apply(Config config) {
		throw new UnsupportedOperationException("ReadBodyPredicateFactory is only async.");
	}

	public static class Config {

		private Class inClass;

		private Predicate predicate;

		private Map<String, Object> hints;

		public Class getInClass() {
			return inClass;
		}

		public Config setInClass(Class inClass) {
			this.inClass = inClass;
			return this;
		}

		public Predicate getPredicate() {
			return predicate;
		}

		public Config setPredicate(Predicate predicate) {
			this.predicate = predicate;
			return this;
		}

		public <T> Config setPredicate(Class<T> inClass, Predicate<T> predicate) {
			setInClass(inClass);
			this.predicate = predicate;
			return this;
		}

		public Map<String, Object> getHints() {
			return hints;
		}

		public Config setHints(Map<String, Object> hints) {
			this.hints = hints;
			return this;
		}

	}

}
