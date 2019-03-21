/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpHeaders;

/**
 * https://blog.appcanary.com/2017/http-security-headers.html
 * @author Spencer Gibb
 */
public class SecureHeadersGatewayFilterFactory extends AbstractGatewayFilterFactory {

	public static final String X_XSS_PROTECTION_HEADER = "X-Xss-Protection";
	public static final String STRICT_TRANSPORT_SECURITY_HEADER = "Strict-Transport-Security";
	public static final String X_FRAME_OPTIONS_HEADER = "X-Frame-Options";
	public static final String X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";
	public static final String REFERRER_POLICY_HEADER = "Referrer-Policy";
	public static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";
	public static final String X_DOWNLOAD_OPTIONS_HEADER = "X-Download-Options";
	public static final String X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER = "X-Permitted-Cross-Domain-Policies";

	private final SecureHeadersProperties properties;

	public SecureHeadersGatewayFilterFactory(SecureHeadersProperties properties) {
		this.properties = properties;
	}

	@Override
	public GatewayFilter apply(Object config) {
		//TODO: allow args to override properties

		return (exchange, chain) -> {
			HttpHeaders headers = exchange.getResponse().getHeaders();

			//TODO: allow header to be disabled
			headers.add(X_XSS_PROTECTION_HEADER, properties.getXssProtectionHeader());
			headers.add(STRICT_TRANSPORT_SECURITY_HEADER, properties.getStrictTransportSecurity());
			headers.add(X_FRAME_OPTIONS_HEADER, properties.getFrameOptions());
			headers.add(X_CONTENT_TYPE_OPTIONS_HEADER, properties.getContentTypeOptions());
			headers.add(REFERRER_POLICY_HEADER, properties.getReferrerPolicy());
			headers.add(CONTENT_SECURITY_POLICY_HEADER, properties.getContentSecurityPolicy());
			headers.add(X_DOWNLOAD_OPTIONS_HEADER, properties.getDownloadOptions());
			headers.add(X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER, properties.getPermittedCrossDomainPolicies());

			return chain.filter(exchange);
		};
	}
}
