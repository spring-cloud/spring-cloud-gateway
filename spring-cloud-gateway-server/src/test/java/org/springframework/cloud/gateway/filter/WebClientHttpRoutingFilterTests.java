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

package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getAllServeEvents;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.requestMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * @author Sergey Zolotaryov
 */
class WebClientHttpRoutingFilterTests {
	WebClientHttpRoutingFilter filter;

	@Nested
	class UnitTests {
		@Test
		void getOrder_whenCreated_hasLowestPrecedenceOrder() {
			filter = new WebClientHttpRoutingFilter(null, null);
			assertThat(filter.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
		}

		@Test
		void getHeadersFilter_ifHeaderFiltersNull_delegatesToInjectedObjectProvider() {
			HttpHeadersFilter filterMock = mock();
			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProviderMock = mock();
			given(headersFiltersProviderMock.getIfAvailable()).willReturn(List.of(filterMock));

			filter = new WebClientHttpRoutingFilter(null, headersFiltersProviderMock);

			assertThat(filter.getHeadersFilters()).containsExactlyInAnyOrder(filterMock);
			then(headersFiltersProviderMock).should().getIfAvailable();
		}

		@Test
		void filter_ifAlreadyRouted_filtersSameExchangeDownFilterChain() {
			MockServerWebExchange exchange =
					MockServerWebExchange.from(MockServerHttpRequest.get("/"));
			exchange.getAttributes().putAll(Map.of(
					ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR, true,
					ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create("")
			));
			GatewayFilterChain chainMock = mock();
			given(chainMock.filter(exchange)).willReturn(Mono.empty());
			filter = new WebClientHttpRoutingFilter(null, null);

			StepVerifier.create(filter.filter(exchange, chainMock))
					.verifyComplete();
			then(chainMock).should().filter(exchange);
		}

		@Test
		void filter_ifNotRouted_butSchemeNeitherHttpNorHttps_filtersSameExchangeDownFilterChain() {
			MockServerWebExchange exchange =
					MockServerWebExchange.from(MockServerHttpRequest.get("/"));
			exchange.getAttributes().putAll(Map.of(
					ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR, false,
					ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create("some-other-scheme://example.com")
			));
			GatewayFilterChain chainMock = mock();
			given(chainMock.filter(exchange)).willReturn(Mono.empty());
			filter = new WebClientHttpRoutingFilter(null, null);

			StepVerifier.create(filter.filter(exchange, chainMock))
					.verifyComplete();
			then(chainMock).should().filter(exchange);
		}
	}

	@Nested
	@WireMockTest
	@ExtendWith(MockitoExtension.class)
	class WireMockTests {
		@Mock
		GatewayFilterChain chainMock;
		@Captor
		ArgumentCaptor<ServerWebExchange> exchangeCaptor;
		int port;

		@BeforeEach
		void setUp(WireMockRuntimeInfo info) {
			port = info.getHttpPort();
			given(chainMock.filter(any())).willReturn(Mono.empty());
		}

		@Test
		void filter_ifNotRouted_ifSchemeHttps_setsAlreadyRoutedAttributeToTrue() {
			String uri = "http://localhost:%d/post".formatted(port);
			MockServerWebExchange exchange =
					MockServerWebExchange.from(MockServerHttpRequest.post(""));
			exchange.getAttributes().putAll(Map.of(
					ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR, false,
					ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create(uri)
			));

			stubFor(post("/post").willReturn(ok()));

			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProviderMock = mock();
			filter = new WebClientHttpRoutingFilter(WebClient.builder().build(), headersFiltersProviderMock);

			StepVerifier.create(filter.filter(exchange, chainMock))
					.verifyComplete();

			then(chainMock).should().filter(exchangeCaptor.capture());

			ServerWebExchange filteredExchange = exchangeCaptor.getValue();
			assertThat(filteredExchange.getAttributes())
					.extractingByKey(ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR)
					.isEqualTo(true);
		}

		@Test
		void filter_ifNotRouted_ifSchemeHttps_includesAllFilteredHeadersInOutboundRequest() {
			String headerToRemoveName = "header-to-remove";

			MockServerHttpRequest request = MockServerHttpRequest.post("")
					.header(headerToRemoveName, "")
					.header("header-to-keep", "")
					.build();
			String uri = "http://localhost:%d/post".formatted(port);
			MockServerWebExchange exchange = MockServerWebExchange.from(request);
			exchange.getAttributes().putAll(Map.of(
					ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR, false,
					ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create(uri)
			));

			HttpHeaders trimmedHeaders = HttpHeaders.writableHttpHeaders(request.getHeaders());
			trimmedHeaders.remove(headerToRemoveName);

			HttpHeadersFilter headersFilterMock = mock();
			given(headersFilterMock.supports(HttpHeadersFilter.Type.REQUEST)).willReturn(true);
			given(headersFilterMock.filter(exchange.getRequest().getHeaders(), exchange)).willReturn(trimmedHeaders);

			stubFor(post("/post").willReturn(ok()));

			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProviderMock = mock();
			given(headersFiltersProviderMock.getIfAvailable()).willReturn(List.of(headersFilterMock));
			filter = new WebClientHttpRoutingFilter(WebClient.builder().build(), headersFiltersProviderMock);

			StepVerifier.create(filter.filter(exchange, chainMock))
					.verifyComplete();

			List<ServeEvent> allServeEvents = getAllServeEvents();
			assertThat(allServeEvents).hasSize(1);

			LoggedRequest loggedRequest = allServeEvents.get(0).getRequest();

			assertThat(loggedRequest.getAllHeaderKeys()).containsOnlyOnceElementsOf(trimmedHeaders.keySet());
		}

