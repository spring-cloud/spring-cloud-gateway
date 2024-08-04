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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Container for request and response details for exchanges performed through
 * {@link WebTestClient}.
 *
 * <p>
 * Note that a decoded response body is not exposed at this level since the body may not
 * have been decoded and consumed yet. Subtypes {@link EntityExchangeResult} and
 * {@link FluxExchangeResult} provide access to a decoded response entity and a decoded
 * (but not consumed) response body respectively.
 *
 * @author Rossen Stoyanchev
 * @author Sam Brannen
 * @since 5.0
 * @see EntityExchangeResult
 * @see FluxExchangeResult
 */
public class ExchangeResult {

	private static final Log logger = LogFactory.getLog(ExchangeResult.class);

	private static final List<MediaType> PRINTABLE_MEDIA_TYPES = List.of(MediaType.parseMediaType("application/*+json"),
			MediaType.APPLICATION_XML, MediaType.parseMediaType("text/*"), MediaType.APPLICATION_FORM_URLENCODED);

	private final RequestEntity request;

	private final ResponseEntity response;

	private final byte[] requestBody;

	private final byte[] responseBody;

	private final Duration timeout;

	@Nullable
	private final String uriTemplate;

	@Nullable
	private final Object mockServerResult;

	/** Ensure single logging, e.g. for expectAll. */
	private boolean diagnosticsLogged;

	/**
	 * Create an instance with an HTTP request and response along with promises for the
	 * serialized request and response body content.
	 * @param request the HTTP request
	 * @param response the HTTP response
	 */
	ExchangeResult(RequestEntity request, ResponseEntity response) {
		this(request, response, new byte[0], new byte[0], null, null, null);
	}

	/**
	 * Create an instance with an HTTP request and response along with promises for the
	 * serialized request and response body content.
	 * @param request the HTTP request
	 * @param response the HTTP response
	 * @param requestBody capture of serialized request body content
	 * @param responseBody capture of serialized response body content
	 * @param timeout how long to wait for content to materialize
	 * @param uriTemplate the URI template used to set up the request, if any
	 * @param serverResult the result of a mock server exchange if applicable.
	 */
	ExchangeResult(RequestEntity request, ResponseEntity response, byte[] requestBody, byte[] responseBody,
			Duration timeout, @Nullable String uriTemplate, @Nullable Object serverResult) {

		Assert.notNull(request, "ClientHttpRequest is required");
		Assert.notNull(response, "ClientHttpResponse is required");
		Assert.notNull(requestBody, "'requestBody' is required");
		Assert.notNull(responseBody, "'responseBody' is required");

		this.request = request;
		this.response = response;
		this.requestBody = requestBody;
		this.responseBody = responseBody;
		this.timeout = timeout;
		this.uriTemplate = uriTemplate;
		this.mockServerResult = serverResult;
	}

	/**
	 * Copy constructor to use after body is decoded and/or consumed.
	 */
	ExchangeResult(ExchangeResult other) {
		this.request = other.request;
		this.response = other.response;
		this.requestBody = other.requestBody;
		this.responseBody = other.responseBody;
		this.timeout = other.timeout;
		this.uriTemplate = other.uriTemplate;
		this.mockServerResult = other.mockServerResult;
		this.diagnosticsLogged = other.diagnosticsLogged;
	}

	/**
	 * Return the method of the request.
	 */
	public HttpMethod getMethod() {
		return this.request.getMethod();
	}

	/**
	 * Return the URI of the request.
	 */
	public URI getUrl() {
		return this.request.getUrl();
	}

	/**
	 * Return the original URI template used to prepare the request, if any.
	 */
	@Nullable
	public String getUriTemplate() {
		return this.uriTemplate;
	}

	/**
	 * Return the request headers sent to the server.
	 */
	public HttpHeaders getRequestHeaders() {
		return this.request.getHeaders();
	}

	/**
	 * Return the raw request body content written through the request.
	 * <p>
	 * <strong>Note:</strong> If the request content has not been consumed for any reason
	 * yet, use of this method will trigger consumption.
	 * @throws IllegalStateException if the request body has not been fully written.
	 */
	@Nullable
	public byte[] getRequestBodyContent() {
		return this.requestBody;
	}

