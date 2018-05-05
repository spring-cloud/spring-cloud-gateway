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
import java.util.Optional;
import java.util.function.Predicate;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.factory.rewrite.HttpMessageWriterResponse;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter.CACHED_REQUEST_BODY_KEY;
import static org.springframework.cloud.gateway.filter.factory.rewrite.RewriteUtils.getHttpMessageReader;
import static org.springframework.cloud.gateway.filter.factory.rewrite.RewriteUtils.getHttpMessageWriter;
import static org.springframework.cloud.gateway.filter.factory.rewrite.RewriteUtils.process;

public class ReadBodyPredicateFactory extends AbstractRoutePredicateFactory<ReadBodyPredicateFactory.Config> {

	private final ServerCodecConfigurer codecConfigurer;

    public ReadBodyPredicateFactory(ServerCodecConfigurer codecConfigurer) {
        super(Config.class);
		this.codecConfigurer = codecConfigurer;
	}

    @Override
    @SuppressWarnings("unchecked")
    public Predicate<ServerWebExchange> apply(Config config) {
        return exchange -> {
            MediaType mediaType = exchange.getRequest().getHeaders().getContentType();
            ResolvableType elementType = ResolvableType.forClass(config.getInClass());
            Optional<HttpMessageReader<?>> reader = getHttpMessageReader(codecConfigurer, elementType, mediaType);
            boolean answer = false;
            if (reader.isPresent()) {
                Mono<Object> readMono = reader.get()
                        .readMono(elementType, exchange.getRequest(), config.getHints())
                        .cast(Object.class);
                answer = process(readMono, peek -> {
                    Optional<HttpMessageWriter<?>> writer = getHttpMessageWriter(codecConfigurer, elementType, mediaType);

                    if (writer.isPresent()) {
                        Publisher publisher = Mono.just(peek);
                        HttpMessageWriterResponse fakeResponse = new HttpMessageWriterResponse(exchange.getResponse().bufferFactory());
                        writer.get().write(publisher, elementType, mediaType, fakeResponse, config.getHints());
                        exchange.getAttributes().put(CACHED_REQUEST_BODY_KEY, fakeResponse.getBody());
                    }
                    return config.getPredicate().test(peek);
                });

            }
            return answer;
        };
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
