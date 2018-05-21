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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class DefaultClientRequest implements ClientRequest {

	private final HttpMethod method;

	private final URI url;

	private final HttpHeaders headers;

	private final MultiValueMap<String, String> cookies;

	private final BodyInserter<?, ? super ClientHttpRequest> body;

	private final Map<String, Object> attributes;

	public DefaultClientRequest(ServerWebExchange exchange,
								BodyInserter<?, ? super ClientHttpRequest> body) {
		ServerHttpRequest request = exchange.getRequest();
		this.method = request.getMethod();
		this.url = request.getURI();
		this.headers = HttpHeaders.readOnlyHttpHeaders(request.getHeaders());
		MultiValueMap<String, String> cookies = new LinkedMultiValueMap<>();
		for (Map.Entry<String, List<HttpCookie>> entry : request.getCookies().entrySet()) {
			List<String> values = new ArrayList<>();
			for (HttpCookie cookie : entry.getValue()) {
				values.add(cookie.getValue());
			}
			cookies.put(entry.getKey(), values);
		}
		this.cookies = CollectionUtils.unmodifiableMultiValueMap(cookies);
		this.body = body;
		this.attributes = Collections.unmodifiableMap(exchange.getAttributes());
	}

	@Override
	public HttpMethod method() {
		return this.method;
	}

	@Override
	public URI url() {
		return this.url;
	}

	@Override
	public HttpHeaders headers() {
		return this.headers;
	}

	@Override
	public MultiValueMap<String, String> cookies() {
		return this.cookies;
	}

	@Override
	public BodyInserter<?, ? super ClientHttpRequest> body() {
		return this.body;
	}

	@Override
	public Map<String, Object> attributes() {
		return this.attributes;
	}

	@Override
	public Mono<Void> writeTo(ClientHttpRequest request, ExchangeStrategies strategies) {
		HttpHeaders requestHeaders = request.getHeaders();
		if (!this.headers.isEmpty()) {
			this.headers.entrySet().stream()
					.filter(entry -> !requestHeaders.containsKey(entry.getKey()))
					.forEach(entry -> requestHeaders
							.put(entry.getKey(), entry.getValue()));
		}

		MultiValueMap<String, HttpCookie> requestCookies = request.getCookies();
		if (!this.cookies.isEmpty()) {
			this.cookies.forEach((name, values) -> values.forEach(value -> {
				HttpCookie cookie = new HttpCookie(name, value);
				requestCookies.add(name, cookie);
			}));
		}

		return this.body.insert(request, new BodyInserter.Context() {
			@Override
			public List<HttpMessageWriter<?>> messageWriters() {
				return strategies.messageWriters();
			}
			@Override
			public Optional<ServerHttpRequest> serverRequest() {
				return Optional.empty();
			}
			@Override
			public Map<String, Object> hints() {
				return Collections.emptyMap();
			}
		});
	}
}
