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

import org.junit.Test;

import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.filter.WebsocketRoutingFilter.changeSchemeIfIsWebSocketUpgrade;
import static org.springframework.cloud.gateway.filter.WebsocketRoutingFilter.convertHttpToWs;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.http.HttpHeaders.UPGRADE;

public class WebsocketRoutingFilterTests {

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

}
