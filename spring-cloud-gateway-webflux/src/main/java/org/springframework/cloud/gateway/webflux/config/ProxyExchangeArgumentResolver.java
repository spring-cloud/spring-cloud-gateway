/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.gateway.webflux.config;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Set;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.webflux.ProxyExchange;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Dave Syer
 * @author Tim Ysewyn
 */
public class ProxyExchangeArgumentResolver implements HandlerMethodArgumentResolver {

	private WebClient rest;

	private HttpHeaders headers;

	private Set<String> autoForwardedHeaders;

	private Set<String> excluded;

	public ProxyExchangeArgumentResolver(WebClient builder) {
		this.rest = builder;
	}

	public void setHeaders(HttpHeaders headers) {
		this.headers = headers;
	}

	public void setAutoForwardedHeaders(Set<String> autoForwardedHeaders) {
		this.autoForwardedHeaders = autoForwardedHeaders;
	}

	@Deprecated
	public void setSensitive(Set<String> excluded) {
		setExcluded(excluded);
	}

	public void setExcluded(Set<String> excluded) {
		this.excluded = excluded;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return ProxyExchange.class.isAssignableFrom(parameter.getParameterType());
	}

	private Type type(MethodParameter parameter) {
		Type type = parameter.getGenericParameterType();
		if (type instanceof ParameterizedType) {
			ParameterizedType param = (ParameterizedType) type;
			type = param.getActualTypeArguments()[0];
		}
		if (type instanceof TypeVariable || type instanceof WildcardType) {
			type = Object.class;
		}
		return type;
	}

	@Override
	public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
			ServerWebExchange exchange) {
		ProxyExchange<?> proxy = new ProxyExchange<>(rest, exchange, bindingContext, type(parameter));
		proxy.headers(headers);
		if (this.autoForwardedHeaders.size() > 0) {
			proxy.headers(extractAutoForwardedHeaders(exchange));
		}
		if (excluded != null) {
			proxy.excluded(excluded.toArray(new String[0]));
		}
		return Mono.just(proxy);
	}

	private HttpHeaders extractAutoForwardedHeaders(ServerWebExchange exchange) {
		HttpHeaders headers = new HttpHeaders();
		exchange.getRequest().getHeaders().forEach((header, values) -> {
			if (this.autoForwardedHeaders.contains(header)) {
				headers.addAll(header, values);
			}
		});
		return headers;
	}

}
