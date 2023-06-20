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

import java.net.URI;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hamcrest.Matcher;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriBuilderFactory;

public interface TestRestClient {

	/**
	 * The name of a request header used to assign a unique id to every request performed
	 * through the {@code WebTestClient}. This can be useful for storing contextual
	 * information at all phases of request processing (e.g. from a server-side component)
	 * under that id and later to look up that information once an {@link ExchangeResult}
	 * is available.
	 */
	String TESTRESTCLIENT_REQUEST_ID = "TestRestClient-Request-Id";

	/**
	 * Prepare an HTTP GET request.
	 * @return a spec for specifying the target URL
	 */
	RequestHeadersUriSpec<?> get();

	/**
	 * Prepare an HTTP HEAD request.
	 * @return a spec for specifying the target URL
	 */
	RequestHeadersUriSpec<?> head();

	/**
	 * Prepare an HTTP POST request.
	 * @return a spec for specifying the target URL
	 */
	RequestBodyUriSpec post();

	/**
	 * Prepare an HTTP PUT request.
	 * @return a spec for specifying the target URL
	 */
	RequestBodyUriSpec put();

	/**
	 * Prepare an HTTP PATCH request.
	 * @return a spec for specifying the target URL
	 */
	RequestBodyUriSpec patch();

	/**
	 * Prepare an HTTP DELETE request.
	 * @return a spec for specifying the target URL
	 */
	RequestHeadersUriSpec<?> delete();

	/**
	 * Prepare an HTTP OPTIONS request.
	 * @return a spec for specifying the target URL
	 */
	RequestHeadersUriSpec<?> options();

	/**
	 * Prepare a request for the specified {@code HttpMethod}.
	 *
	 * @return a spec for specifying the target URL
	 */
	// RequestBodyUriSpec method(HttpMethod method);

	interface UriSpec<S extends RequestHeadersSpec<?>> {

		/**
		 * Specify the URI using an absolute, fully constructed {@link java.net.URI}.
		 * <p>
		 * If a {@link UriBuilderFactory} was configured for the client with a base URI,
		 * that base URI will <strong>not</strong> be applied to the supplied
		 * {@code java.net.URI}. If you wish to have a base URI applied to a
		 * {@code java.net.URI} you must invoke either {@link #uri(String, Object...)} or
		 * {@link #uri(String, Map)} &mdash; for example, {@code uri(myUri.toString())}.
		 * @return spec to add headers or perform the exchange
		 */
		S uri(URI uri);

		/**
		 * Specify the URI for the request using a URI template and URI variables.
		 * <p>
		 * If a {@link UriBuilderFactory} was configured for the client (e.g. with a base
		 * URI) it will be used to expand the URI template.
		 * @return spec to add headers or perform the exchange
		 */
		S uri(String uri, Object... uriVariables);

		/**
		 * Specify the URI for the request using a URI template and URI variables.
		 * <p>
		 * If a {@link UriBuilderFactory} was configured for the client (e.g. with a base
		 * URI) it will be used to expand the URI template.
		 * @return spec to add headers or perform the exchange
		 */
		S uri(String uri, Map<String, ?> uriVariables);

		/**
		 * Build the URI for the request with a {@link UriBuilder} obtained through the
		 * {@link UriBuilderFactory} configured for this client.
		 * @return spec to add headers or perform the exchange
		 */
		S uri(Function<UriBuilder, URI> uriFunction);

	}

	/**
	 * Specification for adding request headers and performing an exchange.
	 *
	 * @param <S> a self reference to the spec type
	 */
	interface RequestHeadersSpec<S extends RequestHeadersSpec<S>> {

		/**
		 * Set the list of acceptable {@linkplain MediaType media types}, as specified by
		 * the {@code Accept} header.
		 * @param acceptableMediaTypes the acceptable media types
		 * @return the same instance
		 */
		S accept(MediaType... acceptableMediaTypes);

		/**
		 * Set the list of acceptable {@linkplain Charset charsets}, as specified by the
		 * {@code Accept-Charset} header.
		 * @param acceptableCharsets the acceptable charsets
		 * @return the same instance
		 */
		S acceptCharset(Charset... acceptableCharsets);

		/**
		 * Add a cookie with the given name and value.
		 * @param name the cookie name
		 * @param value the cookie value
		 * @return the same instance
		 */
		S cookie(String name, String value);

