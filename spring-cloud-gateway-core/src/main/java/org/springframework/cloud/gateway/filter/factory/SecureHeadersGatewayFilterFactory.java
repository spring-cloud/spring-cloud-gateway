/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * https://blog.appcanary.com/2017/http-security-headers.html.
 *
 * @author Spencer Gibb, Thirunavukkarasu Ravichandran
 */
public class SecureHeadersGatewayFilterFactory extends AbstractGatewayFilterFactory {

	/**
	 * Xss-Protection header name.
	 */
	public static final String X_XSS_PROTECTION_HEADER = "X-Xss-Protection";

	/**
	 * Strict transport security header name.
	 */
	public static final String STRICT_TRANSPORT_SECURITY_HEADER = "Strict-Transport-Security";

	/**
	 * Frame options header name.
	 */
	public static final String X_FRAME_OPTIONS_HEADER = "X-Frame-Options";

	/**
	 * Content-Type Options header name.
	 */
	public static final String X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

	/**
	 * Referrer Policy header name.
	 */
	public static final String REFERRER_POLICY_HEADER = "Referrer-Policy";

	/**
	 * Content-Security Policy header name.
	 */
	public static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";

	/**
	 * Download Options header name.
	 */
	public static final String X_DOWNLOAD_OPTIONS_HEADER = "X-Download-Options";

	/**
	 * Permitted Cross-Domain Policies header name.
	 */
	public static final String X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER = "X-Permitted-Cross-Domain-Policies";

	private final SecureHeadersProperties properties;

	public SecureHeadersGatewayFilterFactory(SecureHeadersProperties properties) {
		this.properties = properties;
	}

	@Override
	public GatewayFilter apply(Object config) {
		// TODO: allow args to override properties

		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange,
					GatewayFilterChain chain) {
				HttpHeaders headers = exchange.getResponse().getHeaders();

				List<String> disabled = properties.getDisable();

				if (isEnabled(disabled, X_XSS_PROTECTION_HEADER)) {
					headers.add(X_XSS_PROTECTION_HEADER,
							properties.getXssProtectionHeader());
				}

				if (isEnabled(disabled, STRICT_TRANSPORT_SECURITY_HEADER)) {
					headers.add(STRICT_TRANSPORT_SECURITY_HEADER,
							properties.getStrictTransportSecurity());
				}

				if (isEnabled(disabled, X_FRAME_OPTIONS_HEADER)) {
					headers.add(X_FRAME_OPTIONS_HEADER, properties.getFrameOptions());
				}

				if (isEnabled(disabled, X_CONTENT_TYPE_OPTIONS_HEADER)) {
					headers.add(X_CONTENT_TYPE_OPTIONS_HEADER,
							properties.getContentTypeOptions());
				}

				if (isEnabled(disabled, REFERRER_POLICY_HEADER)) {
					headers.add(REFERRER_POLICY_HEADER, properties.getReferrerPolicy());
				}

				if (isEnabled(disabled, CONTENT_SECURITY_POLICY_HEADER)) {
					headers.add(CONTENT_SECURITY_POLICY_HEADER,
							properties.getContentSecurityPolicy());
				}

				if (isEnabled(disabled, X_DOWNLOAD_OPTIONS_HEADER)) {
					headers.add(X_DOWNLOAD_OPTIONS_HEADER,
							properties.getDownloadOptions());
				}

				if (isEnabled(disabled, X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER)) {
					headers.add(X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER,
							properties.getPermittedCrossDomainPolicies());
				}

				return chain.filter(exchange);
			}

			@Override
			public String toString() {
				return filterToStringCreator(SecureHeadersGatewayFilterFactory.this)
						.toString();
			}
		};
	}

	private boolean isEnabled(List<String> disabledHeaders, String header) {
		return !disabledHeaders.contains(header.toLowerCase());
	}

}
