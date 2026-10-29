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

import java.nio.charset.Charset;
import java.util.ArrayList;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.Connection;
import reactor.netty.NettyInbound;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;

import static io.netty.buffer.PooledByteBufAllocator.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR;

/**
 * @author Violeta Georgieva
 * @author Seungbin Ko
 */
public class NettyWriteResponseFilterTests {

	@Test
	public void testWrap_NettyDataBufferFactory() {
		doTestWrap(new MockServerHttpResponse(new NettyDataBufferFactory(DEFAULT)));
	}

	@Test
	public void testWrap_DefaultDataBufferFactory() {
		doTestWrap(new MockServerHttpResponse());
	}

	@Test
	public void committedResponseDisposesConnectionWithoutWriting() {
		NettyWriteResponseFilter filter = new NettyWriteResponseFilter(new ArrayList<>(), null);
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
		exchange.getResponse().setComplete().block();
		Connection connection = mock(Connection.class);
		exchange.getAttributes().put(CLIENT_RESPONSE_CONN_ATTR, connection);

		filter.filter(exchange, ex -> Mono.empty()).block();

		verify(connection).dispose();
		verify(connection, never()).inbound();
	}

	@Test
	public void retainedBuffersAreReleasedOnDiscard() {
		NettyWriteResponseFilter filter = new NettyWriteResponseFilter(new ArrayList<>(), null);

		ByteBuf buffer1 = DEFAULT.buffer();
		buffer1.writeCharSequence("test-1", Charset.defaultCharset());
		ByteBuf buffer2 = DEFAULT.buffer();
		buffer2.writeCharSequence("test-2", Charset.defaultCharset());
		assertThat(buffer1.refCnt()).isEqualTo(1);
		assertThat(buffer2.refCnt()).isEqualTo(1);

		Connection connection = mock(Connection.class);
		NettyInbound inbound = mock(NettyInbound.class);
		EmbeddedChannel channel = new EmbeddedChannel();
		when(connection.inbound()).thenReturn(inbound);
		when(connection.channel()).thenReturn(channel);

		Flux<ByteBuf> inboundFlux = Flux.create(sink -> {
			sink.next(buffer1);
			sink.next(buffer2);
		});
		when(inbound.receive()).thenReturn(ByteBufFlux.fromInbound(inboundFlux));

		MockServerHttpRequest request = MockServerHttpRequest.get("/").build();
		ServerHttpResponse response = new ServerHttpResponseDecorator(
				new MockServerHttpResponse(new NettyDataBufferFactory(DEFAULT))) {
			@Override
			public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
				return Flux.from(body).take(1).doOnNext(DataBufferUtils::release).then();
			}
		};

		ServerWebExchange exchange = new ServerWebExchangeDecorator(MockServerWebExchange.from(request)) {
			@Override
			public ServerHttpResponse getResponse() {
				return response;
			}
		};
		exchange.getAttributes().put(CLIENT_RESPONSE_CONN_ATTR, connection);

		filter.filter(exchange, ex -> Mono.empty()).block();

		assertThat(buffer1.refCnt()).isEqualTo(1);
		buffer1.release();
		assertThat(buffer1.refCnt()).isEqualTo(0);

		assertThat(buffer2.refCnt()).isEqualTo(0);

		channel.finishAndReleaseAll();
	}

	private void doTestWrap(MockServerHttpResponse response) {
		NettyWriteResponseFilter filter = new NettyWriteResponseFilter(new ArrayList<>(), null);

		ByteBuf buffer = DEFAULT.buffer();
		buffer.writeCharSequence("test", Charset.defaultCharset());

		DataBuffer result = filter.wrap(buffer, response);

		assertThat(result.toString(Charset.defaultCharset())).isEqualTo("test");

		if (result instanceof PooledDataBuffer) {
			((PooledDataBuffer) result).release();
		}

		assertThat(buffer.refCnt()).isEqualTo(0);
	}

}
