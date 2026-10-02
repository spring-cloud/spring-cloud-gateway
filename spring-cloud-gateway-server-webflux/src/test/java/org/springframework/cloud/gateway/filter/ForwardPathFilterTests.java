/*
 * Copyright 2013-present the original author or authors.
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
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * Tests for {@link ForwardPathFilter}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ForwardPathFilterTests {

	private final ForwardPathFilter filter = new ForwardPathFilter();

	@Mock
	private GatewayFilterChain chain;

	@Test
	public void setsOriginalRequestUrlAttrWhenRouteSchemeIsForward() {
		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/original/path").build());
		Route route = Route.async()
			.id("forward_test")
			.uri(UriComponentsBuilder.fromUriString("forward:/internal/test").build().toUri())
			.predicate(swe -> true)
			.build();
		exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);

		when(chain.filter(any())).thenReturn(Mono.empty());

		filter.filter(exchange, chain);

		// see https://github.com/spring-cloud/spring-cloud-gateway/issues/4053: the
		// original request URL must be recorded before the path is rewritten to the
		// forward:// route's path, same as SetPathGatewayFilterFactory and friends do.
		LinkedHashSet<URI> originalUrls = exchange.getRequiredAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
		assertThat(originalUrls).hasSize(1);
		assertThat(originalUrls.iterator().next().getPath()).isEqualTo("/original/path");

		verify(chain).filter(
				argThat(mutatedExchange -> mutatedExchange.getRequest().getURI().getPath().equals("/internal/test")));
	}

	@Test
	public void doesNotSetOriginalRequestUrlAttrWhenRouteSchemeIsNotForward() {
		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/original/path").build());
		Route route = Route.async()
			.id("http_test")
			.uri(UriComponentsBuilder.fromUriString("https://example.org/internal/test").build().toUri())
			.predicate(swe -> true)
			.build();
		exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);

		when(chain.filter(any())).thenReturn(Mono.empty());

		filter.filter(exchange, chain);

		assertThat(exchange.getAttributes().get(GATEWAY_ORIGINAL_REQUEST_URL_ATTR)).isNull();
	}

	@Test
	public void doesNotSetOriginalRequestUrlAttrWhenNoRoute() {
		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/original/path").build());

		when(chain.filter(any())).thenReturn(Mono.empty());

		filter.filter(exchange, chain);

		assertThat(exchange.getAttributes().get(GATEWAY_ORIGINAL_REQUEST_URL_ATTR)).isNull();
	}

}
