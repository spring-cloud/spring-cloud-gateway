/*
 * Copyright 2013-2018 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.filter.headers;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.LinkedHashSet;

import org.junit.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_FOR_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_HOST_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_PORT_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_PREFIX_HEADER;
import static org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter.X_FORWARDED_PROTO_HEADER;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * @author Spencer Gibb
 */
public class XForwardedHeadersFilterTests {

	@Test
	public void remoteAddressIsNull() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost:8080/get")
				.header(HttpHeaders.HOST, "myhost")
				.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers).containsKeys(X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_HOST_HEADER)).isEqualTo("localhost:8080");
		assertThat(headers.getFirst(X_FORWARDED_PORT_HEADER)).isEqualTo("8080");
		assertThat(headers.getFirst(X_FORWARDED_PROTO_HEADER)).isEqualTo("http");
	}

	@Test
	public void xForwardedHeadersDoNotExist() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost:8080/get")
				.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
				.header(HttpHeaders.HOST, "myhost")
				.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers).containsKeys(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).isEqualTo("10.0.0.1");
		assertThat(headers.getFirst(X_FORWARDED_HOST_HEADER)).isEqualTo("localhost:8080");
		assertThat(headers.getFirst(X_FORWARDED_PORT_HEADER)).isEqualTo("8080");
		assertThat(headers.getFirst(X_FORWARDED_PROTO_HEADER)).isEqualTo("http");
	}

	@Test
	public void defaultPort() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get")
				.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
				.header(HttpHeaders.HOST, "myhost")
				.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers).containsKeys(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).isEqualTo("10.0.0.1");
		assertThat(headers.getFirst(X_FORWARDED_HOST_HEADER)).isEqualTo("localhost");
		assertThat(headers.getFirst(X_FORWARDED_PORT_HEADER)).isEqualTo("80");
		assertThat(headers.getFirst(X_FORWARDED_PROTO_HEADER)).isEqualTo("http");
	}

	@Test
	public void appendsValues() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost:8080/get")
				.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
				.header(X_FORWARDED_FOR_HEADER, "192.168.0.2")
				.header(X_FORWARDED_HOST_HEADER, "example.com")
				.header(X_FORWARDED_PORT_HEADER, "443")
				.header(X_FORWARDED_PROTO_HEADER, "https")
				.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers).containsKeys(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).isEqualTo("192.168.0.2,10.0.0.1");
		assertThat(headers.getFirst(X_FORWARDED_HOST_HEADER)).isEqualTo("example.com,localhost:8080");
		assertThat(headers.getFirst(X_FORWARDED_PORT_HEADER)).isEqualTo("443,8080");
		assertThat(headers.getFirst(X_FORWARDED_PROTO_HEADER)).isEqualTo("https,http");
	}

	@Test
	public void appendDisabled() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost:8080/get")
				.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
				.header(X_FORWARDED_FOR_HEADER, "192.168.0.2")
				.header(X_FORWARDED_HOST_HEADER, "example.com")
				.header(X_FORWARDED_PORT_HEADER, "443")
				.header(X_FORWARDED_PROTO_HEADER, "https")
				.header(X_FORWARDED_PREFIX_HEADER,"/prefix")
				.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter();
		filter.setForAppend(false);
		filter.setHostAppend(false);
		filter.setPortAppend(false);
		filter.setProtoAppend(false);
		filter.setPrefixAppend(false);

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers).containsKeys(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER,X_FORWARDED_PREFIX_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).isEqualTo("10.0.0.1");
		assertThat(headers.getFirst(X_FORWARDED_HOST_HEADER)).isEqualTo("localhost:8080");
		assertThat(headers.getFirst(X_FORWARDED_PORT_HEADER)).isEqualTo("8080");
		assertThat(headers.getFirst(X_FORWARDED_PROTO_HEADER)).isEqualTo("http");
		assertThat(headers.getFirst(X_FORWARDED_PREFIX_HEADER)).isEqualTo("/prefix");
	}


	@Test
	public void prefixToInfer() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://originalhost:8080/prefix/get")
				.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
				.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter();
		filter.setPrefixAppend(true);
		filter.setPrefixEnabled(true);

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		LinkedHashSet<URI> originalUris = new LinkedHashSet<>();
		originalUris.add(UriComponentsBuilder.fromUriString("http://originalhost:8080/prefix/get/").build().toUri()); //trailing slash
		exchange.getAttributes().put(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, originalUris);
		URI requestUri = UriComponentsBuilder.fromUriString("http://routedservice:8090/get").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUri);

		HttpHeaders headers = filter.filter(request.getHeaders(), exchange);

		assertThat(headers).containsKeys(X_FORWARDED_PREFIX_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_PREFIX_HEADER)).isEqualTo("/prefix");
	}

	@Test
	public void prefixAddedWithoutTrailingSlash() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://originalhost:8080/foo/bar")
				.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
				.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter();
		filter.setPrefixAppend(true);
		filter.setPrefixEnabled(true);

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		LinkedHashSet<URI> originalUris = new LinkedHashSet<>();
		originalUris.add(UriComponentsBuilder.fromUriString("http://originalhost:8080/foo/bar").build().toUri());
		exchange.getAttributes().put(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, originalUris);
		URI requestUri = UriComponentsBuilder.fromUriString("http://routedservice:8090/").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUri);

		HttpHeaders headers = filter.filter(request.getHeaders(), exchange);

		assertThat(headers.getFirst(X_FORWARDED_PREFIX_HEADER)).isEqualTo("/foo/bar");
	}

	@Test
	public void noPrefixToInfer() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://originalhost:8080/get")
				.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
				.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter();
		filter.setPrefixAppend(true);
		filter.setPrefixEnabled(true);
		filter.setForEnabled(false);
		filter.setHostEnabled(false);
		filter.setPortEnabled(false);
		filter.setProtoEnabled(false);

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		LinkedHashSet<URI> originalUris = new LinkedHashSet<>();
		originalUris.add(UriComponentsBuilder.fromUriString("http://originalhost:8080/get/").build().toUri());
		exchange.getAttributes().put(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, originalUris);
		URI requestUri = UriComponentsBuilder.fromUriString("http://routedservice:8090/get").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUri);

		HttpHeaders headers = filter.filter(request.getHeaders(), exchange);

		assertThat(headers).isEmpty();
	}

	@Test
	public void routedPathInRequestPathButNotPrefix() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://originalhost:8080/get")
				.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
				.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter();
		filter.setPrefixAppend(true);
		filter.setPrefixEnabled(true);
		filter.setForEnabled(false);
		filter.setHostEnabled(false);
		filter.setPortEnabled(false);
		filter.setProtoEnabled(false);

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		LinkedHashSet<URI> originalUris = new LinkedHashSet<>();
		originalUris.add(UriComponentsBuilder.fromUriString("http://originalhost:8080/one/two/three").build().toUri());
		exchange.getAttributes().put(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, originalUris);
		URI requestUri = UriComponentsBuilder.fromUriString("http://routedservice:8090/two").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUri);

		HttpHeaders headers = filter.filter(request.getHeaders(), exchange);

		assertThat(headers).isEmpty();
	}

	@Test
	public void allDisabled() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost:8080/get")
				.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
				.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter();
		filter.setForEnabled(false);
		filter.setHostEnabled(false);
		filter.setPortEnabled(false);
		filter.setProtoEnabled(false);
		filter.setPrefixEnabled(false);

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers).isEmpty();
	}
}
