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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.CONTENT_SECURITY_POLICY_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.Config;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.REFERRER_POLICY_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.STRICT_TRANSPORT_SECURITY_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.X_CONTENT_TYPE_OPTIONS_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.X_DOWNLOAD_OPTIONS_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.X_FRAME_OPTIONS_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.X_XSS_PROTECTION_HEADER;

/**
 * @author Thirunavukkarasu Ravichandran
 */
public class SecureHeadersGatewayFilterFactoryUnitTests {

	private GatewayFilter filter;

	private ServerWebExchange exchange;

	private GatewayFilterChain filterChain;

	private ArgumentCaptor<ServerWebExchange> captor;

	@BeforeEach
	public void setUp() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost").build();
		exchange = MockServerWebExchange.from(request);
		filterChain = mock(GatewayFilterChain.class);
		captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

	}

	@Test
	public void addAllHeadersIfNothingIsDisabled() {
		SecureHeadersGatewayFilterFactory filterFactory = new SecureHeadersGatewayFilterFactory(
				new SecureHeadersProperties());
		filter = filterFactory.apply(new Config());

		filter.filter(exchange, filterChain).block();

		ServerHttpResponse response = exchange.getResponse();
		assertThat(response.getHeaders()).containsKeys(X_XSS_PROTECTION_HEADER, STRICT_TRANSPORT_SECURITY_HEADER,
				X_FRAME_OPTIONS_HEADER, X_CONTENT_TYPE_OPTIONS_HEADER, REFERRER_POLICY_HEADER,
				CONTENT_SECURITY_POLICY_HEADER, X_DOWNLOAD_OPTIONS_HEADER, X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER);
	}

	@Test
	public void doNotAddDisabledHeaders() {
		SecureHeadersProperties properties = new SecureHeadersProperties();
		properties.setDisable(asList("x-xss-protection", "strict-transport-security", "x-frame-options",
				"x-content-type-options", "referrer-policy", "content-security-policy", "x-download-options",
				"x-permitted-cross-domain-policies"));

		SecureHeadersGatewayFilterFactory filterFactory = new SecureHeadersGatewayFilterFactory(properties);
		filter = filterFactory.apply(new Config());

		filter.filter(exchange, filterChain).block();

		ServerHttpResponse response = captor.getValue().getResponse();
		assertThat(response.getHeaders()).doesNotContainKeys(X_XSS_PROTECTION_HEADER, STRICT_TRANSPORT_SECURITY_HEADER,
				X_FRAME_OPTIONS_HEADER, X_CONTENT_TYPE_OPTIONS_HEADER, REFERRER_POLICY_HEADER,
				CONTENT_SECURITY_POLICY_HEADER, X_DOWNLOAD_OPTIONS_HEADER, X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER);

	}

	@Test
	public void overrideSomeHeaders() {
		SecureHeadersProperties properties = new SecureHeadersProperties();
		SecureHeadersGatewayFilterFactory filterFactory = new SecureHeadersGatewayFilterFactory(
				new SecureHeadersProperties());
		Config config = new Config();
		config.setStrictTransportSecurity("max-age=65535");
		config.setReferrerPolicy("referrer");
		filter = filterFactory.apply(config);

		filter.filter(exchange, filterChain).block();

		ServerHttpResponse response = exchange.getResponse();
		assertThat(response.getHeaders()).containsKeys(X_XSS_PROTECTION_HEADER, STRICT_TRANSPORT_SECURITY_HEADER,
				X_FRAME_OPTIONS_HEADER, X_CONTENT_TYPE_OPTIONS_HEADER, REFERRER_POLICY_HEADER,
				CONTENT_SECURITY_POLICY_HEADER, X_DOWNLOAD_OPTIONS_HEADER, X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER);

		assertThat(response.getHeaders().get(STRICT_TRANSPORT_SECURITY_HEADER)).containsOnly("max-age=65535");
		assertThat(response.getHeaders().get(REFERRER_POLICY_HEADER)).containsOnly("referrer");

		assertThat(response.getHeaders().get(X_XSS_PROTECTION_HEADER))
				.containsOnly(properties.getXssProtectionHeader());
		assertThat(response.getHeaders().get(X_FRAME_OPTIONS_HEADER)).containsOnly(properties.getFrameOptions());
		assertThat(response.getHeaders().get(X_CONTENT_TYPE_OPTIONS_HEADER))
				.containsOnly(properties.getContentTypeOptions());
		assertThat(response.getHeaders().get(CONTENT_SECURITY_POLICY_HEADER))
				.containsOnly(properties.getContentSecurityPolicy());
		assertThat(response.getHeaders().get(X_DOWNLOAD_OPTIONS_HEADER)).containsOnly(properties.getDownloadOptions());
		assertThat(response.getHeaders().get(X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER))
				.containsOnly(properties.getPermittedCrossDomainPolicies());

	}

	@Test
	public void doesNotDuplicateHeaders() {
		String originalHeaderValue = "original-header-value";
		SecureHeadersGatewayFilterFactory filterFactory = new SecureHeadersGatewayFilterFactory(
				new SecureHeadersProperties());
		Config config = new Config();

		String[] headers = { X_XSS_PROTECTION_HEADER, STRICT_TRANSPORT_SECURITY_HEADER, X_FRAME_OPTIONS_HEADER,
				X_CONTENT_TYPE_OPTIONS_HEADER, REFERRER_POLICY_HEADER, CONTENT_SECURITY_POLICY_HEADER,
				X_DOWNLOAD_OPTIONS_HEADER, X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER };

		for (String header : headers) {
			filter = filterFactory.apply(config);

			MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost").build();
			exchange = MockServerWebExchange.from(request);
			exchange.getResponse().getHeaders().set(header, originalHeaderValue);

			filter.filter(exchange, filterChain).block();

			ServerHttpResponse response = captor.getValue().getResponse();
			assertThat(response.getHeaders().get(header)).containsOnly(originalHeaderValue);
		}
	}

	@Test
	public void toStringFormat() {
		GatewayFilter filter = new SecureHeadersGatewayFilterFactory(new SecureHeadersProperties()).apply(new Config());
		Assertions.assertThat(filter.toString()).contains("SecureHeaders");
	}

}
