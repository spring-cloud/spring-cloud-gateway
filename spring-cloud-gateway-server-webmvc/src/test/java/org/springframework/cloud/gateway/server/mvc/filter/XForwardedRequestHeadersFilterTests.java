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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.Collections;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.GatewayServerMvcAutoConfiguration;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateAutoConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_FOR_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_HOST_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_PORT_HEADER;
import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilter.X_FORWARDED_PROTO_HEADER;

/**
 * @author Spencer Gibb
 */
public class XForwardedRequestHeadersFilterTests {

	@Test
	public void remoteAddressIsNull() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get")
			.header(HttpHeaders.HOST, "myhost")
			.buildRequest(null);
		servletRequest.setRemoteAddr(null);
		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		XForwardedRequestHeadersFilter filter = new XForwardedRequestHeadersFilter(
				new XForwardedRequestHeadersFilterProperties(), ".*");

		HttpHeaders headers = filter.apply(request.headers().asHttpHeaders(), request);

		assertThat(headers.headerNames()).doesNotContain(X_FORWARDED_FOR_HEADER)
			.contains(X_FORWARDED_HOST_HEADER, X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_HOST_HEADER)).isEqualTo("myhost");
		assertThat(headers.getFirst(X_FORWARDED_PORT_HEADER)).isEqualTo("80");
		assertThat(headers.getFirst(X_FORWARDED_PROTO_HEADER)).isEqualTo("http");
	}

	@Test
	public void trustedProxiesConditionMatches() {
		new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class, RestClientAutoConfiguration.class,
					SslAutoConfiguration.class, TomcatServletWebServerAutoConfiguration.class,
					GatewayServerMvcAutoConfiguration.class, FilterAutoConfiguration.class,
					PredicateAutoConfiguration.class))
			.withPropertyValues(GatewayMvcProperties.PREFIX + ".trusted-proxies=11\\.0\\.0\\..*")
			.run(context -> {
				assertThat(context).hasSingleBean(XForwardedRequestHeadersFilter.class);
			});
	}

	@Test
	public void trustedProxiesConditionDoesNotMatch() {
		new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class, RestClientAutoConfiguration.class,
					SslAutoConfiguration.class, TomcatServletWebServerAutoConfiguration.class,
					GatewayServerMvcAutoConfiguration.class, FilterAutoConfiguration.class,
					PredicateAutoConfiguration.class))
			.run(context -> {
				assertThat(context).doesNotHaveBean(XForwardedRequestHeadersFilter.class);
			});
	}

	@Test
	public void emptyTrustedProxiesFails() {
		Assertions
			.assertThatThrownBy(
					() -> new XForwardedRequestHeadersFilter(new XForwardedRequestHeadersFilterProperties(), ""))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void xForwardedHeadersNotTrusted() throws Exception {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get")
			.remoteAddress("10.0.0.1:80")
			.header(HttpHeaders.HOST, "myhost")
			.buildRequest(null);
		servletRequest.setRemoteHost("10.0.0.1");
		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		XForwardedRequestHeadersFilter filter = new XForwardedRequestHeadersFilter(
				new XForwardedRequestHeadersFilterProperties(), "11\\.0\\.0\\..*");

		HttpHeaders headers = filter.apply(request.headers().asHttpHeaders(), request);

		assertThat(headers.headerNames()).doesNotContain(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);
	}

	// verify that existing forwarded header is not forwarded if not trusted
	@Test
	public void untrustedXForwardedForNotAppended() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get")
			.remoteAddress("10.0.0.1:80")
			.header(HttpHeaders.HOST, "myhost")
			.header(X_FORWARDED_FOR_HEADER, "127.0.0.1")
			.header(X_FORWARDED_FOR_HEADER, "10.0.0.10")
			.buildRequest(null);
		servletRequest.setRemoteHost("10.0.0.1");
		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		XForwardedRequestHeadersFilter filter = new XForwardedRequestHeadersFilter(
				new XForwardedRequestHeadersFilterProperties(), "10\\.0\\.0\\..*");

		HttpHeaders headers = filter.apply(request.headers().asHttpHeaders(), request);

		assertThat(headers.headerNames()).contains(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).doesNotContain("127.0.0.1")
			.contains("10.0.0.1", "10.0.0.10");
	}

	@Test
	public void remoteAdddressIsNullUnTrustedProxyNotAppended() {
		MockHttpServletRequest servletRequest = MockMvcRequestBuilders.get("http://localhost/get")
			.header(HttpHeaders.HOST, "myhost")
			.header(X_FORWARDED_FOR_HEADER, "127.0.0.1")
			.buildRequest(null);
		servletRequest.setRemoteAddr(null);
		ServerRequest request = ServerRequest.create(servletRequest, Collections.emptyList());

		XForwardedRequestHeadersFilter filter = new XForwardedRequestHeadersFilter(
				new XForwardedRequestHeadersFilterProperties(), "10\\.0\\.0\\..*");

		HttpHeaders headers = filter.apply(request.headers().asHttpHeaders(), request);

		assertThat(headers.headerNames()).contains(X_FORWARDED_FOR_HEADER, X_FORWARDED_HOST_HEADER,
				X_FORWARDED_PORT_HEADER, X_FORWARDED_PROTO_HEADER);

		assertThat(headers.getFirst(X_FORWARDED_FOR_HEADER)).doesNotContain("127.0.0.1");
	}

}
