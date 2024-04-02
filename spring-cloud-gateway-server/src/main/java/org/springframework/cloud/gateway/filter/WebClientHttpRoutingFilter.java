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

package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter.filterRequest;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

/**
 * @author Spencer Gibb
 */
public class WebClientHttpRoutingFilter implements GlobalFilter, Ordered {

	private final WebClient webClient;

	private final ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider;

	private volatile List<HttpHeadersFilter> headersFilters;

	public WebClientHttpRoutingFilter(WebClient webClient,
			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider) {
		this.webClient = webClient;
		this.headersFiltersProvider = headersFiltersProvider;
	}

	public List<HttpHeadersFilter> getHeadersFilters() {
		if (headersFilters == null) {
			headersFilters = headersFiltersProvider.getIfAvailable();
		}
		return headersFilters;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	/**
	 * A {@code GlobalFilter} that actually routes the request to the proxied server with the injected
	 * {@code WebClient} if:
	 * <p>
	 * 1. It hasn't been routed already ({@link ServerWebExchangeUtils#isAlreadyRouted(ServerWebExchange)}
	 * invoked on the exchange returns {@code false}).
	 * <p>
	 * 2. The request's URI's scheme is either {@code http} or {@code https}. The URI is retrieved by
	 * fetching the exchange's {@link ServerWebExchangeUtils#GATEWAY_REQUEST_URL_ATTR} attribute –
	 * <em>not</em> by examining the exchange's request directly.
	 * <p>
	 * Before forwarding the request, this filter first applies all {@link HttpHeadersFilter}s provided
	 * by the injected {@code ObjectProvider} on the request's headers. Only retained headers are fed
	 * to the {@code WebClient}.
	 * <p>
	 * Even though this class mainly depends on provided {@code HttpHeadersFilter}s to filter request headers,
	 * this method has an embedded filtering mechanism that excludes the {@code Host} request header from
	 * headers passed to the {@code WebClient} unless {@link ServerWebExchangeUtils#PRESERVE_HOST_HEADER_ATTRIBUTE}
	 * is explicitly set to {@code true}. Note that a {@code WebClient} implementation may still add additional
	 * headers to the request, including {@code Host}.
	 * <p>
	 * If the request's method is {@code POST}, {@code PUT}, or {@code PATCH}, the request body will be
	 * passed to the {@code WebClient}. Otherwise, this class ignores the body altogether.
	 * <p>
	 * This filter asynchronously transfers all headers contained in the response from the proxied server
	 * to the exchange's response and sets the response status code. It <em>does not</em>, however, write
	 * the response body and instead only stores the received response as the {@link ServerWebExchangeUtils#CLIENT_RESPONSE_ATTR}
	 * exchange attribute. Following that, this filter passes the exchange down the filter chain.
	 * <p>
	 * If either of the two aforementioned requirements is not met, this filter passes the given exchange
	 * to the next filter in the chain straight away.
	 *
	 * @throws IllegalArgumentException if the exchange doesn't have a
	 * {@link ServerWebExchangeUtils#GATEWAY_REQUEST_URL_ATTR} attribute
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || (!"http".equals(scheme) && !"https".equals(scheme))) {
			return chain.filter(exchange);
		}
		setAlreadyRouted(exchange);

		ServerHttpRequest request = exchange.getRequest();

		HttpMethod method = request.getMethod();

		HttpHeaders filteredHeaders = filterRequest(getHeadersFilters(), exchange);

		boolean preserveHost = exchange.getAttributeOrDefault(PRESERVE_HOST_HEADER_ATTRIBUTE, false);

		RequestBodySpec bodySpec = this.webClient.method(method).uri(requestUrl).headers(httpHeaders -> {
			httpHeaders.addAll(filteredHeaders);
			// TODO: can this support preserviceHostHeader?
			if (!preserveHost) {
				httpHeaders.remove(HttpHeaders.HOST);
			}
		});

		RequestHeadersSpec<?> headersSpec;
		if (requiresBody(method)) {
			headersSpec = bodySpec.body(BodyInserters.fromDataBuffers(request.getBody()));
		}
		else {
			headersSpec = bodySpec;
		}

		return headersSpec.exchangeToMono(Mono::just)
				.flatMap(res -> {
					ServerHttpResponse response = exchange.getResponse();
					response.getHeaders().putAll(res.headers().asHttpHeaders());
					response.setStatusCode(res.statusCode());
					// Defer committing the response until all route filters have run
					// Put client response as ServerWebExchange attribute and write
					// response later NettyWriteResponseFilter
					exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, res);
					return chain.filter(exchange);
				});
	}

	private boolean requiresBody(HttpMethod method) {
		return method.equals(HttpMethod.PUT) || method.equals(HttpMethod.POST) || method.equals(HttpMethod.PATCH);
	}

}
