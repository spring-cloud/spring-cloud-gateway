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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.filter.WebsocketRoutingFilter.SEC_WEBSOCKET_PROTOCOL;
import static org.springframework.cloud.gateway.filter.WebsocketRoutingFilter.changeSchemeIfIsWebSocketUpgrade;
import static org.springframework.cloud.gateway.filter.WebsocketRoutingFilter.convertHttpToWs;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;
import static org.springframework.http.HttpHeaders.HOST;
import static org.springframework.http.HttpHeaders.UPGRADE;

public class WebsocketRoutingFilterTests {

	@Test
	@SuppressWarnings("unchecked")
	public void testProtocolParsing() {
		ObjectProvider<List<HttpHeadersFilter>> headersFilters = mock(ObjectProvider.class);
		WebsocketRoutingFilter filter = new WebsocketRoutingFilter(mock(WebSocketClient.class),
				mock(WebSocketService.class), headersFilters);

		HttpHeaders headers = new HttpHeaders();
		headers.put(SEC_WEBSOCKET_PROTOCOL, Arrays.asList(" p1,p2", "p3 , p4 "));
		List<String> protocols = filter.getProtocols(headers);
		assertThat(protocols).containsExactly("p1", "p2", "p3", "p4");
	}

	@Test
	public void testConvertHttpToWs() {
		assertThat(convertHttpToWs("http")).isEqualTo("ws");
		assertThat(convertHttpToWs("HTTP")).isEqualTo("ws");
		assertThat(convertHttpToWs("https")).isEqualTo("wss");
		assertThat(convertHttpToWs("HTTPS")).isEqualTo("wss");
		assertThat(convertHttpToWs("tcp")).isEqualTo("tcp");
	}

	@Test
	public void testEncodedUrl() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://not-matters-that")
				.header(UPGRADE, "WebSocket").build();
		ServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR,
				URI.create("http://microservice/my-service/websocket%20upgrade"));
		changeSchemeIfIsWebSocketUpgrade(exchange);
		URI wsRequestUrl = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(wsRequestUrl).isEqualTo(URI.create("ws://microservice/my-service/websocket%20upgrade"));
	}

	@Test
	public void testHeadersFilter() {
		assertDefaultHeadersFilters(false);
	}

	@Test
	public void testHeadersFilterPreserveHost() {
		assertDefaultHeadersFilters(true);
	}

	@SuppressWarnings("unchecked")
	private void assertDefaultHeadersFilters(boolean preserveHostHeader) {
		ObjectProvider<List<HttpHeadersFilter>> headersFilters = mock(ObjectProvider.class);
		when(headersFilters.getIfAvailable(any())).thenReturn(new ArrayList<>());
		WebsocketRoutingFilter filter = new WebsocketRoutingFilter(mock(WebSocketClient.class),
				mock(WebSocketService.class), headersFilters);
		List<HttpHeadersFilter> filters = filter.getHeadersFilters();
		MockServerHttpRequest request = MockServerHttpRequest.get("ws://not-matters-that").header(HOST, "MyHost")
				.header("Sec-Websocket-Something", "someval").header("x-foo", "bar").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(PRESERVE_HOST_HEADER_ATTRIBUTE, preserveHostHeader);
		HttpHeaders httpHeaders = HttpHeadersFilter.filterRequest(filters, exchange);
		assertThat(httpHeaders).doesNotContainKeys("Sec-Websocket-Something").containsKey("x-foo");
		if (preserveHostHeader) {
			assertThat(httpHeaders).containsKey(HOST);
		}
		else {
			assertThat(httpHeaders).doesNotContainKeys(HOST);
		}
	}

}
