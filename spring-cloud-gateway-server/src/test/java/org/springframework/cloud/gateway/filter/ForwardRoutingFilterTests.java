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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * @author Arjun Curat
 */

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ForwardRoutingFilterTests {

	private ServerWebExchange exchange;

	@Mock
	private GatewayFilterChain chain;

	@Mock
	private ObjectProvider<DispatcherHandler> objectProvider;

	@Mock
	private DispatcherHandler dispatcherHandler;

	@InjectMocks
	private ForwardRoutingFilter forwardRoutingFilter;

	@BeforeEach
	public void setup() {
		exchange = MockServerWebExchange.from(MockServerHttpRequest.get("localendpoint").build());
		when(objectProvider.getIfAvailable()).thenReturn(this.dispatcherHandler);
	}

	@Test
	public void shouldNotFilterWhenGatewayRequestUrlSchemeIsNotForward() {
		URI uri = UriComponentsBuilder.fromUriString("https://endpoint").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
		forwardRoutingFilter.filter(exchange, chain);

		verifyNoInteractions(dispatcherHandler);
		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
	}

	@Test
	public void shouldFilterWhenGatewayRequestUrlSchemeIsForward() {
		URI uri = UriComponentsBuilder.fromUriString("forward://endpoint").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		assertThat(exchange.getAttributes().get(GATEWAY_ALREADY_ROUTED_ATTR)).isNull();

		forwardRoutingFilter.filter(exchange, chain);

		verifyNoMoreInteractions(chain);
		verify(dispatcherHandler).handle(exchange);

		assertThat(exchange.getAttributes().get(GATEWAY_ALREADY_ROUTED_ATTR)).isNull();
	}

	@Test
	public void shouldFilterAndKeepHostPathAsSpecified() {

		URI uri = UriComponentsBuilder.fromUriString("forward://host/outage").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

		forwardRoutingFilter.filter(exchange, chain);

		verify(dispatcherHandler).handle(captor.capture());

		assertThat(exchange.getAttributes().get(GATEWAY_ALREADY_ROUTED_ATTR)).isNull();

		ServerWebExchange webExchange = captor.getValue();

		URI forwardedUrl = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		assertThat(forwardedUrl).hasScheme("forward").hasHost("host").hasPath("/outage");
	}

	@Test
	public void shouldNotFilterWhenGatewayRequestUrlSchemeIsForwardButAlreadyRouted() {
		URI uri = UriComponentsBuilder.fromUriString("forward://host").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
		exchange.getAttributes().put(GATEWAY_ALREADY_ROUTED_ATTR, true);

		forwardRoutingFilter.filter(exchange, chain);

		verifyNoInteractions(dispatcherHandler);
		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
	}

	@Test
	public void orderIsLowestPrecedence() {
		assertThat(forwardRoutingFilter.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
	}

}