		/**
		 * Manipulate this request's cookies with the given consumer. The map provided to
		 * the consumer is "live", so that the consumer can be used to
		 * {@linkplain MultiValueMap#set(Object, Object) overwrite} existing header
		 * values, {@linkplain MultiValueMap#remove(Object) remove} values, or use any of
		 * the other {@link MultiValueMap} methods.
		 * @param cookiesConsumer a function that consumes the cookies map
		 * @return this builder
		 */
		S cookies(Consumer<MultiValueMap<String, String>> cookiesConsumer);

		/**
		 * Set the value of the {@code If-Modified-Since} header.
		 * <p>
		 * The date should be specified as the number of milliseconds since January 1,
		 * 1970 GMT.
		 * @param ifModifiedSince the new value of the header
		 * @return the same instance
		 */
		S ifModifiedSince(ZonedDateTime ifModifiedSince);

		/**
		 * Set the values of the {@code If-None-Match} header.
		 * @param ifNoneMatches the new value of the header
		 * @return the same instance
		 */
		S ifNoneMatch(String... ifNoneMatches);

		/**
		 * Add the given, single header value under the given name.
		 * @param headerName the header name
		 * @param headerValues the header value(s)
		 * @return the same instance
		 */
		S header(String headerName, String... headerValues);

		/**
		 * Manipulate the request's headers with the given consumer. The headers provided
		 * to the consumer are "live", so that the consumer can be used to
		 * {@linkplain HttpHeaders#set(String, String) overwrite} existing header values,
		 * {@linkplain HttpHeaders#remove(Object) remove} values, or use any of the other
		 * {@link HttpHeaders} methods.
		 * @param headersConsumer a function that consumes the {@code HttpHeaders}
		 * @return this builder
		 */
		S headers(Consumer<HttpHeaders> headersConsumer);

		/**
		 * Set the attribute with the given name to the given value.
		 * @param name the name of the attribute to add
		 * @param value the value of the attribute to add
		 * @return this builder
		 */
		S attribute(String name, Object value);

		/**
		 * Manipulate the request attributes with the given consumer. The attributes
		 * provided to the consumer are "live", so that the consumer can be used to
		 * inspect attributes, remove attributes, or use any of the other map-provided
		 * methods.
		 * @param attributesConsumer a function that consumes the attributes
		 * @return this builder
		 */
		S attributes(Consumer<Map<String, Object>> attributesConsumer);

		/**
		 * Perform the exchange without a request body.
		 * @return spec for decoding the response
		 */
		ResponseSpec exchange();

	}

	/**
	 * Specification for providing body of a request.
	 */
	interface RequestBodySpec extends RequestHeadersSpec<RequestBodySpec> {

		/**
		 * Set the length of the body in bytes, as specified by the {@code Content-Length}
		 * header.
		 * @param contentLength the content length
		 * @return the same instance
		 * @see HttpHeaders#setContentLength(long)
		 */
		RequestBodySpec contentLength(long contentLength);

		/**
		 * Set the {@linkplain MediaType media type} of the body, as specified by the
		 * {@code Content-Type} header.
		 * @param contentType the content type
		 * @return the same instance
		 * @see HttpHeaders#setContentType(MediaType)
		 */
		RequestBodySpec contentType(MediaType contentType);

		/**
		 * Set the body to the given {@code Object} value. This method invokes the
		 * {@link org.springframework.web.reactive.function.client.WebClient.RequestBodySpec#bodyValue(Object)
		 * bodyValue} method on the underlying {@code WebClient}.
		 * @param body the value to write to the request body
		 * @return spec for further declaration of the request
		 * @since 5.2
		 */
		RequestHeadersSpec<?> bodyValue(Object body);

		/**
		 * Set the body from the given {@code Publisher}. Shortcut for
		 * {@link #body(BodyInserter)} with a {@linkplain BodyInserters#fromPublisher
		 * Publisher inserter}.
		 * @param publisher the request body data
		 * @param elementClass the class of elements contained in the publisher
		 * @param <T> the type of the elements contained in the publisher
		 * @param <S> the type of the {@code Publisher}
		 * @return spec for further declaration of the request
		 */
		// <T, S extends Publisher<T>> RequestHeadersSpec<?> body(S publisher, Class<T>
		// elementClass);

