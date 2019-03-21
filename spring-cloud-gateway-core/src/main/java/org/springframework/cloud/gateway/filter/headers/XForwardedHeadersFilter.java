/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

@ConfigurationProperties("spring.cloud.gateway.x-forwarded")
public class XForwardedHeadersFilter implements HttpHeadersFilter, Ordered {
	/** default http port */
	public static final int HTTP_PORT = 80;

	/** default https port */
	public static final int HTTPS_PORT = 443;

	/** http url scheme */
	public static final String HTTP_SCHEME = "http";

	/** https url scheme */
	public static final String HTTPS_SCHEME = "https";

	/** X-Forwarded-For Header */
	public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

	/** X-Forwarded-Host Header */
	public static final String X_FORWARDED_HOST_HEADER = "X-Forwarded-Host";

	/** X-Forwarded-Port Header */
	public static final String X_FORWARDED_PORT_HEADER = "X-Forwarded-Port";

	/** X-Forwarded-Proto Header */
	public static final String X_FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";

	/** X-Forwarded-Prefix Header */
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

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isForEnabled() {
		return forEnabled;
	}

	public void setForEnabled(boolean forEnabled) {
		this.forEnabled = forEnabled;
	}

	public boolean isHostEnabled() {
		return hostEnabled;
	}

	public void setHostEnabled(boolean hostEnabled) {
		this.hostEnabled = hostEnabled;
	}

	public boolean isPortEnabled() {
		return portEnabled;
	}

	public void setPortEnabled(boolean portEnabled) {
		this.portEnabled = portEnabled;
	}

	public boolean isProtoEnabled() {
		return protoEnabled;
	}

	public void setProtoEnabled(boolean protoEnabled) {
		this.protoEnabled = protoEnabled;
	}

	public boolean isPrefixEnabled() {
		return prefixEnabled;
	}

	public void setPrefixEnabled(boolean prefixEnabled) {
		this.prefixEnabled = prefixEnabled;
	}

	public boolean isForAppend() {
		return forAppend;
	}

	public void setForAppend(boolean forAppend) {
		this.forAppend = forAppend;
	}

	public boolean isHostAppend() {
		return hostAppend;
	}

	public void setHostAppend(boolean hostAppend) {
		this.hostAppend = hostAppend;
	}

	public boolean isPortAppend() {
		return portAppend;
	}

	public void setPortAppend(boolean portAppend) {
		this.portAppend = portAppend;
	}

	public boolean isProtoAppend() {
		return protoAppend;
	}

	public void setProtoAppend(boolean protoAppend) {
		this.protoAppend = protoAppend;
	}

	public void setPrefixAppend(boolean prefixAppend) {
		this.prefixAppend = prefixAppend;
	}

	public boolean isPrefixAppend() {
		return prefixAppend;
	}

	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {


		ServerHttpRequest request = exchange.getRequest();
		HttpHeaders original = input;
		HttpHeaders updated = new HttpHeaders();

		original.entrySet().stream()
				.forEach(entry -> updated.addAll(entry.getKey(), entry.getValue()));

		if (isForEnabled() &&
			request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null)
		{
			String remoteAddr = request.getRemoteAddress().getAddress().getHostAddress();
			List<String> xforwarded = original.get(X_FORWARDED_FOR_HEADER);
			// prevent duplicates
			if (remoteAddr != null &&
					(xforwarded == null || !xforwarded.contains(remoteAddr))) {
				write(updated, X_FORWARDED_FOR_HEADER, remoteAddr, isForAppend());
			}
		}

		String proto = request.getURI().getScheme();
		if (isProtoEnabled()) {
			write(updated, X_FORWARDED_PROTO_HEADER, proto, isProtoAppend());
		}

		if(isPrefixEnabled()) {
			//if the path of the url that the gw is routing to is a subset (and ending part) of the url that it is routing from then the difference is the prefix
			//e.g. if request original.com/prefix/get/ is routed to routedservice:8090/get then /prefix is the prefix - see XForwardedHeadersFilterTests
			//so first get uris, then extract paths and remove one from another if it's the ending part

			LinkedHashSet<URI> originalUris = exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
			URI requestUri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);

			if(originalUris != null && requestUri != null) {

				originalUris.stream().forEach(originalUri -> {

					if(originalUri!=null && originalUri.getPath()!=null) {
						String prefix = originalUri.getPath();

						//strip trailing slashes before checking if request path is end of original path
						String originalUriPath = stripTrailingSlash(originalUri);
						String requestUriPath = stripTrailingSlash(requestUri);

						if(requestUriPath!=null && (originalUriPath.endsWith(requestUriPath))) {
							prefix = originalUriPath.replace(requestUriPath, "");
							if (prefix != null && prefix.length() > 0 &&
									prefix.length() <= originalUri.getPath().length()) {
								write(updated, X_FORWARDED_PREFIX_HEADER, prefix, isPrefixAppend());
							}
						}

					}
				});
			}
		}

		if (isPortEnabled()) {
			String port = String.valueOf(request.getURI().getPort());
			if (request.getURI().getPort() < 0) {
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

	private void write(HttpHeaders headers, String name, String value, boolean append) {
		if (append) {
			headers.add(name, value);
			// these headers should be treated as a single comma separated header
			List<String> values = headers.get(name);
			String delimitedValue = StringUtils.collectionToCommaDelimitedString(values);
			headers.set(name, delimitedValue);
		} else {
			headers.set(name, value);
		}
	}

	private int getDefaultPort(String scheme) {
		return HTTPS_SCHEME.equals(scheme) ? HTTPS_PORT : HTTP_PORT;
	}

	private boolean hasHeader(ServerHttpRequest request, String name) {
		HttpHeaders headers = request.getHeaders();
		return headers.containsKey(name) &&
				StringUtils.hasLength(headers.getFirst(name));
	}


	private String toHostHeader(ServerHttpRequest request) {
		int port = request.getURI().getPort();
		String host = request.getURI().getHost();
		String scheme = request.getURI().getScheme();
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
		} else {
			return uri.getPath();
		}
	}
}