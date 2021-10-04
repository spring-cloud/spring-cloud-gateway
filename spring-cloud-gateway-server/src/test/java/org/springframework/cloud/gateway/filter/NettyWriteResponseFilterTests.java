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

import java.nio.charset.Charset;
import java.util.ArrayList;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import static io.netty.buffer.PooledByteBufAllocator.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Violeta Georgieva
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

	private void doTestWrap(MockServerHttpResponse response) {
		NettyWriteResponseFilter filter = new NettyWriteResponseFilter(new ArrayList<>());

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
