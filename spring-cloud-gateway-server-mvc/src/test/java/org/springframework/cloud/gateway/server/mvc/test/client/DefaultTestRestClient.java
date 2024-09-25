/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.test.client;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.AssertionErrors;
import org.springframework.test.util.ExceptionCollector;
import org.springframework.test.util.JsonExpectationsHelper;
import org.springframework.test.util.XmlExpectationsHelper;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MimeType;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriBuilderFactory;

public class DefaultTestRestClient implements TestRestClient {

	private final TestRestTemplate testRestTemplate;

	private final UriBuilderFactory uriBuilderFactory;

	private final Consumer<EntityExchangeResult<?>> entityResultConsumer;

	private final AtomicLong requestIndex = new AtomicLong();

	public DefaultTestRestClient(TestRestTemplate testRestTemplate, UriBuilderFactory uriBuilderFactory,
			Consumer<EntityExchangeResult<?>> entityResultConsumer) {
		this.uriBuilderFactory = uriBuilderFactory;
		this.testRestTemplate = testRestTemplate;
		this.entityResultConsumer = entityResultConsumer;
	}

	@Override
	public RequestHeadersUriSpec<?> get() {
		return methodInternal(HttpMethod.GET);
	}

	@Override
	public RequestHeadersUriSpec<?> head() {
		return methodInternal(HttpMethod.HEAD);
	}

	@Override
	public RequestBodyUriSpec post() {
		return methodInternal(HttpMethod.POST);
	}

	@Override
	public RequestBodyUriSpec put() {
		return methodInternal(HttpMethod.PUT);
	}

	@Override
	public RequestBodyUriSpec patch() {
		return methodInternal(HttpMethod.PATCH);
	}

	@Override
	public RequestHeadersUriSpec<?> delete() {
		return methodInternal(HttpMethod.DELETE);
	}

	@Override
	public RequestHeadersUriSpec<?> options() {
		return methodInternal(HttpMethod.OPTIONS);
	}

	private RequestBodyUriSpec methodInternal(HttpMethod httpMethod) {
		return new DefaultRequestBodyUriSpec(httpMethod);
	}

	private class DefaultRequestBodyUriSpec implements RequestBodyUriSpec {

		private final HttpMethod httpMethod;

		@Nullable
		private URI uri;

		private final HttpHeaders headers;

		@Nullable
		private MultiValueMap<String, String> cookies;

		private final Map<String, Object> attributes = new LinkedHashMap<>(4);

		@Nullable
		private Consumer<ClientHttpRequest> httpRequestConsumer;

		@Nullable
		private String uriTemplate;

		private final String requestId;

		private Object body;

		private Class<?> type;

		DefaultRequestBodyUriSpec(HttpMethod httpMethod) {
			this.httpMethod = httpMethod;
			this.requestId = String.valueOf(requestIndex.incrementAndGet());
			this.headers = new HttpHeaders();
			this.headers.add(TESTRESTCLIENT_REQUEST_ID, this.requestId);
		}

		@Override
		public RequestBodySpec uri(String uriTemplate, Object... uriVariables) {
			this.uriTemplate = uriTemplate;
			return uri(uriBuilderFactory.expand(uriTemplate, uriVariables));
		}

		@Override
		public RequestBodySpec uri(String uriTemplate, Map<String, ?> uriVariables) {
			this.uriTemplate = uriTemplate;
			return uri(uriBuilderFactory.expand(uriTemplate, uriVariables));
		}

		@Override
		public RequestBodySpec uri(Function<UriBuilder, URI> uriFunction) {
			this.uriTemplate = null;
			return uri(uriFunction.apply(uriBuilderFactory.builder()));
		}

		@Override
		public RequestBodySpec uri(URI uri) {
			this.uri = uri;
			return this;
		}

		private HttpHeaders getHeaders() {
			return this.headers;
		}

		private MultiValueMap<String, String> getCookies() {
			if (this.cookies == null) {
				this.cookies = new LinkedMultiValueMap<>(3);
			}
			return this.cookies;
		}

		@Override
		public RequestBodySpec header(String headerName, String... headerValues) {
			for (String headerValue : headerValues) {
				getHeaders().add(headerName, headerValue);
			}
			return this;
		}

		@Override
		public RequestBodySpec headers(Consumer<HttpHeaders> headersConsumer) {
			headersConsumer.accept(getHeaders());
			return this;
		}

