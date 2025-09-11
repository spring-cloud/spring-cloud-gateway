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

package org.springframework.cloud.gateway.filter.headers;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.reactor.netty.autoconfigure.NettyReactiveWebServerAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
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

	public static Map<String, String> map(String... values) {
		if (values.length % 2 != 0) {
			throw new IllegalArgumentException("values must have even number of items: " + Arrays.asList(values));
		}
		HashMap<String, String> map = new HashMap<>();
		for (int i = 0; i < values.length; i++) {
			map.put(values[i], values[++i]);
		}
		return map;
	}

	@Test
	public void forwardedHeaderDoesNotExist() throws UnknownHostException {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(HttpHeaders.HOST, "myhost")
			.build();

		ForwardedHeadersFilter filter = new ForwardedHeadersFilter(".*");

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.get(FORWARDED_HEADER)).hasSize(1);

		List<Forwarded> forwardeds = ForwardedHeadersFilter.parse(headers.get(FORWARDED_HEADER));

		assertThat(forwardeds).hasSize(1);
		Forwarded forwarded = forwardeds.get(0);

		assertThat(forwarded.getValues()).containsEntry("host", "myhost")
			.containsEntry("proto", "http")
			.containsEntry("for", "\"10.0.0.1:80\"");
	}

	@Test
	public void forwardedHeaderExists() throws UnknownHostException {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(FORWARDED_HEADER, "for=12.34.56.78;host=example.com;proto=https, for=23.45.67.89")
			.build();

		ForwardedHeadersFilter filter = new ForwardedHeadersFilter(".*");

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.get(FORWARDED_HEADER)).hasSize(3);

		List<Forwarded> forwardeds = ForwardedHeadersFilter.parse(headers.get(FORWARDED_HEADER));

		assertThat(forwardeds).hasSize(3);
		Optional<Forwarded> added = forwardeds.stream()
			.filter(forwarded -> forwarded.get("for").contains("10.0.0.1:80"))
			.findFirst();
		assertThat(added).isPresent();
		added.ifPresent(forwarded -> {
			assertThat(forwarded.getValues()).containsEntry("proto", "http").containsEntry("for", "\"10.0.0.1:80\"");
		});
		Optional<Forwarded> existing = forwardeds.stream()
			.filter(forwarded -> forwarded.get("for").equals("23.45.67.89"))
			.findFirst();
		assertThat(existing).isPresent();
		existing.ifPresent(forwarded -> {
			assertThat(forwarded.getValues()).containsEntry("for", "23.45.67.89");
		});
		existing = forwardeds.stream().filter(forwarded -> forwarded.get("for").equals("12.34.56.78")).findFirst();
		assertThat(existing).isPresent();
		existing.ifPresent(forwarded -> {
			assertThat(forwarded.getValues()).containsEntry("proto", "https").containsEntry("for", "12.34.56.78");
		});
	}

	@Test
	public void noHostHeader() throws UnknownHostException {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.build();

		ForwardedHeadersFilter filter = new ForwardedHeadersFilter(".*");

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.get(FORWARDED_HEADER)).hasSize(1);

		List<Forwarded> forwardeds = ForwardedHeadersFilter.parse(headers.get(FORWARDED_HEADER));

		assertThat(forwardeds).hasSize(1);
		Forwarded forwarded = forwardeds.get(0);

		assertThat(forwarded.getValues()).containsEntry("proto", "http").containsEntry("for", "\"10.0.0.1:80\"");
	}

	@Test
	public void correctIPv6RemoteAddressMapping() throws UnknownHostException {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("2001:db8:cafe:0:0:0:0:17"), 80))
			.header(HttpHeaders.HOST, "myhost")
			.build();

		ForwardedHeadersFilter filter = new ForwardedHeadersFilter(".*");

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.get(FORWARDED_HEADER)).hasSize(1);

		List<Forwarded> forwardeds = ForwardedHeadersFilter.parse(headers.get(FORWARDED_HEADER));

		assertThat(forwardeds).hasSize(1);
		Forwarded forwarded = forwardeds.get(0);

		assertThat(forwarded.getValues()).containsEntry("for", "\"[2001:db8:cafe:0:0:0:0:17]:80\"");
	}

	@Test
	public void unresolvedRemoteAddressFallsBackToHostName() throws UnknownHostException {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get")
			.remoteAddress(InetSocketAddress.createUnresolved("unresolvable-hostname", 80))
			.build();

		ForwardedHeadersFilter filter = new ForwardedHeadersFilter(".*");

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.get(FORWARDED_HEADER)).hasSize(1);

		List<Forwarded> forwardeds = ForwardedHeadersFilter.parse(headers.get(FORWARDED_HEADER));

		assertThat(forwardeds).hasSize(1);
		Forwarded forwarded = forwardeds.get(0);

		assertThat(forwarded.getValues()).containsEntry("proto", "http")
			.containsEntry("for", "\"unresolvable-hostname:80\"");
	}

	@Test
	public void forwardedParsedCorrectly() {
		String[] valid = new String[] { "for=\"_gazonk\"", "for=192.0.2.60;proto=http;by=203.0.113.43",
				"for=192.0.2.43, for=198.51.100.17", "for=12.34.56.78;host=example.com;proto=https, for=23.45.67.89",
				"for=12.34.56.78, for=23.45.67.89;secret=egah2CGj55fSJFs, for=10.1.2.3",
				"For=\"[2001:db8:cafe::17]:4711\"", };

		List<List<Map<String, String>>> expectedFor = new ArrayList<>();
		expectedFor.add(Arrays.asList(map("for", "\"_gazonk\"")));
		expectedFor.add(Arrays.asList(map("for", "192.0.2.60", "proto", "http", "by", "203.0.113.43")));
		expectedFor.add(Arrays.asList(map("for", "192.0.2.43"), map("for", "198.51.100.17")));
		expectedFor.add(Arrays.asList(map("for", "12.34.56.78", "host", "example.com", "proto", "https"),
				map("for", "23.45.67.89")));
		expectedFor.add(Arrays.asList(map("for", "12.34.56.78"), map("for", "23.45.67.89", "secret", "egah2CGj55fSJFs"),
				map("for", "10.1.2.3")));
		expectedFor.add(Arrays.asList(map("for", "\"[2001:db8:cafe::17]:4711\"")));

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
				assertThat(forwarded.getValues()).hasSize(expected.get(j).size()).containsAllEntriesOf(expected.get(j));
			}
		}
	}

	@Test
	public void forwardedByForIpv4AddressIsAdded() throws UnknownHostException {
		Forwarded forwarded = new Forwarded();
		InetAddress ipv4Address = InetAddress.getByName("216.103.69.111");
		ForwardedHeadersFilter forwardedHeadersFilter = new ForwardedHeadersFilter();
		forwardedHeadersFilter.setForwardedByEnabled(true);

		forwardedHeadersFilter.addForwardedBy(forwarded, ipv4Address);

		Assertions.assertThat(forwarded.getValues()).containsEntry("by", "216.103.69.111");

	}

	@Test
	public void forwardedByForIpv6AddressIsAdded() throws UnknownHostException {
		Forwarded forwarded = new Forwarded();
		InetAddress ipv6Address = InetAddress.getByName("abc4:babf:955f:1724:11bc:0153:275c:d36e");
		ForwardedHeadersFilter forwardedHeadersFilter = new ForwardedHeadersFilter();
		forwardedHeadersFilter.setForwardedByEnabled(true);

		forwardedHeadersFilter.addForwardedBy(forwarded, ipv6Address);

		Assertions.assertThat(forwarded.getValues())
			.containsEntry("by", "\"[abc4:babf:955f:1724:11bc:153:275c:d36e]\"");

	}

	@Test
	public void forwardedByIsNotAddedIfFeatureIsDisabled() throws UnknownHostException {
		Forwarded forwarded = new Forwarded();
		InetAddress ipv4Address = InetAddress.getByName("216.103.69.111");
		ForwardedHeadersFilter forwardedHeadersFilter = new ForwardedHeadersFilter();
		forwardedHeadersFilter.setForwardedByEnabled(false);

		forwardedHeadersFilter.addForwardedBy(forwarded, ipv4Address);

		Assertions.assertThat(forwarded.getValues()).containsEntry("by", "216.103.69.111");
	}

	@Test
	public void trustedProxiesConditionMatches() {
		new ReactiveWebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, SslAutoConfiguration.class,
					NettyReactiveWebServerAutoConfiguration.class, GatewayAutoConfiguration.class))
			.withPropertyValues("spring.cloud.gateway.server.webflux.trusted-proxies=11\\.0\\.0\\..*")
			.run(context -> {
				assertThat(context).hasSingleBean(ForwardedHeadersFilter.class);
			});
	}

	@Test
	public void trustedProxiesConditionDoesNotMatch() {
		new ReactiveWebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, SslAutoConfiguration.class,
					NettyReactiveWebServerAutoConfiguration.class, GatewayAutoConfiguration.class))
			.run(context -> {
				assertThat(context).doesNotHaveBean(ForwardedHeadersFilter.class);
			});
	}

	@Test
	public void emptyTrustedProxiesFails() {
		Assertions.assertThatThrownBy(() -> new ForwardedHeadersFilter(""))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void forwardedHeadersNotTrusted() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(HttpHeaders.HOST, "myhost")
			.build();

		ForwardedHeadersFilter filter = new ForwardedHeadersFilter("11\\.0\\.0\\..*");

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).doesNotContain(FORWARDED_HEADER);
	}

	// verify that existing forwarded header is not forwarded if not trusted
	@Test
	public void untrustedForwardedForNotAppended() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get")
			.remoteAddress(new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 80))
			.header(HttpHeaders.HOST, "myhost")
			.header(FORWARDED_HEADER, "proto=http;host=myhost;for=\"127.0.0.1:80\",for=10.0.0.11")
			.build();

		ForwardedHeadersFilter filter = new ForwardedHeadersFilter("10\\.0\\.0\\..*");

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).contains(FORWARDED_HEADER);
		List<String> forwardedHeaders = headers.get(FORWARDED_HEADER);
		Optional<String> filtered = forwardedHeaders.stream().filter(value -> value.contains("127.0.0.1")).findFirst();
		assertThat(filtered).isEmpty();
		filtered = forwardedHeaders.stream().filter(value -> value.contains("10.0.0.11")).findFirst();
		assertThat(filtered).isNotEmpty();
	}

	@Test
	public void remoteAdddressIsNullUnTrustedProxyNotAppended() throws Exception {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8080/get")
			.header(HttpHeaders.HOST, "myhost")
			.header(FORWARDED_HEADER, "proto=http;host=myhost;for=127.0.0.1")
			.build();

		ForwardedHeadersFilter filter = new ForwardedHeadersFilter("10\\.0\\.0\\..*");

		HttpHeaders headers = filter.filter(request.getHeaders(), MockServerWebExchange.from(request));

		assertThat(headers.headerNames()).contains(FORWARDED_HEADER);
		List<String> forwardedHeaders = headers.get(FORWARDED_HEADER);
		Optional<String> filtered = forwardedHeaders.stream().filter(value -> value.contains("127.0.0.1")).findFirst();
		assertThat(filtered).isEmpty();
	}

}