	/**
	 * Return the HTTP status code as an {@link HttpStatusCode} value.
	 */
	public HttpStatusCode getStatus() {
		return this.response.getStatusCode();
	}

	/**
	 * Return the HTTP status code as an integer.
	 * @since 5.1.10
	 * @deprecated as of 6.0, in favor of {@link #getStatus()}
	 */
	@Deprecated(since = "6.0", forRemoval = true)
	public int getRawStatusCode() {
		return getStatus().value();
	}

	/**
	 * Return the response headers received from the server.
	 */
	public HttpHeaders getResponseHeaders() {
		return this.response.getHeaders();
	}

	/**
	 * Return response cookies received from the server.
	 */
	public MultiValueMap<String, ResponseCookie> getResponseCookies() {
		return new LinkedMultiValueMap<>(); // TODO: getResponseCookies
	}

	/**
	 * Return the raw request body content written to the response.
	 * <p>
	 * <strong>Note:</strong> If the response content has not been consumed yet, use of
	 * this method will trigger consumption.
	 * @throws IllegalStateException if the response has not been fully read.
	 */
	@Nullable
	public byte[] getResponseBodyContent() {
		return this.responseBody;
	}

	/**
	 * Return the result from the mock server exchange, if applicable, for further
	 * assertions on the state of the server response.
	 * @since 5.3
	 * @see org.springframework.test.web.servlet.client.MockMvcWebTestClient#resultActionsFor(ExchangeResult)
	 */
	@Nullable
	public Object getMockServerResult() {
		return this.mockServerResult;
	}

	/**
	 * Execute the given Runnable, catch any {@link AssertionError}, log details about the
	 * request and response at ERROR level under the class log category, and after that
	 * re-throw the error.
	 */
	public void assertWithDiagnostics(Runnable assertion) {
		try {
			assertion.run();
		}
		catch (AssertionError ex) {
			if (!this.diagnosticsLogged && logger.isErrorEnabled()) {
				this.diagnosticsLogged = true;
				logger.error("Request details for assertion failure:\n" + this);
			}
			throw ex;
		}
	}

	@Override
	public String toString() {
		return "\n" + "> " + getMethod() + " " + getUrl() + "\n" + "> " + formatHeaders(getRequestHeaders(), "\n> ")
				+ "\n" + "\n" + formatBody(getRequestHeaders().getContentType(), this.requestBody) + "\n" + "\n" + "< "
				+ formatStatus(getStatus()) + "\n" + "< " + formatHeaders(getResponseHeaders(), "\n< ") + "\n" + "\n"
				+ formatBody(getResponseHeaders().getContentType(), this.responseBody) + "\n"
				+ formatMockServerResult();
	}

	private String formatStatus(HttpStatusCode statusCode) {
		String result = statusCode.toString();
		if (statusCode instanceof HttpStatus status) {
			result += " " + status.getReasonPhrase();
		}
		return result;
	}

	private String formatHeaders(HttpHeaders headers, String delimiter) {
		return headers.entrySet()
			.stream()
			.map(entry -> entry.getKey() + ": " + entry.getValue())
			.collect(Collectors.joining(delimiter));
	}

	@Nullable
	private String formatBody(@Nullable MediaType contentType, byte[] bytes) {
		if (bytes == null) {
			return "No content";
		}
		if (contentType == null) {
			return bytes.length + " bytes of content (unknown content-type).";
		}
		Charset charset = contentType.getCharset();
		if (charset != null) {
			return new String(bytes, charset);
		}
		if (PRINTABLE_MEDIA_TYPES.stream().anyMatch(contentType::isCompatibleWith)) {
			return new String(bytes, StandardCharsets.UTF_8);
		}
		return bytes.length + " bytes of content.";
	}

	private String formatMockServerResult() {
		return (this.mockServerResult != null
				? "\n======================  MockMvc (Server) ===============================\n" + this.mockServerResult
						+ "\n"
				: "");
	}

}