		@Override
		public RequestBodySpec attribute(String name, Object value) {
			this.attributes.put(name, value);
			return this;
		}

		@Override
		public RequestBodySpec attributes(Consumer<Map<String, Object>> attributesConsumer) {
			attributesConsumer.accept(this.attributes);
			return this;
		}

		@Override
		public RequestBodySpec accept(MediaType... acceptableMediaTypes) {
			getHeaders().setAccept(Arrays.asList(acceptableMediaTypes));
			return this;
		}

		@Override
		public RequestBodySpec acceptCharset(Charset... acceptableCharsets) {
			getHeaders().setAcceptCharset(Arrays.asList(acceptableCharsets));
			return this;
		}

		@Override
		public RequestBodySpec contentType(MediaType contentType) {
			getHeaders().setContentType(contentType);
			return this;
		}

		@Override
		public RequestBodySpec contentLength(long contentLength) {
			getHeaders().setContentLength(contentLength);
			return this;
		}

		@Override
		public RequestBodySpec cookie(String name, String value) {
			getCookies().add(name, value);
			return this;
		}

		@Override
		public RequestBodySpec cookies(Consumer<MultiValueMap<String, String>> cookiesConsumer) {
			cookiesConsumer.accept(getCookies());
			return this;
		}

		@Override
		public RequestBodySpec ifModifiedSince(ZonedDateTime ifModifiedSince) {
			getHeaders().setIfModifiedSince(ifModifiedSince);
			return this;
		}

		@Override
		public RequestBodySpec ifNoneMatch(String... ifNoneMatches) {
			getHeaders().setIfNoneMatch(Arrays.asList(ifNoneMatches));
			return this;
		}

		@Override
		public RequestHeadersSpec<?> bodyValue(Object body) {
			this.body = body;
			return this;
		}

		@Override
		public RequestHeadersSpec<?> body(Object body, Class<?> elementClass) {
			this.body = body;
			this.type = elementClass;
			return this;
		}

		@Override
		public RequestHeadersSpec<?> body(Object body, ParameterizedTypeReference<?> elementTypeRef) {
			this.body = body;
			return this;
		}

		@Override
		public ResponseSpec exchange() {
			HttpHeaders combinedHeaders = new HttpHeaders();
			combinedHeaders.putAll(getHeaders());
			if (!ObjectUtils.isEmpty(cookies)) {
				cookies.forEach((name, values) -> {
					values.forEach(value -> {
						combinedHeaders.add("Cookie", name + "=" + value);
					});
				});
			}
			RequestEntity.BodyBuilder builder = RequestEntity.method(httpMethod, uri).headers(combinedHeaders);
			RequestEntity<Object> request = builder.body(body, type);
			ResponseEntity<byte[]> response = testRestTemplate.exchange(request, byte[].class);
			ExchangeResult exchangeResult = new ExchangeResult(request, response);
			return new DefaultResponseSpec(exchangeResult, response, DefaultTestRestClient.this.entityResultConsumer);
		}

	}

	private class DefaultResponseSpec implements ResponseSpec {

		private final ExchangeResult exchangeResult;

		private final ResponseEntity<byte[]> responseEntity;

		private final Consumer<EntityExchangeResult<?>> entityResultConsumer;

		DefaultResponseSpec(ExchangeResult exchangeResult, ResponseEntity<byte[]> responseEntity,
				Consumer<EntityExchangeResult<?>> entityResultConsumer) {
			this.exchangeResult = exchangeResult;
			this.responseEntity = responseEntity;
			this.entityResultConsumer = entityResultConsumer;
		}

		@Override
		public StatusAssertions expectStatus() {
			return new StatusAssertions(this.exchangeResult, this);
		}

		@Override
		public HeaderAssertions expectHeader() {
			return new HeaderAssertions(this.exchangeResult, this);
		}

		@Override
		public CookieAssertions expectCookie() {
			return new CookieAssertions(this.exchangeResult, this);
		}

