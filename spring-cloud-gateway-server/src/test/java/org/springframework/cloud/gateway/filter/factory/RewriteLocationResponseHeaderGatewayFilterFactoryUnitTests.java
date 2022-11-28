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

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RewriteLocationResponseHeaderGatewayFilterFactory.Config;
import org.springframework.cloud.gateway.filter.factory.RewriteLocationResponseHeaderGatewayFilterFactory.StripVersion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RewriteLocationResponseHeaderGatewayFilterFactoryUnitTests {

	@InjectMocks
	private RewriteLocationResponseHeaderGatewayFilterFactory filter;

	@Mock
	private ServerWebExchange exchange;

	@Mock
	private ServerHttpRequest request;

	@Mock
	private ServerHttpResponse response;

	@Mock
	private HttpHeaders requestHeaders;

	@Mock
	private HttpHeaders responseHeaders;

	private URI uri;

	private Config config;

	@BeforeEach
	public void setUp() {
		filter = new RewriteLocationResponseHeaderGatewayFilterFactory();
		Mockito.when(exchange.getRequest()).thenReturn(request);
		Mockito.when(exchange.getResponse()).thenReturn(response);
		Mockito.when(request.getHeaders()).thenReturn(requestHeaders);
		Mockito.when(response.getHeaders()).thenReturn(responseHeaders);
		config = new Config();
	}

	private void setupTest(String location, String host, String path) {
		Mockito.when(responseHeaders.getFirst(HttpHeaders.LOCATION)).thenReturn(location);
		Mockito.when(requestHeaders.getFirst(HttpHeaders.HOST)).thenReturn(host);
		uri = URI.create("http://" + host + path);
		Mockito.when(request.getURI()).thenReturn(uri);
	}

	@Test
	public void rewriteLocationNullLocation() {
		setupTest(null, "host", "/path");
		filter.rewriteLocation(exchange, config);
		Mockito.verify(responseHeaders, Mockito.never()).set(Mockito.anyString(), Mockito.anyString());
	}

	@Test
	public void rewriteLocationNullHost() {
		setupTest("location", null, "/path");
		filter.rewriteLocation(exchange, config);
		Mockito.verify(responseHeaders, Mockito.never()).set(Mockito.anyString(), Mockito.anyString());
	}

	@Test
	public void rewriteLocation() {
		setupTest("location", "host", "/path");
		filter.rewriteLocation(exchange, config);
		Mockito.verify(responseHeaders).set(Mockito.eq("Location"), Mockito.eq("location"));
	}

	@Test
	public void rewriteLocationCustomHeaderName() {
		setupTest("location", "host", "/path");
		Mockito.when(responseHeaders.getFirst("Link")).thenReturn("link");
		config.setLocationHeaderName("Link");
		filter.rewriteLocation(exchange, config);
		Mockito.verify(responseHeaders).set(Mockito.eq("Link"), Mockito.eq("link"));
	}

	@Test
	public void rewriteLocationCustomHostValue() {
		setupTest("https://replaceme/some/path", "host", "/some/path");
		config.setHostValue("different.host");
		filter.rewriteLocation(exchange, config);
		Mockito.verify(responseHeaders).set(Mockito.eq("Location"), Mockito.eq("https://different.host/some/path"));
	}

	@Test
	public void rewriteLocationCustomProtocols() {
		setupTest("https://replaceme/some/path", "host", "/some/path");
		config.setProtocols("gopher|whatever");
		filter.rewriteLocation(exchange, config);
		Mockito.verify(responseHeaders).set(Mockito.eq("Location"), Mockito.eq("https://replaceme/some/path"));
	}

	@Test
	public void fixedLocationVersionedAlwaysStrip() {
		String location = "https://backend-url.example.com:443/v1/path/to/riches";
		String host = "example.com:443";
		String path = "/v1/path/to/riches";
		setupTest(location, host, path);
		assertThat(filter.fixedLocation(location, host, path, StripVersion.ALWAYS_STRIP, config.getHostPortPattern(),
				config.getHostPortVersionPattern())).isEqualTo("https://example.com:443/path/to/riches");
	}

	@Test
	public void fixedLocationVersionedStripAsInRequest() {
		String location = "https://backend-url.example.com:443/v1/path/to/riches";
		String host = "example.com:443";
		String path = "/v1/path/to/riches";
		setupTest(location, host, path);
		assertThat(filter.fixedLocation(location, host, path, StripVersion.AS_IN_REQUEST, config.getHostPortPattern(),
				config.getHostPortVersionPattern())).isEqualTo("https://example.com:443/v1/path/to/riches");
	}

	@Test
	public void fixedLocationVersionedDontStrip() {
		String location = "https://backend-url.example.com:443/v1/path/to/riches";
		String host = "example.com:443";
		String path = "/v1/path/to/riches";
		setupTest(location, host, path);
		assertThat(filter.fixedLocation(location, host, path, StripVersion.NEVER_STRIP, config.getHostPortPattern(),
				config.getHostPortVersionPattern())).isEqualTo("https://example.com:443/v1/path/to/riches");
	}

	@Test
	public void fixedLocationUnversionedAlwaysStrip() {
		String location = "https://backend-url.example.com:443/v2/path/to/riches";
		String host = "api.example.com:443";
		String path = "/path/to/riches";
		setupTest(location, host, path);
		assertThat(filter.fixedLocation(location, host, path, StripVersion.ALWAYS_STRIP, config.getHostPortPattern(),
				config.getHostPortVersionPattern())).isEqualTo("https://api.example.com:443/path/to/riches");
	}

	@Test
	public void fixedLocationUnversionedStripAsInRequest() {
		String location = "https://backend-url.example.com:443/v2/path/to/riches";
		String host = "api.example.com:443";
		String path = "/path/to/riches";
		setupTest(location, host, path);
		assertThat(filter.fixedLocation(location, host, path, StripVersion.AS_IN_REQUEST, config.getHostPortPattern(),
				config.getHostPortVersionPattern())).isEqualTo("https://api.example.com:443/path/to/riches");
	}

	@Test
	public void fixedLocationUnversionedDontStrip() {
		String location = "https://backend-url.example.com:443/v2/path/to/riches";
		String host = "api.example.com:443";
		String path = "/path/to/riches";
		setupTest(location, host, path);
		assertThat(filter.fixedLocation(location, host, path, StripVersion.NEVER_STRIP, config.getHostPortPattern(),
				config.getHostPortVersionPattern())).isEqualTo("https://api.example.com:443/v2/path/to/riches");
	}

	@Test
	public void fixedLocationNoPort() {
		String location = "https://backend-url.example.com/v2/path/to/riches";
		String host = "api.example.com:443";
		String path = "/path/to/riches";
		setupTest(location, host, path);
		assertThat(filter.fixedLocation(location, host, path, StripVersion.AS_IN_REQUEST, config.getHostPortPattern(),
				config.getHostPortVersionPattern())).isEqualTo("https://api.example.com:443/path/to/riches");
	}

	@Test
	public void toStringFormat() {
		// @formatter:off
		Config config = new Config().setStripVersion(StripVersion.ALWAYS_STRIP)
				.setLocationHeaderName("mylocation")
				.setHostValue("myhost")
				.setProtocols("myproto");
		GatewayFilter filter = new RewriteLocationResponseHeaderGatewayFilterFactory()
				.apply(config);
		assertThat(filter.toString())
				.contains("ALWAYS_STRIP")
				.contains("mylocation")
				.contains("myhost")
				.contains("myproto");
		// @formatter:on

	}

}
