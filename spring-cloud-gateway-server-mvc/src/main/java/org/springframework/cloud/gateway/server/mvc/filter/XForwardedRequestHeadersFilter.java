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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.ServerRequest;

import static org.springframework.cloud.gateway.server.mvc.filter.XForwardedRequestHeadersFilterProperties.PREFIX;

@ConfigurationProperties("spring.cloud.gateway.x-forwarded")
public class XForwardedRequestHeadersFilter implements HttpHeadersFilter.RequestHttpHeadersFilter, Ordered {

	/** Default http port. */
	public static final int HTTP_PORT = 80;

	/** Default https port. */
	public static final int HTTPS_PORT = 443;

	/** Http url scheme. */
	public static final String HTTP_SCHEME = "http";

	/** Https url scheme. */
	public static final String HTTPS_SCHEME = "https";

	/** X-Forwarded-For Header. */
	public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

	/** X-Forwarded-Host Header. */
	public static final String X_FORWARDED_HOST_HEADER = "X-Forwarded-Host";

	/** X-Forwarded-Port Header. */
	public static final String X_FORWARDED_PORT_HEADER = "X-Forwarded-Port";

	/** X-Forwarded-Proto Header. */
	public static final String X_FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";

	/** X-Forwarded-Prefix Header. */
	public static final String X_FORWARDED_PREFIX_HEADER = "X-Forwarded-Prefix";

	/** The order of the XForwardedHeadersFilter. */
	private int order = 0;

	/** If the XForwardedHeadersFilter is enabled. */
	private boolean enabled = true;

	/** If X-Forwarded-For is enabled. */
	private boolean forEnabled = true;

	/** If X-Forwarded-Host is enabled. */
	private boolean hostEnabled = true;

	/** If X-Forwarded-Port is enabled. */
	private boolean portEnabled = true;

	/** If X-Forwarded-Proto is enabled. */
	private boolean protoEnabled = true;

	/** If X-Forwarded-Prefix is enabled. */
	private boolean prefixEnabled = true;

	/** If appending X-Forwarded-For as a list is enabled. */
	private boolean forAppend = true;

	/** If appending X-Forwarded-Host as a list is enabled. */
	private boolean hostAppend = true;

	/** If appending X-Forwarded-Port as a list is enabled. */
	private boolean portAppend = true;

	/** If appending X-Forwarded-Proto as a list is enabled. */
	private boolean protoAppend = true;

	/** If appending X-Forwarded-Prefix as a list is enabled. */
	private boolean prefixAppend = true;

	@Deprecated
	public XForwardedRequestHeadersFilter() {
		this(new XForwardedRequestHeadersFilterProperties());
	}

	public XForwardedRequestHeadersFilter(XForwardedRequestHeadersFilterProperties props) {
		// TODO: remove individual properties in 4.2.0
		// this.properties = properties;
		PropertyMapper map = PropertyMapper.get();
		map.from(props::getOrder).to(o -> this.order = o);
		map.from(props::isEnabled).to(b -> this.enabled = b);
		map.from(props::isForEnabled).to(b -> this.forEnabled = b);
		map.from(props::isHostEnabled).to(b -> this.hostEnabled = b);
		map.from(props::isPortEnabled).to(b -> this.portEnabled = b);
		map.from(props::isProtoEnabled).to(b -> this.protoEnabled = b);
		map.from(props::isPrefixEnabled).to(b -> this.prefixEnabled = b);
		map.from(props::isForAppend).to(b -> this.forAppend = b);
		map.from(props::isHostAppend).to(b -> this.hostAppend = b);
		map.from(props::isPortAppend).to(b -> this.portAppend = b);
		map.from(props::isProtoAppend).to(b -> this.protoAppend = b);
		map.from(props::isPrefixAppend).to(b -> this.prefixAppend = b);
	}