		/**
		 * Variant of {@link #body(Publisher, Class)} that allows providing element type
		 * information with generics.
		 * @param publisher the request body data
		 * @param elementTypeRef the type reference of elements contained in the publisher
		 * @param <T> the type of the elements contained in the publisher
		 * @param <S> the type of the {@code Publisher}
		 * @return spec for further declaration of the request
		 * @since 5.2
		 */
		// <T, S extends Publisher<T>> RequestHeadersSpec<?> body(
		// S publisher, ParameterizedTypeReference<T> elementTypeRef);

		/**
		 * Set the body from the given producer. This method invokes the
		 * {@link org.springframework.web.reactive.function.client.WebClient.RequestBodySpec#body(Object, Class)
		 * body(Object, Class)} method on the underlying {@code WebClient}.
		 * @param producer the producer to write to the request. This must be a
		 * {@link Publisher} or another producer adaptable to a {@code Publisher} via
		 * {@link ReactiveAdapterRegistry}
		 * @param elementClass the class of elements contained in the producer
		 * @return spec for further declaration of the request
		 * @since 5.2
		 */
		RequestHeadersSpec<?> body(Object producer, Class<?> elementClass);

		/**
		 * Set the body from the given producer. This method invokes the
		 * {@link org.springframework.web.reactive.function.client.WebClient.RequestBodySpec#body(Object, ParameterizedTypeReference)
		 * body(Object, ParameterizedTypeReference)} method on the underlying
		 * {@code WebClient}.
		 * @param producer the producer to write to the request. This must be a
		 * {@link Publisher} or another producer adaptable to a {@code Publisher} via
		 * {@link ReactiveAdapterRegistry}
		 * @param elementTypeRef the type reference of elements contained in the producer
		 * @return spec for further declaration of the request
		 * @since 5.2
		 */
		RequestHeadersSpec<?> body(Object producer, ParameterizedTypeReference<?> elementTypeRef);

		/**
		 * Set the body of the request to the given {@code BodyInserter}. This method
		 * invokes the
		 * {@link org.springframework.web.reactive.function.client.WebClient.RequestBodySpec#body(BodyInserter)
		 * body(BodyInserter)} method on the underlying {@code WebClient}.
		 * @param inserter the body inserter to use
		 * @return spec for further declaration of the request
		 * @see org.springframework.web.reactive.function.BodyInserters
		 */
		// RequestHeadersSpec<?> body(BodyInserter<?, ? super ClientHttpRequest>
		// inserter);

	}

	/**
	 * Specification for providing request headers and the URI of a request.
	 *
	 * @param <S> a self reference to the spec type
	 */
	interface RequestHeadersUriSpec<S extends RequestHeadersSpec<S>> extends UriSpec<S>, RequestHeadersSpec<S> {

	}

	/**
	 * Specification for providing the body and the URI of a request.
	 */
	interface RequestBodyUriSpec extends RequestBodySpec, RequestHeadersUriSpec<RequestBodySpec> {

	}

	/**
	 * Chained API for applying assertions to a response.
	 */
	interface ResponseSpec {

		/**
		 * Apply multiple assertions to a response with the given
		 * {@linkplain ResponseSpec.ResponseSpecConsumer consumers}, with the guarantee
		 * that all assertions will be applied even if one or more assertions fails with
		 * an exception.
		 * <p>
		 * If a single {@link Error} or {@link RuntimeException} is thrown, it will be
		 * rethrown.
		 * <p>
		 * If multiple exceptions are thrown, this method will throw an
		 * {@link AssertionError} whose error message is a summary of all the exceptions.
		 * In addition, each exception will be added as a
		 * {@linkplain Throwable#addSuppressed(Throwable) suppressed exception} to the
		 * {@code AssertionError}.
		 * <p>
		 * This feature is similar to the {@code SoftAssertions} support in AssertJ and
		 * the {@code assertAll()} support in JUnit Jupiter.
		 *
		 * <h4>Example</h4> <pre class="code">
		 * get().uri("/hello").exchange()
		 *     .expectAll(
		 *         responseSpec -&gt; responseSpec.expectStatus().isOk(),
		 *         responseSpec -&gt; responseSpec.expectBody(String.class).isEqualTo("Hello, World!")
		 *     );
		 * </pre>
		 * @param consumers the list of {@code ResponseSpec} consumers
		 * @since 5.3.10
		 */
		ResponseSpec expectAll(ResponseSpec.ResponseSpecConsumer... consumers);

