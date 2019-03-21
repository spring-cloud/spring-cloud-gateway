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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilter.Forwarded;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilter.FORWARDED_HEADER;

/**
 * @author Spencer Gibb
 */
public class ForwardedHeadersFilterTests {

	@Test
	public void forwardedHeaderDoesNotExist() throws UnknownHostException {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get")
				.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
				.header(HttpHeaders.HOST, "myhost")
				.build();

		ForwardedHeadersFilter filter = new ForwardedHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.get(FORWARDED_HEADER)).hasSize(1);

		List<Forwarded> forwardeds = ForwardedHeadersFilter.parse(headers.get(FORWARDED_HEADER));

		assertThat(forwardeds).hasSize(1);
		Forwarded forwarded = forwardeds.get(0);

		assertThat(forwarded.getValues())
				.containsEntry("host", "myhost")
				.containsEntry("proto", "http")
				.containsEntry("for", "\"10.0.0.1:80\"");
	}

	@Test
	public void forwardedHeaderExists() throws UnknownHostException {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get")
				.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
				.header(FORWARDED_HEADER, "for=12.34.56.78;host=example.com;proto=https; for=23.45.67.89")
				.build();

		ForwardedHeadersFilter filter = new ForwardedHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.get(FORWARDED_HEADER)).hasSize(2);


		List<Forwarded> forwardeds = ForwardedHeadersFilter.parse(headers.get(FORWARDED_HEADER));

		assertThat(forwardeds).hasSize(2);
		Forwarded addedForwardedHeader = forwardeds.get(0);
		Forwarded existingForwardedHeader = forwardeds.get(1);

		assertThat(existingForwardedHeader.getValues())
				.containsEntry("proto", "http")
				.containsEntry("for", "\"10.0.0.1:80\"");

		assertThat(addedForwardedHeader.getValues())
				.containsEntry("proto", "https")
				.containsEntry("for", "23.45.67.89");
	}

	@Test
	public void noHostHeader() throws UnknownHostException {
		MockServerHttpRequest request = MockServerHttpRequest
				.get("http://localhost/get")
				.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
				.build();

		ForwardedHeadersFilter filter = new ForwardedHeadersFilter();

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.get(FORWARDED_HEADER)).hasSize(1);

		List<Forwarded> forwardeds = ForwardedHeadersFilter.parse(headers.get(FORWARDED_HEADER));

		assertThat(forwardeds).hasSize(1);
		Forwarded forwarded = forwardeds.get(0);

		assertThat(forwarded.getValues())
				.containsEntry("proto", "http")
				.containsEntry("for", "\"10.0.0.1:80\"");
	}

	@Test
	public void forwardedParsedCorrectly() {
		String[] valid = new String[]{
				"for=\"_gazonk\"",
				"for=192.0.2.60;proto=http;by=203.0.113.43",
				"for=192.0.2.43, for=198.51.100.17",
				"for=12.34.56.78;host=example.com;proto=https, for=23.45.67.89",
				"for=12.34.56.78, for=23.45.67.89;secret=egah2CGj55fSJFs, for=10.1.2.3",
				"For=\"[2001:db8:cafe::17]:4711\"",
		};

		@SuppressWarnings("unchecked")
		List<List<Map<String, String>>> expectedFor = new ArrayList<List<Map<String, String>>>() {{
			add(list(map("for", "\"_gazonk\"")));
			add(list(map("for", "192.0.2.60", "proto", "http", "by", "203.0.113.43")));
			add(list(map("for", "192.0.2.43"), map("for", "198.51.100.17")));
			add(list(map("for", "12.34.56.78", "host", "example.com", "proto", "https"),
					map("for", "23.45.67.89")));
			add(list(map("for", "12.34.56.78"),
					map("for", "23.45.67.89", "secret", "egah2CGj55fSJFs"),
					map("for", "10.1.2.3")));
			add(list(map("for", "\"[2001:db8:cafe::17]:4711\"")));
		}};

		for (int i = 0; i < valid.length; i++) {
			String value = valid[i];
			// simulate spring's parsed headers
			String[] values = StringUtils.tokenizeToStringArray(value, ",");
			List<Forwarded> results = ForwardedHeadersFilter.parse(Arrays.asList(values));
			// System.out.println("results: "+results);

			assertThat(results).hasSize(values.length);
			assertThat(results.get(0)).isNotNull();

			List<Map<String, String>> expected = expectedFor.get(i);
			// System.out.println("expected: "+Arrays.asList(expected));
			assertThat(expected).hasSize(results.size());

			for (int j = 0; j < results.size(); j++) {
				Forwarded forwarded = results.get(j);
				assertThat(forwarded.getValues()).hasSize(expected.get(j).size())
						.containsAllEntriesOf(expected.get(j));
			}
		}
	}

	private List<Map<String, String>> list(Map<String, String>... map) {
		return new ArrayList<>(Arrays.asList(map));
	}

	private Map<String, String> map(String... values) {
		if (values.length % 2 != 0) {
			throw new IllegalArgumentException("values must have even number of items: "
					+ Arrays.asList(values));
		}
		HashMap<String, String> map = new HashMap<>();
		for (int i = 0; i < values.length; i++) {
			map.put(values[i], values[++i]);
		}
		return map;
	}

}
