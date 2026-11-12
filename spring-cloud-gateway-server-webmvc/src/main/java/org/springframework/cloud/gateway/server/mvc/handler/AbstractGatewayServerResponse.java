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

package org.springframework.cloud.gateway.server.mvc.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.ModelAndView;

/**
 * Abstract base class for {@link ServerResponse} implementations.
 *
 * @author Arjen Poutsma
 * @since 5.3.2
 */
abstract class AbstractGatewayServerResponse extends GatewayErrorHandlingServerResponse
		implements GatewayServerResponse {

	private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD);

	private HttpStatusCode statusCode;

	private final HttpHeaders headers;

	private final MultiValueMap<String, Cookie> cookies;

	protected AbstractGatewayServerResponse(HttpStatusCode statusCode, HttpHeaders headers,
			MultiValueMap<String, Cookie> cookies) {

		this.statusCode = statusCode;
		this.headers = HttpHeaders.copyOf(headers);
		this.cookies = new LinkedMultiValueMap<>(cookies);
	}

	@Override
	public final HttpStatusCode statusCode() {
		return this.statusCode;
	}

	@Override
	public void setStatusCode(HttpStatusCode statusCode) {
		this.statusCode = statusCode;
	}

	@Override
	public final HttpHeaders headers() {
		return this.headers;
	}

	@Override
	public MultiValueMap<String, Cookie> cookies() {
		return this.cookies;
	}

	@Override
	public @Nullable ModelAndView writeTo(HttpServletRequest request, HttpServletResponse response, Context context)
			throws ServletException, IOException {

		try {
			writeStatusAndHeaders(response);

			long lastModified = headers().getLastModified();
			ServletWebRequest servletWebRequest = new ServletWebRequest(request, response);
			HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod());
			if (SAFE_METHODS.contains(httpMethod)
					&& servletWebRequest.checkNotModified(headers().getETag(), lastModified)) {
				// Not-modified short-circuit skips writeToInternal, which is where
				// RestClientProxyExchange / ClientHttpRequestFactoryProxyExchange
				// close the upstream ClientHttpResponse and return the connection
				// to the pool (GH-4259).
				closeClientResponse(request);
				return null;
			}
			else {
				return writeToInternal(request, response, context);
			}
		}
		catch (Throwable throwable) {
			return handleError(throwable, request, response, context);
		}
	}

	/**
	 * Closes a proxied {@link ClientHttpResponse} stored on the request when the response
	 * body will not be written (for example HTTP 304 Not Modified).
	 * @param request the current servlet request
	 */
	private static void closeClientResponse(HttpServletRequest request) {
		Object clientResponse = request.getAttribute(MvcUtils.CLIENT_RESPONSE_ATTR);
		if (clientResponse instanceof ClientHttpResponse clientHttpResponse) {
			clientHttpResponse.close();
			request.removeAttribute(MvcUtils.CLIENT_RESPONSE_ATTR);
		}
	}

	private void writeStatusAndHeaders(HttpServletResponse response) {
		response.setStatus(this.statusCode.value());
		writeHeaders(response);
		writeCookies(response);
	}

	private void writeHeaders(HttpServletResponse servletResponse) {
		this.headers.forEach((headerName, headerValues) -> {
			for (String headerValue : headerValues) {
				servletResponse.addHeader(headerName, headerValue);
			}
		});
		// HttpServletResponse exposes some headers as properties: we should include those
		// if not already present
		if (servletResponse.getContentType() == null && this.headers.getContentType() != null) {
			servletResponse.setContentType(this.headers.getContentType().toString());
		}
		if (servletResponse.getCharacterEncoding() == null && this.headers.getContentType() != null
				&& this.headers.getContentType().getCharset() != null) {
			servletResponse.setCharacterEncoding(this.headers.getContentType().getCharset().name());
		}
	}

	private void writeCookies(HttpServletResponse servletResponse) {
		this.cookies.values().stream().flatMap(Collection::stream).forEach(servletResponse::addCookie);
	}

	protected abstract @Nullable ModelAndView writeToInternal(HttpServletRequest request, HttpServletResponse response,
			Context context) throws Exception;

}
