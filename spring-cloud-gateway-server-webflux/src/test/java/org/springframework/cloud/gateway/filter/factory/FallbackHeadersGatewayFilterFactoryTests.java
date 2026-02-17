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

package org.springframework.cloud.gateway.filter.factory;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;

/**
 * Tests for {@link FallbackHeadersGatewayFilterFactory}.
 *
 * @author Spring Cloud Gateway Team
 */
class FallbackHeadersGatewayFilterFactoryTests {

	private final FallbackHeadersGatewayFilterFactory factory = new FallbackHeadersGatewayFilterFactory();

	private final GatewayFilterChain chain = mock(GatewayFilterChain.class);

	@Test
	void shouldSetExecutionExceptionTypeHeader() {
		FallbackHeadersGatewayFilterFactory.Config config = new FallbackHeadersGatewayFilterFactory.Config();
		GatewayFilter filter = factory.apply(config);

		RuntimeException exception = new RuntimeException("Test exception message");
		ServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("http://localhost/get").build());
		exchange.getAttributes().put(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR, exception);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(chain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		verify(chain).filter(any(ServerWebExchange.class));
		verifyNoMoreInteractions(chain);

		ServerWebExchange filteredExchange = captor.getValue();
		HttpHeaders headers = filteredExchange.getRequest().getHeaders();

		assertThat(headers.get(config.getExecutionExceptionTypeHeaderName()))
			.containsExactly(RuntimeException.class.getName());
		assertThat(headers.get(config.getExecutionExceptionMessageHeaderName()))
			.containsExactly("Test exception message");
	}

	@Test
	void shouldSetExecutionExceptionTypeAndMessageHeadersDistinctly() {
		FallbackHeadersGatewayFilterFactory.Config config = new FallbackHeadersGatewayFilterFactory.Config();
		config.setExecutionExceptionTypeHeaderName("Custom-Exception-Type");
		config.setExecutionExceptionMessageHeaderName("Custom-Exception-Message");
		GatewayFilter filter = factory.apply(config);

		IllegalStateException exception = new IllegalStateException("Custom error message");
		ServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("http://localhost/get").build());
		exchange.getAttributes().put(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR, exception);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(chain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		verify(chain).filter(any(ServerWebExchange.class));
		verifyNoMoreInteractions(chain);

		ServerWebExchange filteredExchange = captor.getValue();
		HttpHeaders headers = filteredExchange.getRequest().getHeaders();

		assertThat(headers.get("Custom-Exception-Type")).containsExactly(IllegalStateException.class.getName());
		assertThat(headers.get("Custom-Exception-Message")).containsExactly("Custom error message");
		assertThat(headers.get("Custom-Exception-Type")).isNotEqualTo(headers.get("Custom-Exception-Message"));
	}

	@Test
	void shouldHandleExceptionWithNullMessage() {
		FallbackHeadersGatewayFilterFactory.Config config = new FallbackHeadersGatewayFilterFactory.Config();
		GatewayFilter filter = factory.apply(config);

		NullPointerException exception = new NullPointerException();
		ServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("http://localhost/get").build());
		exchange.getAttributes().put(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR, exception);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(chain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		verify(chain).filter(any(ServerWebExchange.class));
		verifyNoMoreInteractions(chain);

		ServerWebExchange filteredExchange = captor.getValue();
		HttpHeaders headers = filteredExchange.getRequest().getHeaders();

		assertThat(headers.get(config.getExecutionExceptionTypeHeaderName()))
			.containsExactly(NullPointerException.class.getName());
		assertThat(headers.get(config.getExecutionExceptionMessageHeaderName())).containsExactly("");
	}

	@Test
	void shouldNotAddHeadersWhenNoException() {
		FallbackHeadersGatewayFilterFactory.Config config = new FallbackHeadersGatewayFilterFactory.Config();
		GatewayFilter filter = factory.apply(config);

		ServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("http://localhost/get").build());

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(chain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		verify(chain).filter(any(ServerWebExchange.class));
		verifyNoMoreInteractions(chain);

		ServerWebExchange filteredExchange = captor.getValue();
		HttpHeaders headers = filteredExchange.getRequest().getHeaders();

		assertThat(headers.get(config.getExecutionExceptionTypeHeaderName())).isNull();
		assertThat(headers.get(config.getExecutionExceptionMessageHeaderName())).isNull();
	}

}
