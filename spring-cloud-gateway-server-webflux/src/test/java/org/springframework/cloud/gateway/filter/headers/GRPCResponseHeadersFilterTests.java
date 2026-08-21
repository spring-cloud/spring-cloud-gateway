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

package org.springframework.cloud.gateway.filter.headers;

import java.util.function.Consumer;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerResponse;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author hutiefang
 */
public class GRPCResponseHeadersFilterTests {

	@Test
	@SuppressWarnings("unchecked")
	public void shouldIncludeGrpcStatusDetailsBinTrailerIfGRPC() {
		ServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
			.header(HttpHeaders.CONTENT_TYPE, "application/grpc")
			.build();
		HttpServerResponse nativeResponse = mock(HttpServerResponse.class);
		when(nativeResponse.trailerHeaders(any())).thenReturn(nativeResponse);
		ServerWebExchange exchange = mock(ServerWebExchange.class);
		when(exchange.getRequest()).thenReturn(request);
		when(exchange.getResponse()).thenReturn(new TestServerHttpResponse(nativeResponse));

		HttpHeaders headers = new HttpHeaders();
		headers.set("grpc-status", "13");
		headers.set("grpc-message", "internal");
		headers.set("grpc-status-details-bin", "AQID");

		new GRPCResponseHeadersFilter().filter(headers, exchange);

		ArgumentCaptor<Consumer<io.netty.handler.codec.http.HttpHeaders>> captor = ArgumentCaptor
			.forClass(Consumer.class);
		verify(nativeResponse).trailerHeaders(captor.capture());
		io.netty.handler.codec.http.HttpHeaders trailers = new DefaultHttpHeaders();
		captor.getValue().accept(trailers);
		assertThat(trailers.get("grpc-status")).isEqualTo("13");
		assertThat(trailers.get("grpc-message")).isEqualTo("internal");
		assertThat(trailers.get("grpc-status-details-bin")).isEqualTo("AQID");
	}

	private static final class TestServerHttpResponse extends AbstractServerHttpResponse {

		private final HttpServerResponse nativeResponse;

		private TestServerHttpResponse(HttpServerResponse nativeResponse) {
			super(new DefaultDataBufferFactory());
			this.nativeResponse = nativeResponse;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> T getNativeResponse() {
			return (T) this.nativeResponse;
		}

		@Override
		protected Mono<Void> writeWithInternal(Publisher<? extends DataBuffer> body) {
			return Mono.empty();
		}

		@Override
		protected Mono<Void> writeAndFlushWithInternal(Publisher<? extends Publisher<? extends DataBuffer>> body) {
			return Mono.empty();
		}

		@Override
		protected void applyStatusCode() {
		}

		@Override
		protected void applyHeaders() {
		}

		@Override
		protected void applyCookies() {
		}

	}

}
