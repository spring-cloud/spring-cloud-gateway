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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Spencer Gibb, Thirunavukkarasu Ravichandran, Jörg Richter
 */
@ConfigurationProperties("spring.cloud.gateway.filter.secure-headers")
public class SecureHeadersProperties {

	/**
	 * Xss-Protection header name.
	 */
	public static final String X_XSS_PROTECTION_HEADER = "X-Xss-Protection";

	/**
	 * Xss-Protection header default.
	 */
	public static final String X_XSS_PROTECTION_HEADER_DEFAULT = "1 ; mode=block";

	/**
	 * Strict transport security header name.
	 */
	public static final String STRICT_TRANSPORT_SECURITY_HEADER = "Strict-Transport-Security";

	/**
	 * Strict transport security header default.
	 */
	public static final String STRICT_TRANSPORT_SECURITY_HEADER_DEFAULT = "max-age=631138519";

	/**
	 * Frame options header name.
	 */
	public static final String X_FRAME_OPTIONS_HEADER = "X-Frame-Options";

	/**
	 * Frame Options header default.
	 */
	public static final String X_FRAME_OPTIONS_HEADER_DEFAULT = "DENY";

	/**
	 * Content-Type Options header name.
	 */
	public static final String X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

	/**
	 * Content-Type Options header default.
	 */
	public static final String X_CONTENT_TYPE_OPTIONS_HEADER_DEFAULT = "nosniff";

	/**
	 * Referrer Policy header name.
	 */
	public static final String REFERRER_POLICY_HEADER = "Referrer-Policy";

	/**
	 * Referrer Policy header default.
	 */
	public static final String REFERRER_POLICY_HEADER_DEFAULT = "no-referrer";

	/**
	 * Content-Security Policy header name.
	 */
	public static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";

	/**
	 * Content-Security Policy header default.
	 */
	public static final String CONTENT_SECURITY_POLICY_HEADER_DEFAULT = "default-src 'self' https:; font-src 'self' https: data:; img-src 'self' https: data:; object-src 'none'; script-src https:; style-src 'self' https: 'unsafe-inline'";

	/**
	 * Download Options header name.
	 */
	public static final String X_DOWNLOAD_OPTIONS_HEADER = "X-Download-Options";

	/**
	 * Download Options header default.
	 */
	public static final String X_DOWNLOAD_OPTIONS_HEADER_DEFAULT = "noopen";

	/**
	 * Permitted Cross-Domain Policies header name.
	 */
	public static final String X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER = "X-Permitted-Cross-Domain-Policies";

	/**
	 * Permitted Cross-Domain Policies header default.
	 */
	public static final String X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER_DEFAULT = "none";

	/**
	 * Permissions Policy header name. Opt-In required by external configuration.
	 */
	public static final String PERMISSIONS_POLICY_HEADER = "Permissions-Policy";

	/**
	 * Permissions Policy header default. Opt-In by external configuration required,
	 * because the header default disables a comprehensive list of features.
	 */
	public static final String PERMISSIONS_POLICY_HEADER_OPT_IN_DEFAULT = "accelerometer=(), ambient-light-sensor=(), "
			+ "autoplay=(), battery=(), camera=(), cross-origin-isolated=(), display-capture=(), document-domain=(), "
			+ "encrypted-media=(), execution-while-not-rendered=(), execution-while-out-of-viewport=(), fullscreen=(), "
			+ "geolocation=(), gyroscope=(), keyboard-map=(), magnetometer=(), microphone=(), midi=(), "
			+ "navigation-override=(), payment=(), picture-in-picture=(), publickey-credentials-get=(), "
			+ "screen-wake-lock=(), sync-xhr=(), usb=(), web-share=(), xr-spatial-tracking=()";

	/**
	 * Default constructor for {@link SecureHeadersProperties}. Initializes the
	 * `defaultHeaders` set with a predefined list of security headers. The headers are
	 * transformed to lowercase for case-insensitive comparison.
	 **/
	public SecureHeadersProperties() {

		defaultHeaders = Stream.of(SecureHeadersProperties.X_XSS_PROTECTION_HEADER,
				SecureHeadersProperties.STRICT_TRANSPORT_SECURITY_HEADER,
				SecureHeadersProperties.X_FRAME_OPTIONS_HEADER, SecureHeadersProperties.X_CONTENT_TYPE_OPTIONS_HEADER,
				SecureHeadersProperties.REFERRER_POLICY_HEADER, SecureHeadersProperties.CONTENT_SECURITY_POLICY_HEADER,
				SecureHeadersProperties.X_DOWNLOAD_OPTIONS_HEADER,
				SecureHeadersProperties.X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER)
			.map(String::toLowerCase)
			.collect(Collectors.toUnmodifiableSet());

	}

	private String xssProtectionHeader = X_XSS_PROTECTION_HEADER_DEFAULT;

	private String strictTransportSecurity = STRICT_TRANSPORT_SECURITY_HEADER_DEFAULT;