		@Override
		public <B> BodySpec<B, ?> expectBody(Class<B> bodyType) {
			HttpMessageConverterExtractor<B> httpMessageConverterExtractor = new HttpMessageConverterExtractor<>(
					bodyType, DefaultTestRestClient.this.testRestTemplate.getRestTemplate().getMessageConverters());
			try {
				MockClientHttpResponse mockResponse = new MockClientHttpResponse(this.responseEntity.getBody(),
						this.responseEntity.getStatusCode());
				mockResponse.getHeaders().putAll(this.responseEntity.getHeaders());
				B body = httpMessageConverterExtractor.extractData(mockResponse);
				EntityExchangeResult<B> entityResult = initEntityExchangeResult(body);
				return new DefaultBodySpec<>(entityResult);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public <B> BodySpec<B, ?> expectBody(ParameterizedTypeReference<B> bodyType) {
			throw new UnsupportedOperationException("expectBody(ParameterizedTypeReference<B> bodyType)");
			/*
			 * B body =
			 * DefaultConversionService.getSharedInstance().convert(this.responseEntity.
			 * getBody(), bodyType); EntityExchangeResult<B> entityResult =
			 * initEntityExchangeResult(body); return new DefaultBodySpec<>(entityResult);
			 */
		}

		private <B> EntityExchangeResult<B> initEntityExchangeResult(@Nullable B body) {
			EntityExchangeResult<B> result = new EntityExchangeResult<>(this.exchangeResult, body);
			result.assertWithDiagnostics(() -> this.entityResultConsumer.accept(result));
			return result;
		}

		@Override
		public <E> ListBodySpec<E> expectBodyList(Class<E> elementType) {
			return null;
		}

		@Override
		public <E> ListBodySpec<E> expectBodyList(ParameterizedTypeReference<E> elementType) {
			return null;
		}

		@Override
		public BodyContentSpec expectBody() {
			return new DefaultBodyContentSpec(null);
		}

		/*
		 * @Override public <T> FluxExchangeResult<T> returnResult(Class<T> elementClass)
		 * { return null; }
		 *
		 * @Override public <T> FluxExchangeResult<T>
		 * returnResult(ParameterizedTypeReference<T> elementTypeRef) { return null; }
		 */

		@Override
		public TestRestClient.ResponseSpec expectAll(TestRestClient.ResponseSpec.ResponseSpecConsumer... consumers) {
			ExceptionCollector exceptionCollector = new ExceptionCollector();
			for (TestRestClient.ResponseSpec.ResponseSpecConsumer consumer : consumers) {
				exceptionCollector.execute(() -> consumer.accept(this));
			}
			try {
				exceptionCollector.assertEmpty();
			}
			catch (RuntimeException ex) {
				throw ex;
			}
			catch (Exception ex) {
				// In theory, a ResponseSpecConsumer should never throw an Exception
				// that is not a RuntimeException, but since ExceptionCollector may
				// throw a checked Exception, we handle this to appease the compiler
				// and in case someone uses a "sneaky throws" technique.
				AssertionError assertionError = new AssertionError(ex.getMessage());
				assertionError.initCause(ex);
				throw assertionError;
			}
			return this;
		}

	}

	private static class DefaultBodySpec<B, S extends TestRestClient.BodySpec<B, S>>
			implements TestRestClient.BodySpec<B, S> {

		private final EntityExchangeResult<B> result;

		DefaultBodySpec(EntityExchangeResult<B> result) {
			this.result = result;
		}

		protected EntityExchangeResult<B> getResult() {
			return this.result;
		}

		@Override
		public <T extends S> T isEqualTo(B expected) {
			this.result.assertWithDiagnostics(
					() -> AssertionErrors.assertEquals("Response body", expected, this.result.getResponseBody()));
			return self();
		}

		@Override
		public <T extends S> T value(Matcher<? super B> matcher) {
			this.result.assertWithDiagnostics(() -> MatcherAssert.assertThat(this.result.getResponseBody(), matcher));
			return self();
		}

		@Override
		public <T extends S, R> T value(Function<B, R> bodyMapper, Matcher<? super R> matcher) {
			this.result.assertWithDiagnostics(() -> {
				B body = this.result.getResponseBody();
				MatcherAssert.assertThat(bodyMapper.apply(body), matcher);
			});
			return self();
		}

		@Override
		public <T extends S> T value(Consumer<B> consumer) {
			this.result.assertWithDiagnostics(() -> consumer.accept(this.result.getResponseBody()));
			return self();
		}

		@Override
		public <T extends S> T consumeWith(Consumer<EntityExchangeResult<B>> consumer) {
			this.result.assertWithDiagnostics(() -> consumer.accept(this.result));
			return self();
		}

		@SuppressWarnings("unchecked")
		private <T extends S> T self() {
			return (T) this;
		}

		@Override
		public EntityExchangeResult<B> returnResult() {
			return this.result;
		}

	}

	private static class DefaultListBodySpec<E>
			extends DefaultTestRestClient.DefaultBodySpec<List<E>, TestRestClient.ListBodySpec<E>>
			implements TestRestClient.ListBodySpec<E> {

		DefaultListBodySpec(EntityExchangeResult<List<E>> result) {
			super(result);
		}

		@Override
		public TestRestClient.ListBodySpec<E> hasSize(int size) {
			List<E> actual = getResult().getResponseBody();
			String message = "Response body does not contain " + size + " elements";
			getResult().assertWithDiagnostics(
					() -> AssertionErrors.assertEquals(message, size, (actual != null ? actual.size() : 0)));
			return this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public TestRestClient.ListBodySpec<E> contains(E... elements) {
			List<E> expected = Arrays.asList(elements);
			List<E> actual = getResult().getResponseBody();
			String message = "Response body does not contain " + expected;
			getResult().assertWithDiagnostics(
					() -> AssertionErrors.assertTrue(message, (actual != null && actual.containsAll(expected))));
			return this;
		}

		@Override
		@SuppressWarnings("unchecked")
		public TestRestClient.ListBodySpec<E> doesNotContain(E... elements) {
			List<E> expected = Arrays.asList(elements);
			List<E> actual = getResult().getResponseBody();
			String message = "Response body should not have contained " + expected;
			getResult().assertWithDiagnostics(
					() -> AssertionErrors.assertTrue(message, (actual == null || !actual.containsAll(expected))));
			return this;
		}

		@Override
		public EntityExchangeResult<List<E>> returnResult() {
			return getResult();
		}

	}

	private static class DefaultBodyContentSpec implements TestRestClient.BodyContentSpec {

		private final EntityExchangeResult<byte[]> result;

		private final boolean isEmpty;

		DefaultBodyContentSpec(EntityExchangeResult<byte[]> result) {
			this.result = result;
			this.isEmpty = (result.getResponseBody() == null || result.getResponseBody().length == 0);
		}

		@Override
		public EntityExchangeResult<Void> isEmpty() {
			this.result.assertWithDiagnostics(() -> AssertionErrors.assertTrue("Expected empty body", this.isEmpty));
			return new EntityExchangeResult<>(this.result, null);
		}

		@Override
		public TestRestClient.BodyContentSpec json(String json, boolean strict) {
			this.result.assertWithDiagnostics(() -> {
				try {
					new JsonExpectationsHelper().assertJsonEqual(json, getBodyAsString(), strict);
				}
				catch (Exception ex) {
					throw new AssertionError("JSON parsing error", ex);
				}
			});
			return this;
		}

		@Override
		public TestRestClient.BodyContentSpec xml(String expectedXml) {
			this.result.assertWithDiagnostics(() -> {
				try {
					new XmlExpectationsHelper().assertXmlEqual(expectedXml, getBodyAsString());
				}
				catch (Exception ex) {
					throw new AssertionError("XML parsing error", ex);
				}
			});
			return this;
		}

		@Override
		public JsonPathAssertions jsonPath(String expression, Object... args) {
			return new JsonPathAssertions(this, getBodyAsString(), expression, args);
		}

		@Override
		public XpathAssertions xpath(String expression, @Nullable Map<String, String> namespaces, Object... args) {
			return new XpathAssertions(this, expression, namespaces, args);
		}

		private String getBodyAsString() {
			byte[] body = this.result.getResponseBody();
			if (body == null || body.length == 0) {
				return "";
			}
			Charset charset = Optional.ofNullable(this.result.getResponseHeaders().getContentType())
				.map(MimeType::getCharset)
				.orElse(StandardCharsets.UTF_8);
			return new String(body, charset);
		}

		@Override
		public TestRestClient.BodyContentSpec consumeWith(Consumer<EntityExchangeResult<byte[]>> consumer) {
			this.result.assertWithDiagnostics(() -> consumer.accept(this.result));
			return this;
		}

		@Override
		public EntityExchangeResult<byte[]> returnResult() {
			return this.result;
		}

	}

}