		@Test
		void filter_ifNotRouted_ifSchemeHttps_ifNoPreserveHostRequestAttribute_doesNotPreserveCustomHost() {
			String customHost = "custom-host";
			MockServerHttpRequest request = MockServerHttpRequest.post("")
					.header(HttpHeaders.HOST, customHost)
					.build();
			String uri = "http://localhost:%d/post".formatted(port);
			MockServerWebExchange exchange = MockServerWebExchange.from(request);
			exchange.getAttributes().putAll(Map.of(
					ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR, false,
					ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create(uri)
			));

			stubFor(post("/post").willReturn(ok()));

			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProviderMock = mock();
			filter = new WebClientHttpRoutingFilter(WebClient.builder().build(), headersFiltersProviderMock);

			StepVerifier.create(filter.filter(exchange, chainMock))
					.verifyComplete();

			List<ServeEvent> allServeEvents = getAllServeEvents();
			assertThat(allServeEvents).hasSize(1);

			LoggedRequest loggedRequest = allServeEvents.get(0).getRequest();
			HttpHeader header = loggedRequest.getHeaders().getHeader(HttpHeaders.HOST);
			assertThat(header.values()).doesNotContain(customHost);
		}

		@Test
		void filter_ifNotRouted_ifSchemeHttps_ifPreserveHostRequestAttributeSetToTrue_preservesHostHeader() {
			String customHost = "custom-host";
			MockServerHttpRequest request = MockServerHttpRequest.post("")
					.header(HttpHeaders.HOST, customHost)
					.build();
			String uri = "http://localhost:%d/post".formatted(port);
			MockServerWebExchange exchange = MockServerWebExchange.from(request);
			exchange.getAttributes().putAll(Map.of(
					ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE, true,
					ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR, false,
					ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create(uri)
			));

			stubFor(post("/post").willReturn(ok()));

			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProviderMock = mock();
			WebClientHttpRoutingFilter filter =
					new WebClientHttpRoutingFilter(WebClient.builder().build(), headersFiltersProviderMock);

			StepVerifier.create(filter.filter(exchange, chainMock))
					.verifyComplete();

			List<ServeEvent> allServeEvents = getAllServeEvents();
			assertThat(allServeEvents).hasSize(1);

			LoggedRequest loggedRequest = allServeEvents.get(0).getRequest();
			HttpHeader header = loggedRequest.getHeaders().getHeader(HttpHeaders.HOST);
			assertThat(header.values()).contains(customHost);
		}

		@ParameterizedTest
		@ValueSource(strings = {"GET", "HEAD", "DELETE", "OPTIONS", "TRACE"})
		void filter_ifNotRouted_ifSchemeHttps_ifMethodRequiresNoBody_bodyIgnored(String methodString) {
			HttpMethod method = HttpMethod.valueOf(methodString);
			MockServerHttpRequest request = MockServerHttpRequest.method(method, "")
					.body("some-body");

			MockServerWebExchange exchange = MockServerWebExchange.from(request);
			String uri = "http://localhost:%d/%s".formatted(port, methodString.toLowerCase());
			exchange.getAttributes().putAll(Map.of(
					ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR, false,
					ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create(uri)
			));

			stubFor(requestMatching(r -> {
				boolean isMatch = r.getMethod().value().equals(methodString) &&
						r.getUrl().equals("/%s".formatted(methodString.toLowerCase()));
				return MatchResult.of(isMatch);
			}).willReturn(ok()));

			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProviderMock = mock();
			filter = new WebClientHttpRoutingFilter(WebClient.builder().build(), headersFiltersProviderMock);

			StepVerifier.create(filter.filter(exchange, chainMock))
					.verifyComplete();

			List<ServeEvent> allServeEvents = getAllServeEvents();
			assertThat(allServeEvents).hasSize(1);

			LoggedRequest loggedRequest = allServeEvents.get(0).getRequest();
			String actualBody = loggedRequest.getBodyAsString();
			assertThat(actualBody).isNullOrEmpty();
		}

