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

package org.springframework.cloud.gateway.support.tagsprovider;

import io.micrometer.core.instrument.Tags;
import org.junit.Test;

import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.OK;

/**
 * @author Ingyu Hwang
 */
public class GatewayHttpTagsProviderTests {

	private final GatewayHttpTagsProvider tagsProvider = new GatewayHttpTagsProvider();

	private static final String ROUTE_URI = "http://gatewaytagsprovider.org:80";

	private static final Tags DEFAULT_TAGS = Tags.of("outcome", OK.series().name(), "status", OK.name(),
			"httpStatusCode", String.valueOf(OK.value()), "httpMethod", "GET");

	@Test
	public void httpTags() {
		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(ROUTE_URI).build());
		exchange.getResponse().setStatusCode(OK);

		Tags tags = tagsProvider.apply(exchange);
		assertThat(tags).isEqualTo(DEFAULT_TAGS);
	}

	@Test
	public void statusNotChanged() {
		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(ROUTE_URI).build());

		Tags tags = tagsProvider.apply(exchange);
		assertThat(tags).isEqualTo(
				Tags.of("outcome", "CUSTOM", "status", "CUSTOM", "httpStatusCode", "NA", "httpMethod", "GET"));
	}

	@Test
	public void notAbstractServerHttpResponse() {
		ServerWebExchange mockExchange = mock(ServerWebExchange.class);
		ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(new MockServerHttpResponse());
		responseDecorator.setStatusCode(OK);

		when(mockExchange.getRequest()).thenReturn(MockServerHttpRequest.get(ROUTE_URI).build());
		when(mockExchange.getResponse()).thenReturn(responseDecorator);

		Tags tags = tagsProvider.apply(mockExchange);
		assertThat(tags).isEqualTo(DEFAULT_TAGS);
	}

}
