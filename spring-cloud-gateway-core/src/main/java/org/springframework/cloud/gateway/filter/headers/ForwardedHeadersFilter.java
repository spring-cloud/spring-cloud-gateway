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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

public class ForwardedHeadersFilter implements HttpHeadersFilter, Ordered {

	public static final String FORWARDED_HEADER = "Forwarded";

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		ServerHttpRequest request = exchange.getRequest();
		HttpHeaders original = input;
		HttpHeaders updated = new HttpHeaders();

		// copy all headers except Forwarded
		original.entrySet().stream()
				.filter(entry -> !entry.getKey().toLowerCase().equalsIgnoreCase(FORWARDED_HEADER))
				.forEach(entry -> updated.addAll(entry.getKey(), entry.getValue()));

		List<Forwarded> forwardeds = parse(original.get(FORWARDED_HEADER));

		for (Forwarded f : forwardeds) {
			updated.add(FORWARDED_HEADER, f.toHeaderValue());
		}

		//TODO: add new forwarded
		URI uri = request.getURI();
		String host = original.getFirst(HttpHeaders.HOST);
		Forwarded forwarded = new Forwarded()
				.put("host", host)
				.put("proto", uri.getScheme());

		InetSocketAddress remoteAddress = request.getRemoteAddress();
		if (remoteAddress != null) {
			String forValue = remoteAddress.getAddress().getHostAddress();
			int port = remoteAddress.getPort();
			if (port >= 0) {
				forValue = forValue + ":" + port;
			}
			forwarded.put("for", forValue);
		}
		// TODO: support by?

		updated.add(FORWARDED_HEADER, forwarded.toHeaderValue());

		return updated;
	}


	/* for testing */ static List<Forwarded> parse(List<String> values) {
		ArrayList<Forwarded> forwardeds = new ArrayList<>();
		if (CollectionUtils.isEmpty(values)) {
			return forwardeds;
		}
    	for (String value : values) {
			Forwarded forwarded = parse(value);
			forwardeds.add(forwarded);
		}
		return forwardeds;
	}

	/* for testing */ static Forwarded parse(String value) {
		String[] pairs = StringUtils.tokenizeToStringArray(value, ";");

		LinkedCaseInsensitiveMap<String> result = splitIntoCaseInsensitiveMap(pairs);
		if (result == null) return null;

		Forwarded forwarded = new Forwarded(result);

		return forwarded;
	}

	@Nullable
	/* for testing */ static LinkedCaseInsensitiveMap<String> splitIntoCaseInsensitiveMap(String[] pairs) {
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

	/* for testing */ static class Forwarded {

		private static final char EQUALS = '=';
		private static final char SEMICOLON = ';';

		private final Map<String, String> values;

		public Forwarded() {
			this.values = new HashMap<>();
		}

		public Forwarded(Map<String, String> values) {
			this.values = values;
		}

		public Forwarded put(String key, String value) {
			this.values.put(key, quoteIfNeeded(value));
			return this;
		}


		private String quoteIfNeeded(String s) {
			if (s != null && s.contains(":")) { //TODO: broaded quote
				return "\""+s+"\"";
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
			return "Forwarded{" +
					"values=" + this.values +
					'}';
		}

		public String toHeaderValue() {
			StringBuilder builder = new StringBuilder();
			for (Map.Entry<String, String> entry : this.values.entrySet()) {
				if (builder.length() > 0) {
					builder.append(SEMICOLON);
				}
				builder.append(entry.getKey())
						.append(EQUALS)
						.append(entry.getValue());
			}
			return builder.toString();
		}
	}

}
