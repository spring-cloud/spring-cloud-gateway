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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.gateway.filter.secure-headers")
public class SecureHeadersProperties {
	public static final String X_XSS_PROTECTION_HEADER_DEFAULT = "1 ; mode=block";
	public static final String STRICT_TRANSPORT_SECURITY_HEADER_DEFAULT = "max-age=631138519"; //; includeSubDomains preload")
	public static final String X_FRAME_OPTIONS_HEADER_DEFAULT = "DENY"; //SAMEORIGIN = ALLOW-FROM
	public static final String X_CONTENT_TYPE_OPTIONS_HEADER_DEFAULT = "nosniff";
	public static final String REFERRER_POLICY_HEADER_DEFAULT = "no-referrer"; //no-referrer-when-downgrade = origin = origin-when-cross-origin = same-origin = strict-origin = strict-origin-when-cross-origin = unsafe-url
	public static final String CONTENT_SECURITY_POLICY_HEADER_DEFAULT = "default-src 'self' https:; font-src 'self' https: data:; img-src 'self' https: data:; object-src 'none'; script-src https:; style-src 'self' https: 'unsafe-inline'";
	public static final String X_DOWNLOAD_OPTIONS_HEADER_DEFAULT = "noopen";
	public static final String X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER_DEFAULT = "none";

	private String xssProtectionHeader = X_XSS_PROTECTION_HEADER_DEFAULT;
	private String strictTransportSecurity = STRICT_TRANSPORT_SECURITY_HEADER_DEFAULT;
	private String frameOptions = X_FRAME_OPTIONS_HEADER_DEFAULT;
	private String contentTypeOptions = X_CONTENT_TYPE_OPTIONS_HEADER_DEFAULT;
	private String referrerPolicy = REFERRER_POLICY_HEADER_DEFAULT;
	private String contentSecurityPolicy = CONTENT_SECURITY_POLICY_HEADER_DEFAULT;
	private String downloadOptions = X_DOWNLOAD_OPTIONS_HEADER_DEFAULT;
	private String permittedCrossDomainPolicies = X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER_DEFAULT;

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

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("SecureHeadersProperties{");
		sb.append("xssProtectionHeader='").append(xssProtectionHeader).append('\'');
		sb.append(", strictTransportSecurity='").append(strictTransportSecurity).append('\'');
		sb.append(", frameOptions='").append(frameOptions).append('\'');
		sb.append(", contentTypeOptions='").append(contentTypeOptions).append('\'');
		sb.append(", referrerPolicy='").append(referrerPolicy).append('\'');
		sb.append(", contentSecurityPolicy='").append(contentSecurityPolicy).append('\'');
		sb.append(", downloadOptions='").append(downloadOptions).append('\'');
		sb.append(", permittedCrossDomainPolicies='").append(permittedCrossDomainPolicies).append('\'');
		sb.append('}');
		return sb.toString();
	}
}
