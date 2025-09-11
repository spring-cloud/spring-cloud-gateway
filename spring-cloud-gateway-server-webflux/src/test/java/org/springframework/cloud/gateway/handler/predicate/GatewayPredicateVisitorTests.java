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

package org.springframework.cloud.gateway.handler.predicate;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * @author Spencer Gibb
 * @author FuYiNan Guo
 */
public class GatewayPredicateVisitorTests {

	@Test
	public void asyncPredicateVisitVisitsEachNode() {
		PathRoutePredicateFactory pathRoutePredicateFactory = new PathRoutePredicateFactory(new WebFluxProperties());
		HostRoutePredicateFactory hostRoutePredicateFactory = new HostRoutePredicateFactory();
		ReadBodyRoutePredicateFactory readBodyRoutePredicateFactory1 = new ReadBodyRoutePredicateFactory();
		ReadBodyRoutePredicateFactory readBodyRoutePredicateFactory2 = new ReadBodyRoutePredicateFactory();
		AsyncPredicate<ServerWebExchange> predicate = AsyncPredicate
			.from(pathRoutePredicateFactory.apply(pathRoutePredicateFactory.newConfig()))
			.and(AsyncPredicate.from(hostRoutePredicateFactory.apply(hostRoutePredicateFactory.newConfig())))
			.and(readBodyRoutePredicateFactory1.applyAsync(readBodyRoutePredicateFactory1.newConfig()))
			.and(readBodyRoutePredicateFactory2.applyAsync(readBodyRoutePredicateFactory2.newConfig()));

		Route route = Route.async().id("git").uri("http://myuri").asyncPredicate(predicate).build();
		ArrayList<Object> configs = new ArrayList<>();
		route.getPredicate().accept(p -> configs.add(p.getConfig()));

		assertThat(configs).hasSize(4)
			.hasExactlyElementsOfTypes(PathRoutePredicateFactory.Config.class, HostRoutePredicateFactory.Config.class,
					ReadBodyRoutePredicateFactory.Config.class, ReadBodyRoutePredicateFactory.Config.class);
	}

	@Test
	public void predicateVisitVisitsEachNode() {
		PathRoutePredicateFactory pathRoutePredicateFactory = new PathRoutePredicateFactory(new WebFluxProperties());
		HostRoutePredicateFactory hostRoutePredicateFactory = new HostRoutePredicateFactory();
		Predicate<ServerWebExchange> predicate = pathRoutePredicateFactory.apply(pathRoutePredicateFactory.newConfig())
			.and(hostRoutePredicateFactory.apply(hostRoutePredicateFactory.newConfig()));

		Route route = Route.async().id("git").uri("http://myuri").predicate(predicate).build();
		ArrayList<Object> configs = new ArrayList<>();
		route.getPredicate().accept(p -> configs.add(p.getConfig()));

		assertThat(configs).hasSize(2)
			.hasExactlyElementsOfTypes(PathRoutePredicateFactory.Config.class, HostRoutePredicateFactory.Config.class);
	}

	@Test
	public void pathRoutePredicateVisitWithSetWebfluxBasePath() {
		WebFluxProperties webFluxProperties = new WebFluxProperties();
		webFluxProperties.setBasePath("/gw/api/v1");

		PathRoutePredicateFactory pathRoutePredicateFactory = new PathRoutePredicateFactory(webFluxProperties);
		PathRoutePredicateFactory.Config config = new PathRoutePredicateFactory.Config()
			.setPatterns(List.of("/temp/**"))
			.setMatchTrailingSlash(true);

		Predicate<ServerWebExchange> predicate = pathRoutePredicateFactory.apply(config);

		ServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("http://127.0.0.1:8080/gw/api/v1/temp/test").build());

		assertThat(predicate.test(exchange)).isEqualTo(true);
	}

	@Test
	public void pathRoutePredicateVisitWithSetWebfluxBasePathStripPrefix() {
		WebFluxProperties webFluxProperties = new WebFluxProperties();
		webFluxProperties.setBasePath("/gw/api/v1");

		PathRoutePredicateFactory pathRoutePredicateFactory = new PathRoutePredicateFactory(webFluxProperties);
		PathRoutePredicateFactory.Config config = new PathRoutePredicateFactory.Config()
			.setPatterns(List.of("/temp/**"))
			.setMatchTrailingSlash(true);

		Predicate<ServerWebExchange> predicate = pathRoutePredicateFactory.apply(config);

		ServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("http://127.0.0.1:8080/gw/api/v1/temp/test").build());

		assertThat(predicate.test(exchange)).isEqualTo(true);

		// webflux base path strips prefix is 3
		GatewayFilter filter = new StripPrefixGatewayFilterFactory().apply(c -> c.setParts(3));

		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, filterChain);

		ServerWebExchange webExchange = captor.getValue();

		assertThat(webExchange.getRequest().getURI()).hasPath("/temp/test");

		URI requestUrl = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(requestUrl).hasScheme("http").hasHost("127.0.0.1").hasPort(8080).hasPath("/temp/test");

		LinkedHashSet<URI> uris = webExchange.getRequiredAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
		assertThat(uris).contains(exchange.getRequest().getURI());
	}

}
