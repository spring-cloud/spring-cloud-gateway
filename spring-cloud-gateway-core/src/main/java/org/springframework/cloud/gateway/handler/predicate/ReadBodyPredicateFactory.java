/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Map;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.support.BodyInserterContext;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.support.DefaultServerRequest;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter.CACHED_REQUEST_BODY_KEY;

/**
 * This predicate is BETA and may be subject to change in a future release.
 */
public class ReadBodyPredicateFactory
		extends AbstractRoutePredicateFactory<ReadBodyPredicateFactory.Config> {

	private static final String TEST_ATTRIBUTE = "read_body_predicate_test_attribute";
	private static final String CACHE_REQUEST_BODY_OBJECT_KEY = "cachedRequestBodyObject";
	private final ServerCodecConfigurer codecConfigurer;

	public ReadBodyPredicateFactory(ServerCodecConfigurer codecConfigurer) {
		super(Config.class);
		this.codecConfigurer = codecConfigurer;
	}

	@Override
	@SuppressWarnings("unchecked")
	public AsyncPredicate<ServerWebExchange> applyAsync(Config config) {
		return exchange -> {
			Class inClass = config.getInClass();

			Object cachedBody = exchange.getAttribute(CACHE_REQUEST_BODY_OBJECT_KEY);
			Mono<?> modifiedBody;
			if(cachedBody != null) {
				boolean test = config.predicate.test(cachedBody);
				exchange.getAttributes().put(TEST_ATTRIBUTE, test);
				modifiedBody = Mono.just(cachedBody);
			} else {
				ServerRequest serverRequest = new DefaultServerRequest(exchange);
				// TODO: flux or mono
				modifiedBody = serverRequest.bodyToMono(inClass)
						// .log("modify_request_mono", Level.INFO)
						.flatMap(body -> {
							// TODO: migrate to async
							exchange.getAttributes().put(CACHE_REQUEST_BODY_OBJECT_KEY, body);
							boolean test = config.predicate.test(body);
							exchange.getAttributes().put(TEST_ATTRIBUTE, test);
							return Mono.just(body);
						});
			}
			BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, inClass);
			CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange,
					exchange.getRequest().getHeaders());
			return bodyInserter.insert(outputMessage, new BodyInserterContext())
					// .log("modify_request", Level.INFO)
					.then(Mono.defer(() -> {
						boolean test = (Boolean) exchange.getAttributes()
								.getOrDefault(TEST_ATTRIBUTE, Boolean.FALSE);
						exchange.getAttributes().remove(TEST_ATTRIBUTE);
						exchange.getAttributes().put(CACHED_REQUEST_BODY_KEY,
								outputMessage.getBody());
						return Mono.just(test);
					}));
		};
	}

	@Override
	@SuppressWarnings("unchecked")
	public Predicate<ServerWebExchange> apply(Config config) {
		throw new UnsupportedOperationException(
				"ReadBodyPredicateFactory is only async.");
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

		public <T> Config setPredicate(Class<T> inClass, Predicate<T> predicate) {
			setInClass(inClass);
			this.predicate = predicate;
			return this;
		}

		public Config setPredicate(Predicate predicate) {
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
