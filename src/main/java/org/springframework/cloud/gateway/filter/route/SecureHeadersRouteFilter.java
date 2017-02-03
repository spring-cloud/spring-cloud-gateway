package org.springframework.cloud.gateway.filter.route;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.WebFilter;

/**
 * https://blog.appcanary.com/2017/http-security-headers.html
 * @author Spencer Gibb
 */
public class SecureHeadersRouteFilter implements RouteFilter {

	public static final String X_XSS_PROTECTION_HEADER = "X-Xss-Protection";
	public static final String STRICT_TRANSPORT_SECURITY_HEADER = "Strict-Transport-Security";
	public static final String X_FRAME_OPTIONS_HEADER = "X-Frame-Options";
	public static final String X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";
	public static final String REFERRER_POLICY_HEADER = "Referrer-Policy";
	public static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";
	public static final String X_DOWNLOAD_OPTIONS_HEADER = "X-Download-Options";
	public static final String X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER = "X-Permitted-Cross-Domain-Policies";

	private final SecureHeadersProperties properties;

	public SecureHeadersRouteFilter(SecureHeadersProperties properties) {
		this.properties = properties;
	}

	@Override
	public WebFilter apply(String... args) {
		//TODO: allow args to override properties

		return (exchange, chain) -> {
			HttpHeaders headers = exchange.getResponse().getHeaders();

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