		@ParameterizedTest
		@ValueSource(strings = {"POST", "PUT", "PATCH"})
		void filter_ifNotRouted_ifSchemeHttps_ifMethodRequiresBody_bodyUnchangedInOutboundRequest(String methodString) {
			String bodyValue = "some-body";
			HttpMethod method = HttpMethod.valueOf(methodString);
			MockServerHttpRequest request = MockServerHttpRequest.method(method, "")
					.body(bodyValue);

			stubFor(requestMatching(r -> {
				boolean isMatch = r.getMethod().value().equals(methodString) &&
						r.getUrl().equals("/%s".formatted(methodString.toLowerCase()));
				return MatchResult.of(isMatch);
			}).willReturn(ok()));

			MockServerWebExchange exchange = MockServerWebExchange.from(request);
			String uri = "http://localhost:%d/%s".formatted(port, methodString.toLowerCase());
			exchange.getAttributes().putAll(Map.of(
					ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR, false,
					ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create(uri)
			));

			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProviderMock = mock();
			filter = new WebClientHttpRoutingFilter(WebClient.builder().build(), headersFiltersProviderMock);

			StepVerifier.create(filter.filter(exchange, chainMock))
					.verifyComplete();

			List<ServeEvent> allServeEvents = getAllServeEvents();
			assertThat(allServeEvents).hasSize(1);
			LoggedRequest loggedRequest = allServeEvents.get(0).getRequest();

			String actualBody = loggedRequest.getBodyAsString();
			assertThat(actualBody).isEqualTo(bodyValue);
		}

		@Test
		void filter_ifNotRouted_ifSchemeHttps_responseHeadersPropagated() {
			SimpleEntry<String, String> additionalHeader = new SimpleEntry<>("some-header", "some-value");
			stubFor(post("/post")
					.willReturn(aResponse().withHeader(additionalHeader.getKey(), additionalHeader.getValue())));
			MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(""));
			String uri = "http://localhost:%d/post".formatted(port);
			exchange.getAttributes().putAll(Map.of(
					ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR, false,
					ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create(uri)
			));

			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProviderMock = mock();
			filter = new WebClientHttpRoutingFilter(WebClient.builder().build(), headersFiltersProviderMock);

			StepVerifier.create(filter.filter(exchange, chainMock))
					.verifyComplete();

			then(chainMock).should().filter(exchangeCaptor.capture());
			HttpHeaders headers = exchange.getResponse().getHeaders();
			assertThat(headers.get(additionalHeader.getKey())).containsExactly(additionalHeader.getValue());
		}

		@Test
		void filter_ifNotRouted_ifSchemeHttps_responseStatusPropagated() {
			HttpStatus status = HttpStatus.I_AM_A_TEAPOT;
			stubFor(post("/post")
					.willReturn(aResponse().withStatus(status.value())));
			MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(""));
			String uri = "http://localhost:%d/post".formatted(port);
			exchange.getAttributes().putAll(Map.of(
					ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR, false,
					ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create(uri)
			));

			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProviderMock = mock();
			filter = new WebClientHttpRoutingFilter(WebClient.builder().build(), headersFiltersProviderMock);

			StepVerifier.create(filter.filter(exchange, chainMock))
					.verifyComplete();

			then(chainMock).should().filter(exchangeCaptor.capture());
			HttpStatusCode actualStatus = exchange.getResponse().getStatusCode();
			assertThat(actualStatus).isEqualTo(status);
		}

		@Test
		void filter_ifNotRouted_ifSchemeHttps_receivedResponseStoredAsAttribute() {
			String responseBody = "response-body";
			HttpStatus responseStatus = HttpStatus.I_AM_A_TEAPOT;
			SimpleEntry<String, String> someHeader = new SimpleEntry<>("some-header", "some-value");
			ResponseDefinitionBuilder mockedResponse = aResponse()
					.withStatus(responseStatus.value())
					.withHeader(someHeader.getKey(), someHeader.getValue())
					.withBody(responseBody);
			stubFor(post("/post").willReturn(mockedResponse));

			MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(""));
			String uri = "http://localhost:%d/post".formatted(port);
			exchange.getAttributes().putAll(Map.of(
					ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR, false,
					ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create(uri)
			));

			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProviderMock = mock();
			filter = new WebClientHttpRoutingFilter(WebClient.builder().build(), headersFiltersProviderMock);

			assumeThat((ClientResponse) exchange.getAttribute(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR)).isNull();

			StepVerifier.create(filter.filter(exchange, chainMock))
					.verifyComplete();

			then(chainMock).should().filter(exchangeCaptor.capture());
			ClientResponse storedClientResponse = exchange.getAttribute(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR);
			assertThat(storedClientResponse).isNotNull();
			assertThat(storedClientResponse.statusCode()).isEqualTo(responseStatus);
			HttpHeaders actualResponseHeaders = storedClientResponse.headers().asHttpHeaders();
			assertThat(actualResponseHeaders)
					.extractingByKey(someHeader.getKey())
					.asList()
					.containsExactly(someHeader.getValue());
		}
	}
}
