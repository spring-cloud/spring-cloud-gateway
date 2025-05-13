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

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.ServerRequest;

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

	private final XForwardedRequestHeadersFilterProperties properties;

	public XForwardedRequestHeadersFilter(XForwardedRequestHeadersFilterProperties properties) {
		this.properties = properties;
	}

	@Override
	public int getOrder() {
		return properties.getOrder();
	}

	@Override
	public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
		HttpHeaders original = input;
		HttpHeaders updated = new HttpHeaders();

		for (Map.Entry<String, List<String>> entry : original.headerSet()) {
			updated.addAll(entry.getKey(), entry.getValue());
		}

		InetSocketAddress remoteAddress = request.remoteAddress().orElse(null);
		if (properties.isForEnabled() && remoteAddress != null && remoteAddress.getAddress() != null) {
			String remoteAddr = remoteAddress.getAddress().getHostAddress();
			write(updated, X_FORWARDED_FOR_HEADER, remoteAddr, properties.isForAppend());
		}

		String proto = request.uri().getScheme();
		if (properties.isProtoEnabled()) {
			write(updated, X_FORWARDED_PROTO_HEADER, proto, properties.isProtoAppend());
		}

		if (properties.isPrefixEnabled()) {
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

		if (properties.isPortEnabled()) {
			String port = String.valueOf(request.uri().getPort());
			if (request.uri().getPort() < 0) {
				port = String.valueOf(getDefaultPort(proto));
			}
			write(updated, X_FORWARDED_PORT_HEADER, port, properties.isPortAppend());
		}

		if (properties.isHostEnabled()) {
			String host = toHostHeader(request);
			write(updated, X_FORWARDED_HOST_HEADER, host, properties.isHostAppend());
		}

		return updated;
	}

	private void updateRequest(HttpHeaders updated, URI originalUri, String originalUriPath, String requestUriPath) {
		String prefix;
		if (requestUriPath != null && (originalUriPath.endsWith(requestUriPath))) {
			prefix = substringBeforeLast(originalUriPath, requestUriPath);
			if (prefix != null && prefix.length() > 0 && prefix.length() <= originalUri.getPath().length()) {
				write(updated, X_FORWARDED_PREFIX_HEADER, prefix, properties.isPrefixAppend());
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