	private String frameOptions = X_FRAME_OPTIONS_HEADER_DEFAULT;

	private String contentTypeOptions = X_CONTENT_TYPE_OPTIONS_HEADER_DEFAULT;

	private String referrerPolicy = REFERRER_POLICY_HEADER_DEFAULT;

	private String contentSecurityPolicy = CONTENT_SECURITY_POLICY_HEADER_DEFAULT;

	private String downloadOptions = X_DOWNLOAD_OPTIONS_HEADER_DEFAULT;

	private String permittedCrossDomainPolicies = X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER_DEFAULT;

	private String permissionsPolicy = PERMISSIONS_POLICY_HEADER_OPT_IN_DEFAULT;

	private final Set<String> defaultHeaders;

	private Set<String> enabledHeaders = new HashSet<>();

	private Set<String> disabledHeaders = new HashSet<>();

	public String getXssProtectionHeader() {
		return xssProtectionHeader;
	}

	public void setXssProtectionHeader(String xssProtectionHeader) {
		this.xssProtectionHeader = xssProtectionHeader;
	}

	public String getStrictTransportSecurity() {
		return strictTransportSecurity;
	}

	public void setStrictTransportSecurity(String strictTransportSecurity) {
		this.strictTransportSecurity = strictTransportSecurity;
	}

	public String getFrameOptions() {
		return frameOptions;
	}

	public void setFrameOptions(String frameOptions) {
		this.frameOptions = frameOptions;
	}

	public String getContentTypeOptions() {
		return contentTypeOptions;
	}

	public void setContentTypeOptions(String contentTypeOptions) {
		this.contentTypeOptions = contentTypeOptions;
	}

	public String getReferrerPolicy() {
		return referrerPolicy;
	}

	public void setReferrerPolicy(String referrerPolicy) {
		this.referrerPolicy = referrerPolicy;
	}

	public String getContentSecurityPolicy() {
		return contentSecurityPolicy;
	}

	public void setContentSecurityPolicy(String contentSecurityPolicy) {
		this.contentSecurityPolicy = contentSecurityPolicy;
	}

	public String getDownloadOptions() {
		return downloadOptions;
	}

	public void setDownloadOptions(String downloadOptions) {
		this.downloadOptions = downloadOptions;
	}

	public String getPermittedCrossDomainPolicies() {
		return permittedCrossDomainPolicies;
	}

	public void setPermittedCrossDomainPolicies(String permittedCrossDomainPolicies) {
		this.permittedCrossDomainPolicies = permittedCrossDomainPolicies;
	}

	public String getPermissionsPolicy() {
		return permissionsPolicy;
	}

	public void setPermissionsPolicy(String permissionsPolicy) {
		this.permissionsPolicy = permissionsPolicy;
	}

	/**
	 * @return the default/opt-out header names to disable
	 */
	public List<String> getDisable() {
		return disabledHeaders.stream().toList();
	}

	/**
	 * Binds the list of default/opt-out header names to disable, transforms them into a
	 * lowercase set. This is to ensure case-insensitive comparison.
	 * @param disable - list of default/opt-out header names to disable
	 */
	public void setDisable(List<String> disable) {
		if (disable != null) {
			disabledHeaders = disable.stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
		}
	}

	/**
	 * @return the opt-in header names to enable
	 */
	public Set<String> getEnabledHeaders() {
		return enabledHeaders;
	}

	/**
	 * Binds the list of default/opt-out header names to enable, transforms them into a
	 * lowercase set. This is to ensure case-insensitive comparison.
	 * @param enable - list of default/opt-out header enable
	 */
	public void setEnable(List<String> enable) {
		if (enable != null) {
			enabledHeaders = enable.stream().map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
		}
	}

	/**
	 * @return the default/opt-out header names to disable
	 */
	public Set<String> getDisabledHeaders() {
		return disabledHeaders;
	}

	/**
	 * @return the default/opt-out header names to apply
	 */
	public Set<String> getDefaultHeaders() {
		return defaultHeaders;
	}

	@Override
	public String toString() {
		return "SecureHeadersProperties{" + "xssProtectionHeader='" + xssProtectionHeader + '\''
				+ ", strictTransportSecurity='" + strictTransportSecurity + '\'' + ", frameOptions='" + frameOptions
				+ '\'' + ", contentTypeOptions='" + contentTypeOptions + '\'' + ", referrerPolicy='" + referrerPolicy
				+ '\'' + ", contentSecurityPolicy='" + contentSecurityPolicy + '\'' + ", downloadOptions='"
				+ downloadOptions + '\'' + ", permittedCrossDomainPolicies='" + permittedCrossDomainPolicies + '\''
				+ ", permissionsPolicy='" + permissionsPolicy + '\'' + ", defaultHeaders=" + defaultHeaders
				+ ", enable=" + enabledHeaders + ", disable=" + disabledHeaders + '}';
	}

}
