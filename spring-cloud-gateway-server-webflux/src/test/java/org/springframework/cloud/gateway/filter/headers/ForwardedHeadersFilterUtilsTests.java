/*
 * Copyright 2026-present the original author or authors.
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.SslInfo;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ForwardedHeadersFilterUtils}.
 *
 * @author Dmitrii Grigorev
 */
class ForwardedHeadersFilterUtilsTests {

	@Test
	void extractRemoteAddressFromNativeRequest() throws Exception {
		InetSocketAddress peerAddress = new InetSocketAddress(InetAddress.getByName("1.1.1.1"), 80);
		ServerHttpRequest request = new TestServerHttpRequestWithNative(peerAddress);

		InetSocketAddress result = ForwardedHeadersFilterUtils.extractPeerRemoteAddress(request);

		assertThat(result).isNotNull();
		assertThat(result.getHostString()).isEqualTo("1.1.1.1");
		assertThat(result.getPort()).isEqualTo(80);
	}

	@Test
	void extractRemoteAddressFromNativeRequestOverrides() throws Exception {
		InetSocketAddress peerAddress = new InetSocketAddress(InetAddress.getByName("1.1.1.1"), 80);
		ServerHttpRequest nativeRequest = new TestServerHttpRequestWithNative(peerAddress);

		InetSocketAddress clientAddress = new InetSocketAddress(InetAddress.getByName("2.2.2.2"), 80);

		ServerHttpRequest.Builder builder = nativeRequest.mutate();
		// such behaviour is here:
		// org.springframework.web.server.adapter.ForwardedHeaderTransformer
		builder.remoteAddress(clientAddress);
		ServerHttpRequest transformedRequest = builder.build();

		InetSocketAddress result = ForwardedHeadersFilterUtils.extractPeerRemoteAddress(transformedRequest);

		assertThat(result).isNotNull();
		// the transformed request's remote address is overridden, we can't rely on it.
		assertThat(Objects.requireNonNull(transformedRequest.getRemoteAddress()).getHostString()).isEqualTo("2.2.2.2");
		// only native request's has the real peer remote address
		assertThat(result.getHostString()).isEqualTo("1.1.1.1");
		assertThat(result.getPort()).isEqualTo(80);
	}

	@Test
	void extractRemoteAddressNull() {
		ServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get").build();

		InetSocketAddress result = ForwardedHeadersFilterUtils.extractPeerRemoteAddress(request);

		assertThat(result).isNull();
	}

	/**
	 * Minimal AbstractServerHttpRequest that exposes a native request with a peer remote
	 * address.
	 */
	private static final class TestServerHttpRequestWithNative extends AbstractServerHttpRequest {

		private final InetSocketAddress nativePeerAddress;

		TestServerHttpRequestWithNative(InetSocketAddress nativePeerAddress) {
			super(HttpMethod.GET, URI.create("http://localhost/"), null, new HttpHeaders());
			this.nativePeerAddress = nativePeerAddress;
		}

		@Override
		protected MultiValueMap<String, HttpCookie> initCookies() {
			return new LinkedMultiValueMap<>();
		}

		@Override
		protected SslInfo initSslInfo() {
			return null;
		}

		@Override
		public ServerHttpRequest getNativeRequest() {
			return MockServerHttpRequest.get("http://localhost/").remoteAddress(this.nativePeerAddress).build();
		}

		@Override
		public InetSocketAddress getRemoteAddress() {
			return null;
		}

		@Override
		public Flux<DataBuffer> getBody() {
			return Flux.empty();
		}

	}

}
