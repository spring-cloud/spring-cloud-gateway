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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.LinkedHashSet;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.reactor.netty.autoconfigure.NettyReactiveWebServerAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayProperties;
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

	public static final String ALLOW_ALL_REGEX = ".*";

	@Test
	public void remoteAddressIsNull() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
			.header(HttpHeaders.HOST, "myhost")
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).doesNotContain(X_FORWARDED_FOR_HEADER)
			.contains(X_FORWARDED_HOST_HEADER, X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_HOST_HEADER)).isEqualTo("localhost:8080");
		assertThat(headers.getFirst(X_FORWARDED_PORT_HEADER)).isEqualTo("8080");
		assertThat(headers.getFirst(X_FORWARDED_PROTO_HEADER)).isEqualTo("http");
	}

	@Test
	public void xForwardedHeadersDoNotExist() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(HttpHeaders.HOST, "myhost")
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).contains(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).isEqualTo("10.0.0.1");
		assertThat(headers.getFirst(X_FORWARDED_HOST_HEADER)).isEqualTo("localhost:8080");
		assertThat(headers.getFirst(X_FORWARDED_PORT_HEADER)).isEqualTo("8080");
		assertThat(headers.getFirst(X_FORWARDED_PROTO_HEADER)).isEqualTo("http");
	}

	@Test
	public void defaultPort() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(HttpHeaders.HOST, "myhost")
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).contains(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).isEqualTo("10.0.0.1");
		assertThat(headers.getFirst(X_FORWARDED_HOST_HEADER)).isEqualTo("localhost");
		assertThat(headers.getFirst(X_FORWARDED_PORT_HEADER)).isEqualTo("80");
		assertThat(headers.getFirst(X_FORWARDED_PROTO_HEADER)).isEqualTo("http");
	}

	@Test
	public void appendsValues() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(X_FORWARDED_FOR_HEADER, "192.168.0.2")
			.header(X_FORWARDED_HOST_HEADER, "example.com")
			.header(X_FORWARDED_PORT_HEADER, "443")
			.header(X_FORWARDED_PROTO_HEADER, "https")
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).contains(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).isEqualTo("192.168.0.2,10.0.0.1");
		assertThat(headers.getFirst(X_FORWARDED_HOST_HEADER)).isEqualTo("example.com,localhost:8080");
		assertThat(headers.getFirst(X_FORWARDED_PORT_HEADER)).isEqualTo("443,8080");
		assertThat(headers.getFirst(X_FORWARDED_PROTO_HEADER)).isEqualTo("https,http");
	}

	@Test
	public void appendDisabled() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(X_FORWARDED_FOR_HEADER, "192.168.0.2")
			.header(X_FORWARDED_HOST_HEADER, "example.com")
			.header(X_FORWARDED_PORT_HEADER, "443")
			.header(X_FORWARDED_PROTO_HEADER, "https")
			.header(X_FORWARDED_PREFIX_HEADER, "/prefix")
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);
		filter.setForAppend(false);
		filter.setHostAppend(false);
		filter.setPortAppend(false);
		filter.setProtoAppend(false);
		filter.setPrefixAppend(false);

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).contains(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER, X_FORWARDED_PREFIX_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).isEqualTo("10.0.0.1");
		assertThat(headers.getFirst(X_FORWARDED_HOST_HEADER)).isEqualTo("localhost:8080");
		assertThat(headers.getFirst(X_FORWARDED_PORT_HEADER)).isEqualTo("8080");
		assertThat(headers.getFirst(X_FORWARDED_PROTO_HEADER)).isEqualTo("http");
		assertThat(headers.getFirst(X_FORWARDED_PREFIX_HEADER)).isEqualTo("/prefix");
	}

	@Test
	public void prefixToInfer() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("https://originalhost:8080/prefix/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);
		filter.setPrefixAppend(true);
		filter.setPrefixEnabled(true);

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		LinkedHashSet<URI> originalUris = new LinkedHashSet<>();
		originalUris.add(UriComponentsBuilder.fromUriString("https://originalhost:8080/prefix/get/").build().toUri()); // trailing
																														// slash
		exchange.getAttributes().put(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, originalUris);
		URI requestUri = UriComponentsBuilder.fromUriString("https://routedservice:8090/get").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUri);

		HttpHeaders headers = filter.filter(request.getHeaders(), exchange);

		assertThat(headers.headerNames()).contains(X_FORWARDED_PREFIX_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_PREFIX_HEADER)).isEqualTo("/prefix");
	}

	@Test
	public void prefixToInferWhenEqualsResource() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("https://originalhost:8080/resource/resource/")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);
		filter.setPrefixAppend(true);
		filter.setPrefixEnabled(true);

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		LinkedHashSet<URI> originalUris = new LinkedHashSet<>();
		originalUris
			.add(UriComponentsBuilder.fromUriString("https://originalhost:8080/resource/resource/").build().toUri()); // trailing
																														// slash
		exchange.getAttributes().put(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, originalUris);
		URI requestUri = UriComponentsBuilder.fromUriString("https://routedservice:8090/resource").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUri);

		HttpHeaders headers = filter.filter(request.getHeaders(), exchange);

		assertThat(headers.headerNames()).contains(X_FORWARDED_PREFIX_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_PREFIX_HEADER)).isEqualTo("/resource");
	}

	@Test
	public void prefixAddedWithoutTrailingSlash() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("https://originalhost:8080/foo/bar")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);
		filter.setPrefixAppend(true);
		filter.setPrefixEnabled(true);

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		LinkedHashSet<URI> originalUris = new LinkedHashSet<>();
		originalUris.add(UriComponentsBuilder.fromUriString("https://originalhost:8080/foo/bar").build().toUri());
		exchange.getAttributes().put(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, originalUris);
		URI requestUri = UriComponentsBuilder.fromUriString("https://routedservice:8090/").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUri);

		HttpHeaders headers = filter.filter(request.getHeaders(), exchange);

		assertThat(headers.getFirst(X_FORWARDED_PREFIX_HEADER)).isEqualTo("/foo/bar");
	}

	@Test
	public void noPrefixToInfer() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("https://originalhost:8080/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);
		filter.setPrefixAppend(true);
		filter.setPrefixEnabled(true);
		filter.setForEnabled(false);
		filter.setHostEnabled(false);
		filter.setPortEnabled(false);
		filter.setProtoEnabled(false);

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		LinkedHashSet<URI> originalUris = new LinkedHashSet<>();
		originalUris.add(UriComponentsBuilder.fromUriString("https://originalhost:8080/get/").build().toUri());
		exchange.getAttributes().put(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, originalUris);
		URI requestUri = UriComponentsBuilder.fromUriString("https://routedservice:8090/get").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUri);

		HttpHeaders headers = filter.filter(request.getHeaders(), exchange);

		assertThat(headers.isEmpty()).isTrue();
	}

	@Test
	public void routedPathInRequestPathButNotPrefix() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("https://originalhost:8080/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);
		filter.setPrefixAppend(true);
		filter.setPrefixEnabled(true);
		filter.setForEnabled(false);
		filter.setHostEnabled(false);
		filter.setPortEnabled(false);
		filter.setProtoEnabled(false);

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		LinkedHashSet<URI> originalUris = new LinkedHashSet<>();
		originalUris.add(UriComponentsBuilder.fromUriString("https://originalhost:8080/one/two/three").build().toUri());
		exchange.getAttributes().put(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, originalUris);
		URI requestUri = UriComponentsBuilder.fromUriString("https://routedservice:8090/two").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUri);

		HttpHeaders headers = filter.filter(request.getHeaders(), exchange);

		assertThat(headers.isEmpty()).isTrue();
	}

	@Test
	public void allDisabled() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);
		filter.setForEnabled(false);
		filter.setHostEnabled(false);
		filter.setPortEnabled(false);
		filter.setProtoEnabled(false);
		filter.setPrefixEnabled(false);

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.isEmpty()).isTrue();
	}

	@Test
	public void allowDuplicateEntriesInXForwardedForHeader() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(X_FORWARDED_FOR_HEADER, "10.0.0.1")
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).contains(X_FORWARDED_FOR_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).isEqualTo("10.0.0.1,10.0.0.1");
	}

	@Test
	public void nullValuesSkipped() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(X_FORWARDED_FOR_HEADER, "10.0.0.1")
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter(ALLOW_ALL_REGEX);

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).doesNotContain(X_FORWARDED_PROTO_HEADER, X_FORWARDED_HOST_HEADER);
	}

	@Test
	public void trustedProxiesConditionMatches() {
		new ReactiveWebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, SslAutoConfiguration.class,
					NettyReactiveWebServerAutoConfiguration.class, GatewayAutoConfiguration.class))
			.withPropertyValues(GatewayProperties.PREFIX + ".trusted-proxies=11\\.0\\.0\\..*")
			.run(context -> {
				assertThat(context).hasSingleBean(XForwardedHeadersFilter.class);
			});
	}

	@Test
	public void trustedProxiesConditionDoesNotMatch() {
		new ReactiveWebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, SslAutoConfiguration.class,
					NettyReactiveWebServerAutoConfiguration.class, GatewayAutoConfiguration.class))
			.run(context -> {
				assertThat(context).doesNotHaveBean(XForwardedHeadersFilter.class);
			});
	}

	@Test
	public void emptyTrustedProxiesFails() {
		Assertions.assertThatThrownBy(() -> new XForwardedHeadersFilter(""))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void xForwardedHeadersNotTrusted() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(HttpHeaders.HOST, "myhost")
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter("11\\.0\\.0\\..*");

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).doesNotContain(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);
	}

	// : verify that existing x-forwarded-* headers are not forwarded
	// if x-forwarded-for is not trusted
	@Test
	public void untrustedXForwardedForNotAppended() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(HttpHeaders.HOST, "myhost")
			.header(X_FORWARDED_FOR_HEADER, "127.0.0.1")
			.header(X_FORWARDED_FOR_HEADER, "10.0.0.10")
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter("10\\.0\\.0\\..*");

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).contains(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).doesNotContain("127.0.0.1")
			.contains("10.0.0.1", "10.0.0.10");
	}

	@Test
	public void remoteAdddressIsNullUnTrustedProxyNotAppended() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
			.header(HttpHeaders.HOST, "myhost")
			.header(X_FORWARDED_FOR_HEADER, "127.0.0.1")
			.build();

		XForwardedHeadersFilter filter = new XForwardedHeadersFilter("10\\.0\\.0\\..*");

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).contains(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).doesNotContain("127.0.0.1");
	}

}