		/**
		 * Assertions on the response status.
		 */
		StatusAssertions expectStatus();

		/**
		 * Assertions on the headers of the response.
		 */
		HeaderAssertions expectHeader();

		/**
		 * Assertions on the cookies of the response.
		 * @since 5.3
		 */
		CookieAssertions expectCookie();

		/**
		 * Consume and decode the response body to a single object of type {@code <B>} and
		 * then apply assertions.
		 * @param bodyType the expected body type
		 */
		<B> BodySpec<B, ?> expectBody(Class<B> bodyType);

		/**
		 * Alternative to {@link #expectBody(Class)} that accepts information about a
		 * target type with generics.
		 */
		<B> BodySpec<B, ?> expectBody(ParameterizedTypeReference<B> bodyType);

		/**
		 * Consume and decode the response body to {@code List<E>} and then apply
		 * List-specific assertions.
		 * @param elementType the expected List element type
		 */
		<E> ListBodySpec<E> expectBodyList(Class<E> elementType);

		/**
		 * Alternative to {@link #expectBodyList(Class)} that accepts information about a
		 * target type with generics.
		 */
		<E> ListBodySpec<E> expectBodyList(ParameterizedTypeReference<E> elementType);

		/**
		 * Consume and decode the response body to {@code byte[]} and then apply
		 * assertions on the raw content (e.g. isEmpty, JSONPath, etc.)
		 */
		BodyContentSpec expectBody();

		/**
		 * Exit the chained flow in order to consume the response body externally, e.g.
		 * via {@link reactor.test.StepVerifier}.
		 * <p>
		 * Note that when {@code Void.class} is passed in, the response body is consumed
		 * and released. If no content is expected, then consider using
		 * {@code .expectBody().isEmpty()} instead which asserts that there is no content.
		 */
		// <T> FluxExchangeResult<T> returnResult(Class<T> elementClass);

		/**
		 * Alternative to {@link #returnResult(Class)} that accepts information about a
		 * target type with generics.
		 */
		// <T> FluxExchangeResult<T> returnResult(ParameterizedTypeReference<T>
		// elementTypeRef);

		/**
		 * {@link Consumer} of a {@link ResponseSpec}.
		 *
		 * @since 5.3.10
		 * @see ResponseSpec#expectAll(ResponseSpec.ResponseSpecConsumer...)
		 */
		@FunctionalInterface
		interface ResponseSpecConsumer extends Consumer<ResponseSpec> {

		}

	}

	/**
	 * Spec for expectations on the response body decoded to a single Object.
	 *
	 * @param <S> a self reference to the spec type
	 * @param <B> the body type
	 */
	interface BodySpec<B, S extends BodySpec<B, S>> {

		/**
		 * Assert the extracted body is equal to the given value.
		 */
		<T extends S> T isEqualTo(B expected);

		/**
		 * Assert the extracted body with a {@link Matcher}.
		 * @since 5.1
		 */
		<T extends S> T value(Matcher<? super B> matcher);

		/**
		 * Transform the extracted the body with a function, e.g. extracting a property,
		 * and assert the mapped value with a {@link Matcher}.
		 * @since 5.1
		 */
		<T extends S, R> T value(Function<B, R> bodyMapper, Matcher<? super R> matcher);

		/**
		 * Assert the extracted body with a {@link Consumer}.
		 * @since 5.1
		 */
		<T extends S> T value(Consumer<B> consumer);

		/**
		 * Assert the exchange result with the given {@link Consumer}.
		 */
		<T extends S> T consumeWith(Consumer<EntityExchangeResult<B>> consumer);

		/**
		 * Exit the chained API and return an {@code ExchangeResult} with the decoded
		 * response content.
		 */
		EntityExchangeResult<B> returnResult();

	}

	/**
	 * Spec for expectations on the response body decoded to a List.
	 *
	 * @param <E> the body list element type
	 */
	interface ListBodySpec<E> extends BodySpec<List<E>, ListBodySpec<E>> {

		/**
		 * Assert the extracted list of values is of the given size.
		 * @param size the expected size
		 */
		ListBodySpec<E> hasSize(int size);

		/**
		 * Assert the extracted list of values contains the given elements.
		 * @param elements the elements to check
		 */
		@SuppressWarnings("unchecked")
		ListBodySpec<E> contains(E... elements);

