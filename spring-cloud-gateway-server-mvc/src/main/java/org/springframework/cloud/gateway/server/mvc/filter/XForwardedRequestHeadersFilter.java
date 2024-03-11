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
		map.from(props::getOrder).to(this::setOrder);
		map.from(props::isEnabled).to(this::setEnabled);
		map.from(props::isForEnabled).to(this::setForEnabled);
		map.from(props::isHostEnabled).to(this::setHostEnabled);
		map.from(props::isPortEnabled).to(this::setPortEnabled);
		map.from(props::isProtoEnabled).to(this::setProtoEnabled);
		map.from(props::isPrefixEnabled).to(this::setPrefixEnabled);
		map.from(props::isForAppend).to(this::setForAppend);
		map.from(props::isHostAppend).to(this::setHostAppend);
		map.from(props::isPortAppend).to(this::setPortAppend);
		map.from(props::isProtoAppend).to(this::setProtoAppend);
		map.from(props::isPrefixAppend).to(this::setPrefixAppend);
	}

	@DeprecatedConfigurationProperty(replacement = PREFIX + ".order")
	@Override
	public int getOrder() {
		return this.order;
	}

	@Deprecated
	public void setOrder(int order) {
		this.order = order;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".enabled")
	public boolean isEnabled() {
		return enabled;
	}

	@Deprecated
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".for-enabled")
	public boolean isForEnabled() {
		return forEnabled;
	}

	@Deprecated
	public void setForEnabled(boolean forEnabled) {
		this.forEnabled = forEnabled;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".host-enabled")
	public boolean isHostEnabled() {
		return hostEnabled;
	}

	@Deprecated
	public void setHostEnabled(boolean hostEnabled) {
		this.hostEnabled = hostEnabled;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".port-enabled")
	public boolean isPortEnabled() {
		return portEnabled;
	}

	@Deprecated
	public void setPortEnabled(boolean portEnabled) {
		this.portEnabled = portEnabled;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".proto-enabled")
	public boolean isProtoEnabled() {
		return protoEnabled;
	}

	@Deprecated
	public void setProtoEnabled(boolean protoEnabled) {
		this.protoEnabled = protoEnabled;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".prefix-enabled")
	public boolean isPrefixEnabled() {
		return prefixEnabled;
	}

	public void setPrefixEnabled(boolean prefixEnabled) {
		this.prefixEnabled = prefixEnabled;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".for-append")
	public boolean isForAppend() {
		return forAppend;
	}

	@Deprecated
	public void setForAppend(boolean forAppend) {
		this.forAppend = forAppend;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".host-append")
	public boolean isHostAppend() {
		return hostAppend;
	}

	@Deprecated
	public void setHostAppend(boolean hostAppend) {
		this.hostAppend = hostAppend;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".port-append")
	public boolean isPortAppend() {
		return portAppend;
	}

	@Deprecated
	public void setPortAppend(boolean portAppend) {
		this.portAppend = portAppend;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".proto-append")
	public boolean isProtoAppend() {
		return protoAppend;
	}

	public void setProtoAppend(boolean protoAppend) {
		this.protoAppend = protoAppend;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(replacement = PREFIX + ".prefix-append")
	public boolean isPrefixAppend() {
		return prefixAppend;
	}

	@Deprecated
	public void setPrefixAppend(boolean prefixAppend) {
		this.prefixAppend = prefixAppend;
	}

	@Override
	public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
		HttpHeaders original = input;
		HttpHeaders updated = new HttpHeaders();

		for (Map.Entry<String, List<String>> entry : original.entrySet()) {
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

			LinkedHashSet<URI> originalUris = null; // TODO:
													// exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
			URI requestUri = null; // TODO:
									// exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);

			if (originalUris != null && requestUri != null) {

				originalUris.forEach(originalUri -> {

					if (originalUri != null && originalUri.getPath() != null) {
						String prefix = originalUri.getPath();

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