	@DeprecatedConfigurationProperty(replacement = PREFIX + ".order")
	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#setOrder(int)} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	public void setOrder(int order) {
		this.order = order;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#isEnabled()} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".enabled")
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#setEnabled(boolean)} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#isForEnabled()} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".for-enabled")
	public boolean isForEnabled() {
		return forEnabled;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#setForEnabled(boolean)} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	public void setForEnabled(boolean forEnabled) {
		this.forEnabled = forEnabled;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#isHostEnabled()} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".host-enabled")
	public boolean isHostEnabled() {
		return hostEnabled;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#setHostEnabled(boolean)} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	public void setHostEnabled(boolean hostEnabled) {
		this.hostEnabled = hostEnabled;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#isPortEnabled()} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".port-enabled")
	public boolean isPortEnabled() {
		return portEnabled;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#setPortEnabled(boolean)} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	public void setPortEnabled(boolean portEnabled) {
		this.portEnabled = portEnabled;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#isProtoEnabled()} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".proto-enabled")
	public boolean isProtoEnabled() {
		return protoEnabled;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#setProtoEnabled(boolean)} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	public void setProtoEnabled(boolean protoEnabled) {
		this.protoEnabled = protoEnabled;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#isPrefixEnabled()} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".prefix-enabled")
	public boolean isPrefixEnabled() {
		return prefixEnabled;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#setPrefixEnabled(boolean)} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	public void setPrefixEnabled(boolean prefixEnabled) {
		this.prefixEnabled = prefixEnabled;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#isForAppend()} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".for-append")
	public boolean isForAppend() {
		return forAppend;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#setForAppend(boolean)} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	public void setForAppend(boolean forAppend) {
		this.forAppend = forAppend;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#isHostAppend()} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".host-append")
	public boolean isHostAppend() {
		return hostAppend;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#setHostAppend(boolean)} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	public void setHostAppend(boolean hostAppend) {
		this.hostAppend = hostAppend;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#isPortAppend()} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".port-append")
	public boolean isPortAppend() {
		return portAppend;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#setPortAppend(boolean)} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	public void setPortAppend(boolean portAppend) {
		this.portAppend = portAppend;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#isProtoAppend()} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".proto-append")
	public boolean isProtoAppend() {
		return protoAppend;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#setProtoAppend(boolean)} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	public void setProtoAppend(boolean protoAppend) {
		this.protoAppend = protoAppend;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#isPrefixAppend()} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".prefix-append")
	public boolean isPrefixAppend() {
		return prefixAppend;
	}

	/**
	 * @deprecated since 4.1.2 for removal in 4.2.0 in favor of
	 * {@link XForwardedRequestHeadersFilterProperties#setPrefixAppend(boolean)} )}
	 */
	@SuppressWarnings("removal")
	@Deprecated(since = "4.1.2", forRemoval = true)
	public void setPrefixAppend(boolean prefixAppend) {
		this.prefixAppend = prefixAppend;
	}

	@Override
	public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
		HttpHeaders original = input;
		HttpHeaders updated = new HttpHeaders();

		for (Map.Entry<String, List<String>> entry : original.headerSet()) {
			updated.addAll(entry.getKey(), entry.getValue());
		}

		InetSocketAddress remoteAddress = request.remoteAddress().orElse(null);
		if (isForEnabled() && remoteAddress != null && remoteAddress.getAddress() != null) {
			String remoteAddr = remoteAddress.getAddress().getHostAddress();
			write(updated, X_FORWARDED_FOR_HEADER, remoteAddr, isForAppend());
		}

		String proto = request.uri().getScheme();
		if (isProtoEnabled()) {
			write(updated, X_FORWARDED_PROTO_HEADER, proto, isProtoAppend());
		}

		if (isPrefixEnabled()) {
			// If the path of the url that the gw is routing to is a subset
			// (and ending part) of the url that it is routing from then the difference
			// is the prefix e.g. if request original.com/prefix/get/ is routed
			// to routedservice:8090/get then /prefix is the prefix
			// - see XForwardedHeadersFilterTests, so first get uris, then extract paths
			// and remove one from another if it's the ending part.

			LinkedHashSet<URI> originalUris = MvcUtils.getAttribute(request,
					MvcUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
			URI requestUri = request.uri();

			if (originalUris != null && requestUri != null) {

				originalUris.forEach(originalUri -> {

					if (originalUri != null && originalUri.getPath() != null) {
						// strip trailing slashes before checking if request path is end
						// of original path
						String originalUriPath = stripTrailingSlash(originalUri);
						String requestUriPath = stripTrailingSlash(requestUri);

						updateRequest(updated, originalUri, originalUriPath, requestUriPath);

					}
				});
			}
		}

		if (isPortEnabled()) {
			String port = String.valueOf(request.uri().getPort());
			if (request.uri().getPort() < 0) {
				port = String.valueOf(getDefaultPort(proto));
			}
			write(updated, X_FORWARDED_PORT_HEADER, port, isPortAppend());
		}

		if (isHostEnabled()) {
			String host = toHostHeader(request);
			write(updated, X_FORWARDED_HOST_HEADER, host, isHostAppend());
		}

		return updated;
	}

	private void updateRequest(HttpHeaders updated, URI originalUri, String originalUriPath, String requestUriPath) {
		String prefix;
		if (requestUriPath != null && (originalUriPath.endsWith(requestUriPath))) {
			prefix = substringBeforeLast(originalUriPath, requestUriPath);
			if (prefix != null && prefix.length() > 0 && prefix.length() <= originalUri.getPath().length()) {
				write(updated, X_FORWARDED_PREFIX_HEADER, prefix, isPrefixAppend());
			}
		}
	}

	private static String substringBeforeLast(String str, String separator) {
		if (ObjectUtils.isEmpty(str) || ObjectUtils.isEmpty(separator)) {
			return str;
		}
		int pos = str.lastIndexOf(separator);
		if (pos == -1) {
			return str;
		}
		return str.substring(0, pos);
	}

	private void write(HttpHeaders headers, String name, String value, boolean append) {
		if (value == null) {
			return;
		}
		if (append) {
			headers.add(name, value);
			// these headers should be treated as a single comma separated header
			List<String> values = headers.get(name);
			String delimitedValue = StringUtils.collectionToCommaDelimitedString(values);
			headers.set(name, delimitedValue);
		}
		else {
			headers.set(name, value);
		}
	}

	private int getDefaultPort(String scheme) {
		return HTTPS_SCHEME.equals(scheme) ? HTTPS_PORT : HTTP_PORT;
	}

	private boolean hasHeader(ServerRequest request, String name) {
		HttpHeaders headers = request.headers().asHttpHeaders();
		return headers.containsKey(name) && StringUtils.hasLength(headers.getFirst(name));
	}

	private String toHostHeader(ServerRequest request) {
		int port = request.uri().getPort();
		String host = request.uri().getHost();
		String scheme = request.uri().getScheme();
		if (port < 0 || (port == HTTP_PORT && HTTP_SCHEME.equals(scheme))
				|| (port == HTTPS_PORT && HTTPS_SCHEME.equals(scheme))) {
			return host;
		}
		else {
			return host + ":" + port;
		}
	}

	private String stripTrailingSlash(URI uri) {
		if (uri.getPath().endsWith("/")) {
			return uri.getPath().substring(0, uri.getPath().length() - 1);
		}
		else {
			return uri.getPath();
		}
	}

}
