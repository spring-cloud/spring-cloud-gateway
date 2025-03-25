/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.filter.factory.RewriteRequestParameterGatewayFilterFactory.Config;

/**
 * @author Fredrich Ombico
 */
class RewriteRequestParameterGatewayFilterFactoryTests {

	@Test
	void toStringFormat() {
		Config config = new Config();
		config.setName("campaign");
		config.setReplacement("fall2023");
		GatewayFilter filter = new RewriteRequestParameterGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("campaign").contains("fall2023");
	}

	@Test
	void rewriteRequestParameterFilterWorks() {
		testRewriteRequestParameterFilter("campaign", "fall2023", "size=small&campaign=old",
				Map.of("size", List.of("small"), "campaign", List.of("fall2023")));
	}

	@Test
	void rewriteRequestParameterFilterRewritesMultipleParamsWithSameName() {
		testRewriteRequestParameterFilter("campaign", "fall2023", "campaign=fall&size=small&campaign=old",
				Map.of("size", List.of("small"), "campaign", List.of("fall2023")));
	}

	@Test
	void rewriteRequestParameterFilterDoesNotAddParamIfNameNotFound() {
		testRewriteRequestParameterFilter("campaign", "winter2023", "color=green&sort=popular",
				Map.of("color", List.of("green"), "sort", List.of("popular")));
	}

	@Test
	void rewriteRequestParameterFilterWithSpecialCharactersInParameterValue() {
		testRewriteRequestParameterFilter("campaign", "black friday~(1.A-B_C!)", "campaign=old&color=green",
				Map.of("campaign", List.of("black friday~(1.A-B_C!)"), "color", List.of("green")));
	}

	@Test
	void rewriteRequestParameterFilterWithSpecialCharactersInParameterName() {
		testRewriteRequestParameterFilter("campaign[]", "red", "campaign%5B%5D=blue&color=green",
				Map.of("campaign[]", List.of("red"), "color", List.of("green")));
	}

	@Test
	void rewriteRequestParameterFilterKeepsOtherParamsEncoded() {
		testRewriteRequestParameterFilter("color", "white", "campaign%5B%5D=blue&color=green",
				Map.of("campaign[]", List.of("blue"), "color", List.of("white")));
	}

	private void testRewriteRequestParameterFilter(String name, String replacement, String query,
			Map<String, List<String>> expectedQueryParams) {
		GatewayFilter filter = new RewriteRequestParameterGatewayFilterFactory()
			.apply(config -> config.setReplacement(replacement).setName(name));

		URI url = UriComponentsBuilder.fromUriString("http://localhost/get").query(query).build(true).toUri();
		MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, url).build();

		ServerWebExchange exchange = MockServerWebExchange.from(request);

		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, filterChain);

		ServerWebExchange webExchange = captor.getValue();

		MultiValueMap<String, String> actualQueryParams = webExchange.getRequest().getQueryParams();
		assertThat(actualQueryParams).containsExactlyInAnyOrderEntriesOf(expectedQueryParams);
	}

}
