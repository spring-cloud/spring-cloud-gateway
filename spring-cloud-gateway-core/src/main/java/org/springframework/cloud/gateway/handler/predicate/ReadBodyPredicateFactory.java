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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.support.BodyInserterContext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;

import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter.CACHED_REQUEST_BODY_KEY;
import static org.springframework.cloud.gateway.handler.predicate.ReadBodyPredicateFactory.DataBufferMapCollector.BODY_ONE;
import static org.springframework.cloud.gateway.handler.predicate.ReadBodyPredicateFactory.DataBufferMapCollector.BODY_TWO;

/**
 * This predicate is BETA and may be subject to change in a future release.
 */
public class ReadBodyPredicateFactory
		extends AbstractRoutePredicateFactory<ReadBodyPredicateFactory.Config> {
	protected static final Log LOGGER = LogFactory.getLog(ReadBodyPredicateFactory.class);

	private static final String TEST_ATTRIBUTE = "read_body_predicate_test_attribute";
	private static final String CACHE_REQUEST_BODY_OBJECT_KEY = "cachedRequestBodyObject";
	private static final List<HttpMessageReader<?>> messageReaders = HandlerStrategies.withDefaults().messageReaders();

	public ReadBodyPredicateFactory() {
		super(Config.class);
	}

	@Override
	@SuppressWarnings("unchecked")
	public AsyncPredicate<ServerWebExchange> applyAsync(Config config) {
		return exchange -> {
			Class inClass = config.getInClass();

			Object cachedBody = exchange.getAttribute(CACHE_REQUEST_BODY_OBJECT_KEY);
			Mono<?> modifiedBody;
			// We can only read the body from the request once, once that happens if we try to read the body again an
			// exception will be thrown.  The below if/else caches the body object as a request attribute in the ServerWebExchange
			// so if this filter is run more than once (due to more than one route using it) we do not try to read the
			// request body multiple times
			if (cachedBody != null) {
				try {
					boolean test = config.predicate.test(cachedBody);
					exchange.getAttributes().put(TEST_ATTRIBUTE, test);
					return Mono.just(test);
				} catch (ClassCastException e) {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("Predicate test failed because class in predicate does not match the cached body object",
								e);
					}
				}
				return Mono.just(false);
			} else {
				return exchange.getRequest().getBody().collect(new DataBufferMapCollector()).flatMap(dataBufferMap -> {
					BodyInserter bodyInserter = BodyInserters.fromPublisher(dataBufferMap.get(BODY_ONE), DataBuffer.class);
					CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange,
							exchange.getRequest().getHeaders());
					return bodyInserter.insert(outputMessage, new BodyInserterContext())
							// .log("modify_request", Level.INFO)
							.then(Mono.defer(() -> {
								ResolvableType type = ResolvableType.forClass(inClass);
								for (HttpMessageReader<?> messageReader : messageReaders) {
									if (messageReader.canRead(type, exchange.getRequest().getHeaders().getContentType())) {
										ReactiveHttpInputMessage inputMessage = new ReadBodyReactiveHttpInputMessage(dataBufferMap.get(BODY_TWO),
												exchange.getRequest().getHeaders());
										Function mapper = (bodyObj) -> {
											exchange.getAttributes().put(CACHE_REQUEST_BODY_OBJECT_KEY, bodyObj);
											exchange.getAttributes().put(CACHED_REQUEST_BODY_KEY,
													outputMessage.getBody());
											boolean test = config.predicate.test(bodyObj);
											return Mono.just(test);
										};
										return messageReader.readMono(type, inputMessage, Collections.EMPTY_MAP).flatMap(mapper);
									}
								}
								return Mono.just(false);
							}));

				});
			}
		};
	}

	/**
	 * This {@link Collector} is meant to collect the {@code Flux<DataBuffer>} from the request body into a {@link Map}
	 * which contains two copy of the body, one under the key {@code orig} and the other under the {@key copy}.
	 */
	 class DataBufferMapCollector implements Collector<DataBuffer, Map<String, Flux<DataBuffer>>, Map<String, Flux<DataBuffer>>> {
		public static final String BODY_ONE = "bodyOne";
		public static final String BODY_TWO = "bodyTwo";
		private final Set<Characteristics> CHARACTERISTICS = new HashSet<>(Arrays.asList(
				Characteristics.IDENTITY_FINISH));

		@Override
		public Supplier<Map<String, Flux<DataBuffer>>> supplier() {
			return () -> new HashMap<String, Flux<DataBuffer>>();
		}

		@Override
		public BiConsumer<Map<String, Flux<DataBuffer>>, DataBuffer> accumulator() {
			return (dataBufferMap, dataBuffer) -> {
				accumulate(BODY_ONE, dataBufferMap, dataBuffer);
				accumulate(BODY_TWO, dataBufferMap, dataBuffer);
			};
		}

		private void accumulate(String key, Map<String, Flux<DataBuffer>> dataBufferMap, DataBuffer dataBuffer) {
			if (dataBufferMap.get(key) == null) {
				dataBufferMap.put(key, Flux.just(copy(dataBuffer)));
			} else {
				dataBufferMap.put(key, dataBufferMap.get(key).mergeWith(Flux.just(copy(dataBuffer))));
			}
		}

		@Override
		public BinaryOperator<Map<String, Flux<DataBuffer>>> combiner() {
			return (map1, map2) -> {
				map2.forEach((k, v) -> map1.merge(k, v, (v1, v2) -> v1.mergeWith(v2)));
				return map1;
			};
		}

		@Override
		public Function<Map<String, Flux<DataBuffer>>, Map<String, Flux<DataBuffer>>> finisher() {
			return Function.identity();
		}

		@Override
		public Set<Characteristics> characteristics() {
			return CHARACTERISTICS;
		}

		private DataBuffer copy(DataBuffer dataBuffer) {
			return dataBuffer.factory().allocateBuffer().write(dataBuffer.asByteBuffer());
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Predicate<ServerWebExchange> apply(Config config) {
		throw new UnsupportedOperationException(
				"ReadBodyPredicateFactory is only async.");
	}

	 static class ReadBodyReactiveHttpInputMessage implements ReactiveHttpInputMessage {
		private Flux<DataBuffer> body;
		private HttpHeaders httpHeaders;

		public ReadBodyReactiveHttpInputMessage(Flux<DataBuffer> body, HttpHeaders headers) {
			this.body = body;
			this.httpHeaders = headers;
		}

		@Override
		public Flux<DataBuffer> getBody() {
			return body;
		}

		@Override
		public HttpHeaders getHeaders() {
			return httpHeaders;
		}
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
