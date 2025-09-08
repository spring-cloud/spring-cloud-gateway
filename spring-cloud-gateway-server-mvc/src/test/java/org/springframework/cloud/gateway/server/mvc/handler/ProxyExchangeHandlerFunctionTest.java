/*
 * Copyright 2025-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.handler;

import java.net.URI;
import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.server.mvc.common.AbstractProxyExchange;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.ResponseHttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author raccoonback
 */
class ProxyExchangeHandlerFunctionTest {

	@Test
	void keepOriginalEncodingOfQueryParameter() {
		TestProxyExchange proxyExchange = new TestProxyExchange();
		ProxyExchangeHandlerFunction function = new ProxyExchangeHandlerFunction(proxyExchange, new ObjectProvider<>() {
			@Override
			public RequestHttpHeadersFilter getObject() throws BeansException {
				return null;
			}

			@Override
			public RequestHttpHeadersFilter getObject(Object... args) throws BeansException {
				return null;
			}

			@Override
			public RequestHttpHeadersFilter getIfAvailable() throws BeansException {
				return null;
			}

			@Override
			public RequestHttpHeadersFilter getIfUnique() throws BeansException {
				return null;
			}

			@Override
			public Stream<RequestHttpHeadersFilter> orderedStream() {
				return Stream.of((httpHeaders, serverRequest) -> new HttpHeaders());
			}

		}, new ObjectProvider<>() {

			@Override
			public ResponseHttpHeadersFilter getObject() throws BeansException {
				return null;
			}

			@Override
			public ResponseHttpHeadersFilter getObject(Object... args) throws BeansException {
				return null;
			}

			@Override
			public ResponseHttpHeadersFilter getIfAvailable() throws BeansException {
				return null;
			}

			@Override
			public ResponseHttpHeadersFilter getIfUnique() throws BeansException {
				return null;
			}

			@Override
			public Stream<ResponseHttpHeadersFilter> orderedStream() {
				return Stream.of((httpHeaders, serverRequest) -> new HttpHeaders());

			}
		});

		function.onApplicationEvent(null);

		MockHttpServletRequest servletRequest = MockMvcRequestBuilders
			.get("http://localhost/é?foo=value1 value2&bar=value3=&qux=value4+")
			.buildRequest(null);
		servletRequest.setAttribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR, URI.create("http://localhost:8080"));
		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		function.handle(request);

		URI uri = proxyExchange.getRequest().getUri();

		assertThat(uri).hasToString("http://localhost:8080/%C3%A9?foo=value1%20value2&bar=value3%3D&qux=value4%2B")
			.hasPath("/é")
			.hasParameter("foo", "value1 value2")
			.hasParameter("bar", "value3=")
			.hasParameter("qux", "value4+");
	}

	private class TestProxyExchange extends AbstractProxyExchange {

		private Request request;

		protected TestProxyExchange() {
			super(new GatewayMvcProperties());
		}

		@Override
		public ServerResponse exchange(Request request) {
			this.request = request;

			return ServerResponse.ok().build();
		}

		public Request getRequest() {
			return request;
		}

	}

}
