/*
 * Copyright 2026-present the original author or authors.
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
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.function.AsyncServerResponse;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.RenderingResponse;
import org.springframework.web.servlet.function.ServerResponse;

class DelegatingServerResponse implements ServerResponse {

	private final ServerResponse delegate;

	private @Nullable HttpHeaders originalHeaders;

	private @Nullable HttpHeaders headers;

	private DelegatingServerResponse(ServerResponse delegate) {
		this.delegate = delegate;
	}

	static ServerResponse adapt(ServerResponse response) {
		if (response instanceof GatewayServerResponse || response instanceof DelegatingServerResponse) {
			return response;
		}
		if (response instanceof EntityResponse<?> entityResponse) {
			return new DelegatingEntityResponse<>(entityResponse);
		}
		if (response instanceof RenderingResponse renderingResponse) {
			return new DelegatingRenderingResponse(renderingResponse);
		}
		if (response instanceof AsyncServerResponse asyncResponse) {
			return new DelegatingAsyncServerResponse(asyncResponse);
		}
		return new DelegatingServerResponse(response);
	}

	ServerResponse delegate() {
		return this.delegate;
	}

	@Override
	public HttpStatusCode statusCode() {
		return this.delegate.statusCode();
	}

	@Override
	public HttpHeaders headers() {
		if (this.headers == null) {
			// An unfinished AsyncServerResponse cannot expose its metadata yet.
			this.originalHeaders = HttpHeaders.copyOf(this.delegate.headers());
			this.headers = HttpHeaders.copyOf(this.originalHeaders);
		}
		return this.headers;
	}

	@Override
	public MultiValueMap<String, Cookie> cookies() {
		return this.delegate.cookies();
	}

	@Override
	public @Nullable ModelAndView writeTo(HttpServletRequest request, HttpServletResponse response, Context context)
			throws ServletException, IOException {
		Set<String> changedNames = changedNames();
		if (changedNames.isEmpty()) {
			return this.delegate.writeTo(request, response, context);
		}
		HttpHeaders effectiveHeaders = headers();
		for (String name : changedNames) {
			List<String> values = effectiveHeaders.getOrEmpty(name);
			response.setHeader(name, values.isEmpty() ? null : values.get(0));
			for (int i = 1; i < values.size(); i++) {
				response.addHeader(name, values.get(i));
			}
		}
		if ((changedNames.contains(HttpHeaders.ETAG) || changedNames.contains(HttpHeaders.LAST_MODIFIED))
				&& ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
			response.setStatus(statusCode().value());
			// Avoid writing validators twice when the delegate replays its headers.
			HttpServletResponse conditionalResponse = new ChangedHeadersResponseWrapper(response,
					Set.of(HttpHeaders.ETAG, HttpHeaders.LAST_MODIFIED));
			if (new ServletWebRequest(request, conditionalResponse).checkNotModified(effectiveHeaders.getETag(),
					effectiveHeaders.getLastModified())) {
				effectiveHeaders.forEach((name, values) -> {
					if (!changedNames.contains(name)) {
						values.forEach(value -> response.addHeader(name, value));
					}
				});
				cookies().values().stream().flatMap(Collection::stream).forEach(response::addCookie);
				return null;
			}
			request = new ConditionalHeadersRequestWrapper(request);
		}
		return this.delegate.writeTo(request, new ChangedHeadersResponseWrapper(response, changedNames), context);
	}

	private Set<String> changedNames() {
		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		if (this.headers != null && this.originalHeaders != null) {
			names.addAll(this.originalHeaders.headerNames());
			names.addAll(this.headers.headerNames());
			names.removeIf(name -> Objects.equals(this.originalHeaders.get(name), this.headers.get(name)));
		}
		return names;
	}

	private static final class ChangedHeadersResponseWrapper extends HttpServletResponseWrapper {

		private final Set<String> changedNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

		private ChangedHeadersResponseWrapper(HttpServletResponse response, Set<String> changedNames) {
			super(response);
			this.changedNames.addAll(changedNames);
		}

		@Override
		public void addHeader(String name, @Nullable String value) {
			if (!this.changedNames.contains(name)) {
				super.addHeader(name, value);
			}
		}

		@Override
		public void setHeader(String name, @Nullable String value) {
			if (!this.changedNames.contains(name)) {
				super.setHeader(name, value);
			}
		}

		@Override
		public void addDateHeader(String name, long date) {
			if (!this.changedNames.contains(name)) {
				super.addDateHeader(name, date);
			}
		}

		@Override
		public void setDateHeader(String name, long date) {
			if (!this.changedNames.contains(name)) {
				super.setDateHeader(name, date);
			}
		}

		@Override
		public void addIntHeader(String name, int value) {
			if (!this.changedNames.contains(name)) {
				super.addIntHeader(name, value);
			}
		}

		@Override
		public void setIntHeader(String name, int value) {
			if (!this.changedNames.contains(name)) {
				super.setIntHeader(name, value);
			}
		}

		@Override
		public void setContentType(@Nullable String type) {
			if (!this.changedNames.contains(HttpHeaders.CONTENT_TYPE)) {
				super.setContentType(type);
			}
		}

		@Override
		public void setContentLength(int length) {
			if (!this.changedNames.contains(HttpHeaders.CONTENT_LENGTH)) {
				super.setContentLength(length);
			}
		}

		@Override
		public void setContentLengthLong(long length) {
			if (!this.changedNames.contains(HttpHeaders.CONTENT_LENGTH)) {
				super.setContentLengthLong(length);
			}
		}

		@Override
		public void setCharacterEncoding(@Nullable String charset) {
			// Servlet charset setters also modify Content-Type.
			if (!this.changedNames.contains(HttpHeaders.CONTENT_TYPE)) {
				super.setCharacterEncoding(charset);
			}
		}

		@Override
		public void setCharacterEncoding(Charset charset) {
			if (!this.changedNames.contains(HttpHeaders.CONTENT_TYPE)) {
				super.setCharacterEncoding(charset);
			}
		}

	}

	private static final class ConditionalHeadersRequestWrapper extends HttpServletRequestWrapper {

		private static final Set<String> CONDITIONAL_HEADERS = Set.of(HttpHeaders.IF_MATCH, HttpHeaders.IF_NONE_MATCH,
				HttpHeaders.IF_MODIFIED_SINCE, HttpHeaders.IF_UNMODIFIED_SINCE);

		private ConditionalHeadersRequestWrapper(HttpServletRequest request) {
			super(request);
		}

		private boolean isConditional(String name) {
			return CONDITIONAL_HEADERS.stream().anyMatch(name::equalsIgnoreCase);
		}

		@Override
		public @Nullable String getHeader(String name) {
			return isConditional(name) ? null : super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			return isConditional(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			return Collections.enumeration(
					Collections.list(super.getHeaderNames()).stream().filter(name -> !isConditional(name)).toList());
		}

		@Override
		public long getDateHeader(String name) {
			return isConditional(name) ? -1 : super.getDateHeader(name);
		}

		@Override
		public int getIntHeader(String name) {
			return isConditional(name) ? -1 : super.getIntHeader(name);
		}

	}

	private static final class DelegatingAsyncServerResponse extends DelegatingServerResponse
			implements AsyncServerResponse {

		private final AsyncServerResponse delegate;

		private DelegatingAsyncServerResponse(AsyncServerResponse delegate) {
			super(delegate);
			this.delegate = delegate;
		}

		@Override
		public ServerResponse block() {
			return this.delegate.block();
		}

	}

	private static final class DelegatingEntityResponse<T> extends DelegatingServerResponse
			implements EntityResponse<T> {

		private final EntityResponse<T> delegate;

		private DelegatingEntityResponse(EntityResponse<T> delegate) {
			super(delegate);
			this.delegate = delegate;
		}

		@Override
		public T entity() {
			return this.delegate.entity();
		}

	}

	private static final class DelegatingRenderingResponse extends DelegatingServerResponse
			implements RenderingResponse {

		private final RenderingResponse delegate;

		private DelegatingRenderingResponse(RenderingResponse delegate) {
			super(delegate);
			this.delegate = delegate;
		}

		@Override
		public String name() {
			return this.delegate.name();
		}

		@Override
		public Map<String, Object> model() {
			return this.delegate.model();
		}

	}

}
