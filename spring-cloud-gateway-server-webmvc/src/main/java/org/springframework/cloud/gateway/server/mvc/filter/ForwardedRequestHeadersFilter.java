/*
 * Copyright 2013-present the original author or authors.
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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.core.Ordered;
import org.springframework.core.log.LogMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.ServerRequest;

public class ForwardedRequestHeadersFilter implements HttpHeadersFilter.RequestHttpHeadersFilter, Ordered {

	private static final Log log = LogFactory.getLog(ForwardedRequestHeadersFilter.class);

	/**
	 * Forwarded header.
	 */
	public static final String FORWARDED_HEADER = "Forwarded";

	private final TrustedProxies trustedProxies;

	@Deprecated
	public ForwardedRequestHeadersFilter() {
		trustedProxies = s -> true;
		log.warn(GatewayMvcProperties.PREFIX
				+ ".trusted-proxies is not set. Using deprecated Constructor. Untrusted hosts might be added to Forwarded header.");
	}

	public ForwardedRequestHeadersFilter(String trustedProxiesRegex) {
		trustedProxies = TrustedProxies.from(trustedProxiesRegex);
	}

	/* for testing */
	static List<Forwarded> parse(List<String> values) {
		ArrayList<Forwarded> forwardeds = new ArrayList<>();
		if (CollectionUtils.isEmpty(values)) {
			return forwardeds;
		}
		for (String value : values) {
			String[] forwardedValues = StringUtils.tokenizeToStringArray(value, ",");
			for (String forwardedValue : forwardedValues) {
				Forwarded forwarded = parse(forwardedValue);
				forwardeds.add(forwarded);
			}
		}
		return forwardeds;
	}

	/* for testing */
	static @Nullable Forwarded parse(String value) {
		String[] pairs = StringUtils.tokenizeToStringArray(value, ";");

		LinkedCaseInsensitiveMap<String> result = splitIntoCaseInsensitiveMap(pairs);
		if (result == null) {
			return null;
		}

		Forwarded forwarded = new Forwarded(result);

		return forwarded;
	}

	/* for testing */ static @Nullable LinkedCaseInsensitiveMap<String> splitIntoCaseInsensitiveMap(String[] pairs) {
		if (ObjectUtils.isEmpty(pairs)) {
			return null;
		}

		LinkedCaseInsensitiveMap<String> result = new LinkedCaseInsensitiveMap<>();
		for (String element : pairs) {
			String[] splittedElement = StringUtils.split(element, "=");
			if (splittedElement == null) {
				continue;
			}
			result.put(splittedElement[0].trim(), splittedElement[1].trim());
		}
		return result;
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
		if (request.servletRequest().getRemoteAddr() != null
				&& !trustedProxies.isTrusted(request.servletRequest().getRemoteAddr())) {
			log.trace(LogMessage.format("Remote address not trusted. pattern %s remote address %s", trustedProxies,
					request.servletRequest().getRemoteHost()));
			return input;
		}

		HttpHeaders original = input;
		HttpHeaders updated = new HttpHeaders();

		// copy all headers except Forwarded
		for (Map.Entry<String, List<String>> entry : original.headerSet()) {
			if (!entry.getKey().equalsIgnoreCase(FORWARDED_HEADER)) {
				updated.addAll(entry.getKey(), entry.getValue());
			}
		}

		List<String> forwardedHeader = original.get(FORWARDED_HEADER);
		if (forwardedHeader != null) {
			List<Forwarded> forwardeds = parse(forwardedHeader);

			for (Forwarded f : forwardeds) {
				// only add if "for" value matches trustedProxies
				if (trustedProxies.isTrusted(f.get("for"))) {
					updated.add(FORWARDED_HEADER, f.toHeaderValue());
				}
			}
		}

		// TODO: add new forwarded
		URI uri = request.uri();
		String host = original.getFirst(HttpHeaders.HOST);
		if (host != null) {
			Forwarded forwarded = new Forwarded().put("host", host).put("proto", uri.getScheme());

			request.remoteAddress().ifPresent(remoteAddress -> {
				// If remoteAddress is unresolved, calling getHostAddress() would cause a
				// NullPointerException.
				String forValue;
				if (remoteAddress.isUnresolved()) {
					forValue = remoteAddress.getHostName();
				}
				else {
					InetAddress address = remoteAddress.getAddress();
					forValue = remoteAddress.getAddress().getHostAddress();
					if (address instanceof Inet6Address) {
						forValue = "[" + forValue + "]";
					}
				}
				if (!trustedProxies.isTrusted(forValue)) {
					// don't add for value
					return;
				}
				int port = remoteAddress.getPort();
				if (port >= 0 && !forValue.endsWith(":" + port)) {
					forValue = forValue + ":" + port;
				}
				forwarded.put("for", forValue);
			});
			// TODO: support by?

			updated.add(FORWARDED_HEADER, forwarded.toHeaderValue());
		}

		return updated;
	}

	@SuppressWarnings("NullAway")
	/* for testing */ static class Forwarded {

		private static final char EQUALS = '=';

		private static final char SEMICOLON = ';';

		private final Map<String, String> values;

		Forwarded() {
			this.values = new HashMap<>();
		}

		Forwarded(Map<String, String> values) {
			this.values = values;
		}

		public Forwarded put(String key, String value) {
			this.values.put(key, quoteIfNeeded(value));
			return this;
		}

		private String quoteIfNeeded(String s) {
			if (s != null && s.contains(":")) { // TODO: broaded quote
				return "\"" + s + "\"";
			}
			return s;
		}

		public String get(String key) {
			return this.values.get(key);
		}

		/* for testing */ Map<String, String> getValues() {
			return this.values;
		}

		@Override
		public String toString() {
			return "Forwarded{" + "values=" + this.values + '}';
		}

		public String toHeaderValue() {
			StringBuilder builder = new StringBuilder();
			for (Map.Entry<String, String> entry : this.values.entrySet()) {
				if (builder.length() > 0) {
					builder.append(SEMICOLON);
				}
				builder.append(entry.getKey()).append(EQUALS).append(entry.getValue());
			}
			return builder.toString();
		}

	}

}
