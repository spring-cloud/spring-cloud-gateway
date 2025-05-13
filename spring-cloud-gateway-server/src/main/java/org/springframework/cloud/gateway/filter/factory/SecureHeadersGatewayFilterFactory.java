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
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * GatewayFilterFactory to provide a route filter that applies security headers to the
 * HTTP response. External configuration {@link SecureHeadersProperties} provides
 * opinionated defaults. Following the recommendations made in <a href=
 * "https://blog.appcanary.com/2017/http-security-headers.html">Http-Security-Headers</a>.
 * When opt-out headers are not disabled or explicitly configured, sensible defaults are
 * applied. Additionally, opt-in headers, such as Permissions-Policy, may be applied.
 *
 * @author Spencer Gibb, Thirunavukkarasu Ravichandran, Jörg Richter
 */
public class SecureHeadersGatewayFilterFactory
		extends AbstractGatewayFilterFactory<SecureHeadersGatewayFilterFactory.Config> {

	/**
	 * Xss-Protection header name.
	 */
	public static final String X_XSS_PROTECTION_HEADER = SecureHeadersProperties.X_XSS_PROTECTION_HEADER;

	/**
	 * Strict transport security header name.
	 */
	public static final String STRICT_TRANSPORT_SECURITY_HEADER = SecureHeadersProperties.STRICT_TRANSPORT_SECURITY_HEADER;

	/**
	 * Frame options header name.
	 */
	public static final String X_FRAME_OPTIONS_HEADER = SecureHeadersProperties.X_FRAME_OPTIONS_HEADER;

	/**
	 * Content-Type Options header name.
	 */
	public static final String X_CONTENT_TYPE_OPTIONS_HEADER = SecureHeadersProperties.X_CONTENT_TYPE_OPTIONS_HEADER;

	/**
	 * Referrer Policy header name.
	 */
	public static final String REFERRER_POLICY_HEADER = SecureHeadersProperties.REFERRER_POLICY_HEADER;

	/**
	 * Content-Security Policy header name.
	 */
	public static final String CONTENT_SECURITY_POLICY_HEADER = SecureHeadersProperties.CONTENT_SECURITY_POLICY_HEADER;

	/**
	 * Download Options header name.
	 */
	public static final String X_DOWNLOAD_OPTIONS_HEADER = SecureHeadersProperties.X_DOWNLOAD_OPTIONS_HEADER;

	/**
	 * Permitted Cross-Domain Policies header name.
	 */
	public static final String X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER = SecureHeadersProperties.X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER;

	private final SecureHeadersProperties properties;

	public SecureHeadersGatewayFilterFactory(SecureHeadersProperties properties) {
		super(Config.class);
		this.properties = properties;
	}

	/**
	 * Returns a GatewayFilter that applies security headers to the HTTP response.
	 * @param originalConfig the original security configuration
	 * @return a GatewayFilter instance that applies security headers to the HTTP response
	 */
	@Override
	public GatewayFilter apply(Config originalConfig) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

				HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
				Set<String> headersToAddToResponse = assembleHeaders(originalConfig, properties);

				Config config = originalConfig.withDefaults(properties);
				return chain.filter(exchange)
					.then(Mono
						.fromRunnable(() -> applySecurityHeaders(responseHeaders, headersToAddToResponse, config)));
			}

			@Override
			public String toString() {
				return filterToStringCreator(SecureHeadersGatewayFilterFactory.this).toString();
			}
		};
	}

	/**
	 * Applies security headers to the response using the given filter configuration.
	 * @param responseHeaders - the http headers of the response
	 * @param headersToAddToResponse - the security headers that are to be added to the
	 * response
	 * @param config - the security filter configuration
	 */
	private void applySecurityHeaders(HttpHeaders responseHeaders, Set<String> headersToAddToResponse, Config config) {

		addHeaderIfEnabled(responseHeaders, headersToAddToResponse, SecureHeadersProperties.X_XSS_PROTECTION_HEADER,
				config.getXssProtectionHeaderValue());

		addHeaderIfEnabled(responseHeaders, headersToAddToResponse,
				SecureHeadersProperties.STRICT_TRANSPORT_SECURITY_HEADER,
				config.getStrictTransportSecurityHeaderValue());

		addHeaderIfEnabled(responseHeaders, headersToAddToResponse, SecureHeadersProperties.X_FRAME_OPTIONS_HEADER,
				config.getFrameOptionsHeaderValue());

		addHeaderIfEnabled(responseHeaders, headersToAddToResponse,
				SecureHeadersProperties.X_CONTENT_TYPE_OPTIONS_HEADER, config.getContentTypeOptionsHeaderValue());

		addHeaderIfEnabled(responseHeaders, headersToAddToResponse, SecureHeadersProperties.REFERRER_POLICY_HEADER,
				config.getReferrerPolicyHeaderValue());

		addHeaderIfEnabled(responseHeaders, headersToAddToResponse,
				SecureHeadersProperties.CONTENT_SECURITY_POLICY_HEADER, config.getContentSecurityPolicyHeaderValue());

		addHeaderIfEnabled(responseHeaders, headersToAddToResponse, SecureHeadersProperties.X_DOWNLOAD_OPTIONS_HEADER,
				config.getDownloadOptionsHeaderValue());

		addHeaderIfEnabled(responseHeaders, headersToAddToResponse,
				SecureHeadersProperties.X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER,
				config.getPermittedCrossDomainPoliciesHeaderValue());

		String permissionPolicyHeaderValue = config.getPermissionPolicyHeaderValue();
		if (config.isRouteFilterConfigProvided()) {
			String routePermissionPolicyHeaderValue = config.getRoutePermissionsPolicyHeaderValue();
			if (routePermissionPolicyHeaderValue != null) {
				permissionPolicyHeaderValue = routePermissionPolicyHeaderValue;
			}
		}

		addHeaderIfEnabled(responseHeaders, headersToAddToResponse, SecureHeadersProperties.PERMISSIONS_POLICY_HEADER,
				permissionPolicyHeaderValue);
	}

	/**
	 * Assembles the set of security headers that are to be applied to the response - When
	 * route specific arguments are set, route specific headers are applied. - When no
	 * route specific arguments are set, global default headers are applied.
	 * @param config - the global / route configuration supplied
	 * @param properties - default security headers configuration provided
	 * @return set of security headers that are to be added to the response
	 */
	private Set<String> assembleHeaders(Config config, SecureHeadersProperties properties) {
		Set<String> headersToAddToResponse = new HashSet<>(properties.getDefaultHeaders());
		if (config.isRouteFilterConfigProvided()) {
			headersToAddToResponse.addAll(config.getRouteEnabledHeaders());
			headersToAddToResponse.removeAll(config.getRouteDisabledHeaders());
		}
		else {
			headersToAddToResponse.addAll(properties.getEnabledHeaders());
			headersToAddToResponse.removeAll(properties.getDisabledHeaders());
		}
		return headersToAddToResponse;
	}

	private void addHeaderIfEnabled(HttpHeaders headers, Set<String> headersToAdd, String headerName,
			String headerValue) {
		if (headersToAdd.contains(headerName.toLowerCase(Locale.ROOT))) {
			headers.addIfAbsent(headerName, headerValue);
		}
	}

	/**
	 * POJO for {@link SecureHeadersGatewayFilterFactory} filter configuration.
	 */
	public static class Config {

		private Set<String> routeEnabledHeaders = new HashSet<>();

		private Set<String> routeDisabledHeaders = new HashSet<>();

		private String routePermissionsPolicyHeaderValue;

		private boolean routeFilterConfigProvided;

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

			config.setEnable(routeEnabledHeaders);
			config.setDisable(routeDisabledHeaders);
			config.setPermissionsPolicy(routePermissionsPolicyHeaderValue);

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

		/**
		 * bind the route specific/opt-in header names to enable, in lower case.
		 */
		void setEnable(Set<String> enable) {
			if (enable != null) {
				this.routeFilterConfigProvided = true;
				this.routeEnabledHeaders = enable.stream()
					.map(String::toLowerCase)
					.collect(Collectors.toUnmodifiableSet());
			}
		}

		/**
		 * @return the route specific/opt-in header names to enable, in lower case.
		 */
		Set<String> getRouteEnabledHeaders() {
			return routeEnabledHeaders;
		}

		/**
		 * bind the route specific/opt-out header names to disable, in lower case.
		 */
		void setDisable(Set<String> disable) {
			if (disable != null) {
				this.routeFilterConfigProvided = true;
				this.routeDisabledHeaders = disable.stream()
					.map(String::toLowerCase)
					.collect(Collectors.toUnmodifiableSet());
			}
		}

		/**
		 * @return the route specific/opt-out header names to disable, in lower case
		 */
		Set<String> getRouteDisabledHeaders() {
			return routeDisabledHeaders;
		}

		/**
		 * @return the route specific/opt-out permission policies.
		 */
		String getRoutePermissionsPolicyHeaderValue() {
			return routePermissionsPolicyHeaderValue;
		}

		/**
		 * bind the route specific/opt-out permissions policy.
		 */
		void setPermissionsPolicy(String permissionsPolicy) {
			this.routeFilterConfigProvided = true;
			this.routePermissionsPolicyHeaderValue = permissionsPolicy;
		}

		/**
		 * @return flag whether route specific arguments were bound.
		 */
		boolean isRouteFilterConfigProvided() {
			return routeFilterConfigProvided;
		}

	}

}
