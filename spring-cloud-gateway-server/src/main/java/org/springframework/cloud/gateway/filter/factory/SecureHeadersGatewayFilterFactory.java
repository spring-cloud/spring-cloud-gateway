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

import java.util.HashSet;
import java.util.Set;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * GatewayFilterFactory to provide a route filter that applies security headers to the HTTP response.
 * External configuration {@link SecureHeadersProperties} provides opinionated defaults. Following the recommendations
 * made in <a href="https://blog.appcanary.com/2017/http-security-headers.html">Http-Security-Headers</a>.
 * When opt-out headers are not disabled or explicitly configured, sensible defaults are applied.
 * Additionally, opt-in headers, such as Permissions-Policy, may be applied.
 *
 * @author Spencer Gibb, Thirunavukkarasu Ravichandran, Jörg Richter
 */
public class SecureHeadersGatewayFilterFactory
		extends AbstractGatewayFilterFactory<SecureHeadersGatewayFilterFactory.Config> {

	private final SecureHeadersProperties properties;

	public SecureHeadersGatewayFilterFactory(SecureHeadersProperties properties) {
		super(Config.class);
		this.properties = properties;
	}

	/**
	 * Returns a GatewayFilter that applies security headers to the HTTP response.
	 *
	 * @param originalConfig the original security configuration
	 * @return a GatewayFilter instance that applies security headers to the HTTP response
	 */
	@Override
	public GatewayFilter apply(Config originalConfig) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				HttpHeaders headers = exchange.getResponse().getHeaders();

				Set<String> headersToAddToResponse = new HashSet<>(properties.getDefaultHeaders());
				headersToAddToResponse.addAll(properties.getEnabledHeaders());
				headersToAddToResponse.removeAll(properties.getDisabledHeaders());

				Config config = originalConfig.withDefaults(properties);

				return chain.filter(exchange).then(Mono.fromRunnable(() -> {
					if (isEnabled(headersToAddToResponse, SecureHeadersProperties.X_XSS_PROTECTION_HEADER)) {
						headers.addIfAbsent(SecureHeadersProperties.X_XSS_PROTECTION_HEADER,
								config.getXssProtectionHeaderValue());
					}

					if (isEnabled(headersToAddToResponse, SecureHeadersProperties.STRICT_TRANSPORT_SECURITY_HEADER)) {
						headers.addIfAbsent(SecureHeadersProperties.STRICT_TRANSPORT_SECURITY_HEADER,
								config.getStrictTransportSecurityHeaderValue());
					}

					if (isEnabled(headersToAddToResponse, SecureHeadersProperties.X_FRAME_OPTIONS_HEADER)) {
						headers.addIfAbsent(SecureHeadersProperties.X_FRAME_OPTIONS_HEADER,
								config.getFrameOptionsHeaderValue());
					}

					if (isEnabled(headersToAddToResponse, SecureHeadersProperties.X_CONTENT_TYPE_OPTIONS_HEADER)) {
						headers.addIfAbsent(SecureHeadersProperties.X_CONTENT_TYPE_OPTIONS_HEADER,
								config.getContentTypeOptionsHeaderValue());
					}

					if (isEnabled(headersToAddToResponse, SecureHeadersProperties.REFERRER_POLICY_HEADER)) {
						headers.addIfAbsent(SecureHeadersProperties.REFERRER_POLICY_HEADER,
								config.getReferrerPolicyHeaderValue());
					}

					if (isEnabled(headersToAddToResponse, SecureHeadersProperties.CONTENT_SECURITY_POLICY_HEADER)) {
						headers.addIfAbsent(SecureHeadersProperties.CONTENT_SECURITY_POLICY_HEADER,
								config.getContentSecurityPolicyHeaderValue());
					}

					if (isEnabled(headersToAddToResponse, SecureHeadersProperties.X_DOWNLOAD_OPTIONS_HEADER)) {
						headers.addIfAbsent(SecureHeadersProperties.X_DOWNLOAD_OPTIONS_HEADER,
								config.getDownloadOptionsHeaderValue());
					}

					if (isEnabled(headersToAddToResponse, SecureHeadersProperties.X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER)) {
						headers.addIfAbsent(SecureHeadersProperties.X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER,
								config.getPermittedCrossDomainPoliciesHeaderValue());
					}

					if (isEnabled(headersToAddToResponse, SecureHeadersProperties.PERMISSIONS_POLICY_HEADER)) {
						headers.addIfAbsent(SecureHeadersProperties.PERMISSIONS_POLICY_HEADER,
								config.getPermissionPolicyHeaderValue());
					}

				}));
			}

			@Override
			public String toString() {
				return filterToStringCreator(SecureHeadersGatewayFilterFactory.this).toString();
			}
		};
	}

	private boolean isEnabled(Set<String> headersToAddToResponse, String header) {
		return headersToAddToResponse.contains(header.toLowerCase());
	}

	public static class Config {

		private String xssProtectionHeaderValue;

		private String strictTransportSecurityHeaderValue;

		private String frameOptionsHeaderValue;

		private String contentTypeOptionsHeaderValue;

		private String referrerPolicyHeaderValue;

		private String contentSecurityPolicyHeaderValue;

		private String downloadOptionsHeaderValue;

		private String permittedCrossDomainPoliciesHeaderValue;

		private String permissionPolicyHeaderValue;

		public Config withDefaults(SecureHeadersProperties properties) {
			Config config = new Config();
			config.setXssProtectionHeaderValue(xssProtectionHeaderValue);
			config.setStrictTransportSecurityHeaderValue(strictTransportSecurityHeaderValue);
			config.setFrameOptionsHeaderValue(frameOptionsHeaderValue);
			config.setContentTypeOptionsHeaderValue(contentTypeOptionsHeaderValue);
			config.setReferrerPolicyHeaderValue(referrerPolicyHeaderValue);
			config.setContentSecurityPolicyHeaderValue(contentSecurityPolicyHeaderValue);
			config.setDownloadOptionsHeaderValue(downloadOptionsHeaderValue);
			config.setPermittedCrossDomainPoliciesHeaderValue(permittedCrossDomainPoliciesHeaderValue);
			config.setPermissionPolicyHeaderValue(permissionPolicyHeaderValue);

			if (config.xssProtectionHeaderValue == null) {
				config.xssProtectionHeaderValue = properties.getXssProtectionHeader();
			}

			if (config.strictTransportSecurityHeaderValue == null) {
				config.strictTransportSecurityHeaderValue = properties.getStrictTransportSecurity();
			}

			if (config.frameOptionsHeaderValue == null) {
				config.frameOptionsHeaderValue = properties.getFrameOptions();
			}

			if (config.contentTypeOptionsHeaderValue == null) {
				config.contentTypeOptionsHeaderValue = properties.getContentTypeOptions();
			}

			if (config.referrerPolicyHeaderValue == null) {
				config.referrerPolicyHeaderValue = properties.getReferrerPolicy();
			}

			if (config.contentSecurityPolicyHeaderValue == null) {
				config.contentSecurityPolicyHeaderValue = properties.getContentSecurityPolicy();
			}

			if (config.downloadOptionsHeaderValue == null) {
				config.downloadOptionsHeaderValue = properties.getDownloadOptions();
			}

			if (config.permittedCrossDomainPoliciesHeaderValue == null) {
				config.permittedCrossDomainPoliciesHeaderValue = properties.getPermittedCrossDomainPolicies();
			}

			if (config.permissionPolicyHeaderValue == null) {
				config.permissionPolicyHeaderValue = properties.getPermissionsPolicy();
			}

			return config;
		}

		public String getXssProtectionHeaderValue() {
			return xssProtectionHeaderValue;
		}

		public void setXssProtectionHeaderValue(String xssProtectionHeaderHeaderValue) {
			this.xssProtectionHeaderValue = xssProtectionHeaderHeaderValue;
		}

		public String getStrictTransportSecurityHeaderValue() {
			return strictTransportSecurityHeaderValue;
		}

		public void setStrictTransportSecurityHeaderValue(String strictTransportSecurityHeaderValue) {
			this.strictTransportSecurityHeaderValue = strictTransportSecurityHeaderValue;
		}

		public String getFrameOptionsHeaderValue() {
			return frameOptionsHeaderValue;
		}

		public void setFrameOptionsHeaderValue(String frameOptionsHeaderValue) {
			this.frameOptionsHeaderValue = frameOptionsHeaderValue;
		}

		public String getContentTypeOptionsHeaderValue() {
			return contentTypeOptionsHeaderValue;
		}

		public void setContentTypeOptionsHeaderValue(String contentTypeOptionsHeaderValue) {
			this.contentTypeOptionsHeaderValue = contentTypeOptionsHeaderValue;
		}

		public String getReferrerPolicyHeaderValue() {
			return referrerPolicyHeaderValue;
		}

		public void setReferrerPolicyHeaderValue(String referrerPolicyHeaderValue) {
			this.referrerPolicyHeaderValue = referrerPolicyHeaderValue;
		}

		public String getContentSecurityPolicyHeaderValue() {
			return contentSecurityPolicyHeaderValue;
		}

		public void setContentSecurityPolicyHeaderValue(String contentSecurityPolicyHeaderValue) {
			this.contentSecurityPolicyHeaderValue = contentSecurityPolicyHeaderValue;
		}

		public String getDownloadOptionsHeaderValue() {
			return downloadOptionsHeaderValue;
		}

		public void setDownloadOptionsHeaderValue(String downloadOptionHeaderValue) {
			this.downloadOptionsHeaderValue = downloadOptionsHeaderValue;
		}

		public String getPermittedCrossDomainPoliciesHeaderValue() {
			return permittedCrossDomainPoliciesHeaderValue;
		}

		public void setPermittedCrossDomainPoliciesHeaderValue(String permittedCrossDomainPoliciesHeaderValue) {
			this.permittedCrossDomainPoliciesHeaderValue = permittedCrossDomainPoliciesHeaderValue;
		}

		public String getPermissionPolicyHeaderValue() {
			return permissionPolicyHeaderValue;
		}

		public void setPermissionPolicyHeaderValue(String permissionPolicyHeaderValue) {
			this.permissionPolicyHeaderValue = permissionPolicyHeaderValue;
		}

	}

}