		/**
		 * Assert the extracted list of values doesn't contain the given elements.
		 * @param elements the elements to check
		 */
		@SuppressWarnings("unchecked")
		ListBodySpec<E> doesNotContain(E... elements);

	}

	/**
	 * Spec for expectations on the response body content.
	 */
	interface BodyContentSpec {

		/**
		 * Assert the response body is empty and return the exchange result.
		 */
		EntityExchangeResult<Void> isEmpty();

		/**
		 * Parse the expected and actual response content as JSON and perform a comparison
		 * verifying that they contain the same attribute-value pairs regardless of
		 * formatting with <em>lenient</em> checking (extensible and non-strict array
		 * ordering).
		 * <p>
		 * Use of this method requires the
		 * <a href="https://jsonassert.skyscreamer.org/">JSONassert</a> library to be on
		 * the classpath.
		 * @param expectedJson the expected JSON content
		 * @see #json(String, boolean)
		 */
		default BodyContentSpec json(String expectedJson) {
			return json(expectedJson, false);
		}

		/**
		 * Parse the expected and actual response content as JSON and perform a comparison
		 * verifying that they contain the same attribute-value pairs regardless of
		 * formatting.
		 * <p>
		 * Can compare in two modes, depending on the {@code strict} parameter value:
		 * <ul>
		 * <li>{@code true}: strict checking. Not extensible and strict array
		 * ordering.</li>
		 * <li>{@code false}: lenient checking. Extensible and non-strict array
		 * ordering.</li>
		 * </ul>
		 * <p>
		 * Use of this method requires the
		 * <a href="https://jsonassert.skyscreamer.org/">JSONassert</a> library to be on
		 * the classpath.
		 * @param expectedJson the expected JSON content
		 * @param strict enables strict checking if {@code true}
		 * @since 5.3.16
		 * @see #json(String)
		 */
		BodyContentSpec json(String expectedJson, boolean strict);

		/**
		 * Parse expected and actual response content as XML and assert that the two are
		 * "similar", i.e. they contain the same elements and attributes regardless of
		 * order.
		 * <p>
		 * Use of this method requires the
		 * <a href="https://github.com/xmlunit/xmlunit">XMLUnit</a> library on the
		 * classpath.
		 * @param expectedXml the expected JSON content.
		 * @since 5.1
		 * @see org.springframework.test.util.XmlExpectationsHelper#assertXmlEqual(String,
		 * String)
		 */
		BodyContentSpec xml(String expectedXml);

		/**
		 * Access to response body assertions using a
		 * <a href="https://github.com/jayway/JsonPath">JsonPath</a> expression to inspect
		 * a specific subset of the body.
		 * <p>
		 * The JSON path expression can be a parameterized string using formatting
		 * specifiers as defined in {@link String#format}.
		 * @param expression the JsonPath expression
		 * @param args arguments to parameterize the expression
		 */
		JsonPathAssertions jsonPath(String expression, Object... args);

		/**
		 * Access to response body assertions using an XPath expression to inspect a
		 * specific subset of the body.
		 * <p>
		 * The XPath expression can be a parameterized string using formatting specifiers
		 * as defined in {@link String#format}.
		 * @param expression the XPath expression
		 * @param args arguments to parameterize the expression
		 * @since 5.1
		 * @see #xpath(String, Map, Object...)
		 */
		default XpathAssertions xpath(String expression, Object... args) {
			return xpath(expression, null, args);
		}

		/**
		 * Access to response body assertions with specific namespaces using an XPath
		 * expression to inspect a specific subset of the body.
		 * <p>
		 * The XPath expression can be a parameterized string using formatting specifiers
		 * as defined in {@link String#format}.
		 * @param expression the XPath expression
		 * @param namespaces the namespaces to use
		 * @param args arguments to parameterize the expression
		 * @since 5.1
		 */
		XpathAssertions xpath(String expression, @Nullable Map<String, String> namespaces, Object... args);

		/**
		 * Assert the response body content with the given {@link Consumer}.
		 * @param consumer the consumer for the response body; the input {@code byte[]}
		 * may be {@code null} if there was no response body.
		 */
		BodyContentSpec consumeWith(Consumer<EntityExchangeResult<byte[]>> consumer);

		/**
		 * Exit the chained API and return an {@code ExchangeResult} with the raw response
		 * content.
		 */
		EntityExchangeResult<byte[]> returnResult();

	}

}
