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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriBuilder;

import static org.springframework.cloud.gateway.server.mvc.common.MvcUtils.cacheAndReadBody;
import static org.springframework.cloud.gateway.server.mvc.common.MvcUtils.getAttribute;

public abstract class BodyFilterFunctions {

	private BodyFilterFunctions() {
	}

	public static Function<ServerRequest, ServerRequest> adaptCachedBody() {
		return request -> {
			Object o = getAttribute(request, MvcUtils.CACHED_REQUEST_BODY_ATTR);
			if (o instanceof ByteArrayInputStream body) {
				return wrapRequest(request, body);
			}

			return request;
		};
	}

	private static ServerRequestWrapper wrapRequest(ServerRequest request, byte[] body) {
		return wrapRequest(request, new ByteArrayInputStream(body));
	}

	private static ServerRequestWrapper wrapRequest(ServerRequest request, ByteArrayInputStream body) {
		ByteArrayServletInputStream inputStream = new ByteArrayServletInputStream(body);
		HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request.servletRequest()) {
			@Override
			public ServletInputStream getInputStream() {
				return inputStream;
			}
		};

		return new ServerRequestWrapper(request) {
			@Override
			public HttpServletRequest servletRequest() {
				return wrapper;
			}
		};
	}

	@SuppressWarnings("unchecked")
	public static <T, R> Function<ServerRequest, ServerRequest> modifyRequestBody(Class<T> inClass, Class<R> outClass,
			String newContentType, RewriteFunction<T, R> rewriteFunction) {
		return request -> cacheAndReadBody(request, inClass).map(body -> {
			R convertedBody = rewriteFunction.apply(request, body);
			// TODO: cache converted body

			MediaType contentType = (StringUtils.hasText(newContentType)) ? MediaType.parseMediaType(newContentType)
					: request.headers().contentType().orElse(null);

			List<HttpMessageConverter<?>> httpMessageConverters = request.messageConverters();
			for (HttpMessageConverter<?> messageConverter : httpMessageConverters) {
				if (messageConverter.canWrite(outClass, contentType)) {
					HttpHeaders headers = new HttpHeaders();
					headers.putAll(request.headers().asHttpHeaders());

					// the new content type will be computed by converter
					// and then set in the request decorator
					headers.remove(HttpHeaders.CONTENT_LENGTH);

					// if the body is changing content types, set it here, to the
					// bodyInserter
					// will know about it
					if (contentType != null) {
						headers.setContentType(contentType);
					}
					try {
						ByteArrayHttpOutputMessage outputMessage = new ByteArrayHttpOutputMessage(headers);
						((HttpMessageConverter<R>) messageConverter).write(convertedBody, contentType, outputMessage);
						ServerRequest modified = ServerRequest.from(request)
							.headers(httpHeaders -> httpHeaders.putAll(headers))
							.build();
						return wrapRequest(modified, outputMessage.getBytes());
					}
					catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
			}

			return request;
		}).orElse(request);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T, R> BiFunction<ServerRequest, ServerResponse, ServerResponse> modifyResponseBody(Class<T> inClass,
			Class<R> outClass, String newContentType, RewriteResponseFunction<T, R> rewriteFunction) {
		return (request, response) -> {
			Object o = request.attributes().get(MvcUtils.CLIENT_RESPONSE_INPUT_STREAM_ATTR);
			if (o instanceof InputStream inputStream) {
				try {
					List<HttpMessageConverter<?>> converters = request.messageConverters();
					Optional<HttpMessageConverter<?>> inConverter = converters.stream()
						.filter(c -> c.canRead(inClass, response.headers().getContentType()))
						.findFirst();
					if (inConverter.isEmpty()) {
						// TODO: throw exception?
						return response;
					}
					HttpMessageConverter<?> inputConverter = inConverter.get();
					T input = (T) inputConverter.read((Class) inClass,
							new SimpleInputMessage(inputStream, response.headers()));
					R output = rewriteFunction.apply(request, response, input);

					Optional<HttpMessageConverter<?>> outConverter = converters.stream()
						.filter(c -> c.canWrite(outClass, null))
						.findFirst();
					if (outConverter.isEmpty()) {
						// TODO: throw exception?
						return response;
					}
					HttpMessageConverter<R> byteConverter = (HttpMessageConverter<R>) outConverter.get();
					ByteArrayHttpOutputMessage outputMessage = new ByteArrayHttpOutputMessage(response.headers());
					byteConverter.write(output, null, outputMessage);
					request.attributes()
						.put(MvcUtils.CLIENT_RESPONSE_INPUT_STREAM_ATTR,
								new ByteArrayInputStream(outputMessage.body.toByteArray()));
					if (StringUtils.hasText(newContentType)) {
						response.headers().setContentType(MediaType.parseMediaType(newContentType));
					}
					response.headers().remove(HttpHeaders.CONTENT_LENGTH);
				}
				catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			return response;
		};
	}

	private final static class SimpleInputMessage implements HttpInputMessage {

		private final InputStream inputStream;

		private final HttpHeaders headers;

		private SimpleInputMessage(InputStream inputStream, HttpHeaders headers) {
			this.inputStream = inputStream;
			this.headers = headers;
		}

		@Override
		public InputStream getBody() throws IOException {
			return this.inputStream;
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.headers;
		}

	}

	private final static class ByteArrayHttpOutputMessage implements HttpOutputMessage {

		private final HttpHeaders headers;

		private final ByteArrayOutputStream body;

		private ByteArrayHttpOutputMessage(HttpHeaders headers) {
			this.headers = headers;
			this.body = new ByteArrayOutputStream();
		}

		@Override
		public OutputStream getBody() throws IOException {
			return this.body;
		}

		@Override
		public HttpHeaders getHeaders() {
			return headers;
		}

		public byte[] getBytes() {
			return this.body.toByteArray();
		}

	}

	public interface RewriteFunction<T, R> extends BiFunction<ServerRequest, T, R> {

	}

	public interface RewriteResponseFunction<T, R> {

		R apply(ServerRequest request, ServerResponse response, T t);

	}

	private static class ByteArrayServletInputStream extends ServletInputStream {

		private final ByteArrayInputStream body;

		ByteArrayServletInputStream(ByteArrayInputStream body) {
			body.reset();
			this.body = body;
		}

		@Override
		public boolean isFinished() {
			return body.available() <= 0;
		}

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setReadListener(ReadListener listener) {

		}

		@Override
		public int read() {
			return body.read();
		}

	}

	private static class ServerRequestWrapper implements ServerRequest {

		private final ServerRequest delegate;

		protected ServerRequestWrapper(ServerRequest delegate) {
			this.delegate = delegate;
		}

		@Override
		public <T> T bind(Class<T> bindType) throws BindException {
			return delegate.bind(bindType);
		}

		@Override
		public <T> T bind(Class<T> bindType, Consumer<WebDataBinder> dataBinderCustomizer) throws BindException {
			return delegate.bind(bindType, dataBinderCustomizer);
		}

		@Override
		public HttpMethod method() {
			return delegate.method();
		}

		@Override
		@Deprecated
		public String methodName() {
			return delegate.methodName();
		}

		@Override
		public URI uri() {
			return delegate.uri();
		}

		@Override
		public UriBuilder uriBuilder() {
			return delegate.uriBuilder();
		}

		@Override
		public String path() {
			return delegate.path();
		}

		@Override
		@Deprecated
		public PathContainer pathContainer() {
			return delegate.pathContainer();
		}

		@Override
		public RequestPath requestPath() {
			return delegate.requestPath();
		}

		@Override
		public Headers headers() {
			return delegate.headers();
		}

		@Override
		public MultiValueMap<String, Cookie> cookies() {
			return delegate.cookies();
		}

		@Override
		public Optional<InetSocketAddress> remoteAddress() {
			return delegate.remoteAddress();
		}

		@Override
		public List<HttpMessageConverter<?>> messageConverters() {
			return delegate.messageConverters();
		}

		@Override
		public <T> T body(Class<T> bodyType) throws ServletException, IOException {
			return delegate.body(bodyType);
		}

		@Override
		public <T> T body(ParameterizedTypeReference<T> bodyType) throws ServletException, IOException {
			return delegate.body(bodyType);
		}

		@Override
		public Optional<Object> attribute(String name) {
			return delegate.attribute(name);
		}

		@Override
		public Map<String, Object> attributes() {
			return delegate.attributes();
		}

		@Override
		public Optional<String> param(String name) {
			return delegate.param(name);
		}

		@Override
		public MultiValueMap<String, String> params() {
			return delegate.params();
		}

		@Override
		public MultiValueMap<String, Part> multipartData() throws IOException, ServletException {
			return delegate.multipartData();
		}

		@Override
		public String pathVariable(String name) {
			return delegate.pathVariable(name);
		}

		@Override
		public Map<String, String> pathVariables() {
			return delegate.pathVariables();
		}

		@Override
		public HttpSession session() {
			return delegate.session();
		}

		@Override
		public Optional<Principal> principal() {
			return delegate.principal();
		}

		@Override
		public HttpServletRequest servletRequest() {
			return delegate.servletRequest();
		}

		@Override
		public Optional<ServerResponse> checkNotModified(Instant lastModified) {
			return delegate.checkNotModified(lastModified);
		}

		@Override
		public Optional<ServerResponse> checkNotModified(String etag) {
			return delegate.checkNotModified(etag);
		}

		@Override
		public Optional<ServerResponse> checkNotModified(Instant lastModified, String etag) {
			return delegate.checkNotModified(lastModified, etag);
		}

		public static ServerRequest create(HttpServletRequest servletRequest,
				List<HttpMessageConverter<?>> messageReaders) {
			return ServerRequest.create(servletRequest, messageReaders);
		}

		public static Builder from(ServerRequest other) {
			return ServerRequest.from(other);
		}

	}

}
