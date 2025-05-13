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

package org.springframework.cloud.gateway.server.mvc.handler;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Default {@link EntityResponse.Builder} implementation.
 *
 * @param <T> the entity type
 * @author Arjen Poutsma
 * @since 5.2
 */
final class GatewayEntityResponseBuilder<T> implements EntityResponse.Builder<T> {

	private static final Type RESOURCE_REGION_LIST_TYPE = new ParameterizedTypeReference<List<ResourceRegion>>() {
	}.getType();

	private final T entity;

	private final Type entityType;

	private HttpStatusCode status = HttpStatus.OK;

	private final HttpHeaders headers = new HttpHeaders();

	private final MultiValueMap<String, Cookie> cookies = new LinkedMultiValueMap<>();

	private GatewayEntityResponseBuilder(T entity, @Nullable Type entityType) {
		this.entity = entity;
		this.entityType = (entityType != null) ? entityType : entity.getClass();
	}

	@Override
	public EntityResponse.Builder<T> status(HttpStatusCode status) {
		Assert.notNull(status, "HttpStatusCode must not be null");
		this.status = status;
		return this;
	}

	@Override
	public EntityResponse.Builder<T> status(int status) {
		return status(HttpStatusCode.valueOf(status));
	}

	@Override
	public EntityResponse.Builder<T> cookie(Cookie cookie) {
		Assert.notNull(cookie, "Cookie must not be null");
		this.cookies.add(cookie.getName(), cookie);
		return this;
	}

	@Override
	public EntityResponse.Builder<T> cookies(Consumer<MultiValueMap<String, Cookie>> cookiesConsumer) {
		cookiesConsumer.accept(this.cookies);
		return this;
	}

	@Override
	public EntityResponse.Builder<T> header(String headerName, String... headerValues) {
		for (String headerValue : headerValues) {
			this.headers.add(headerName, headerValue);
		}
		return this;
	}

	@Override
	public EntityResponse.Builder<T> headers(Consumer<HttpHeaders> headersConsumer) {
		headersConsumer.accept(this.headers);
		return this;
	}

	@Override
	public EntityResponse.Builder<T> allow(HttpMethod... allowedMethods) {
		this.headers.setAllow(new LinkedHashSet<>(Arrays.asList(allowedMethods)));
		return this;
	}

	@Override
	public EntityResponse.Builder<T> allow(Set<HttpMethod> allowedMethods) {
		this.headers.setAllow(allowedMethods);
		return this;
	}

	@Override
	public EntityResponse.Builder<T> contentLength(long contentLength) {
		this.headers.setContentLength(contentLength);
		return this;
	}

	@Override
	public EntityResponse.Builder<T> contentType(MediaType contentType) {
		this.headers.setContentType(contentType);
		return this;
	}

	@Override
	public EntityResponse.Builder<T> eTag(String etag) {
		if (!etag.startsWith("\"") && !etag.startsWith("W/\"")) {
			etag = "\"" + etag;
		}
		if (!etag.endsWith("\"")) {
			etag = etag + "\"";
		}
		this.headers.setETag(etag);
		return this;
	}

	@Override
	public EntityResponse.Builder<T> lastModified(ZonedDateTime lastModified) {
		this.headers.setLastModified(lastModified);
		return this;
	}

	@Override
	public EntityResponse.Builder<T> lastModified(Instant lastModified) {
		this.headers.setLastModified(lastModified);
		return this;
	}

	@Override
	public EntityResponse.Builder<T> location(URI location) {
		this.headers.setLocation(location);
		return this;
	}

	@Override
	public EntityResponse.Builder<T> cacheControl(CacheControl cacheControl) {
		this.headers.setCacheControl(cacheControl);
		return this;
	}

	@Override
	public EntityResponse.Builder<T> varyBy(String... requestHeaders) {
		this.headers.setVary(Arrays.asList(requestHeaders));
		return this;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public EntityResponse<T> build() {
		if (this.entity instanceof CompletionStage completionStage) {
			return new CompletionStageEntityResponse(this.status, this.headers, this.cookies, completionStage,
					this.entityType);
		}
		return new GatewayEntityResponse<>(this.status, this.headers, this.cookies, this.entity, this.entityType);
	}

	/**
	 * Return a new {@link EntityResponse.Builder} from the given object.
	 */
	public static <T> EntityResponse.Builder<T> fromObject(T t) {
		return new GatewayEntityResponseBuilder<>(t, null);
	}

	/**
	 * Return a new {@link EntityResponse.Builder} from the given object and type
	 * reference.
	 */
	public static <T> EntityResponse.Builder<T> fromObject(T t, ParameterizedTypeReference<?> bodyType) {
		return new GatewayEntityResponseBuilder<>(t, bodyType.getType());
	}

	/**
	 * Default {@link EntityResponse} implementation for synchronous bodies.
	 */
	private static class GatewayEntityResponse<T> extends AbstractGatewayServerResponse implements EntityResponse<T> {

		private final T entity;

		private final Type entityType;

		GatewayEntityResponse(HttpStatusCode statusCode, HttpHeaders headers, MultiValueMap<String, Cookie> cookies,
				T entity, Type entityType) {

			super(statusCode, headers, cookies);
			this.entity = entity;
			this.entityType = entityType;
		}

		@Override
		public T entity() {
			return this.entity;
		}

		@Override
		protected ModelAndView writeToInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
				Context context) throws ServletException, IOException {

			writeEntityWithMessageConverters(this.entity, servletRequest, servletResponse, context);
			return null;
		}

		@SuppressWarnings({ "unchecked", "resource", "rawtypes" })
		protected void writeEntityWithMessageConverters(Object entity, HttpServletRequest request,
				HttpServletResponse response, ServerResponse.Context context) throws ServletException, IOException {

			ServletServerHttpResponse serverResponse = new ServletServerHttpResponse(response);
			MediaType contentType = getContentType(response);
			Class<?> entityClass = entity.getClass();
			Type entityType = this.entityType;

			if (entityClass != InputStreamResource.class && Resource.class.isAssignableFrom(entityClass)) {
				serverResponse.getHeaders().set(HttpHeaders.ACCEPT_RANGES, "bytes");
				String rangeHeader = request.getHeader(HttpHeaders.RANGE);
				if (rangeHeader != null) {
					Resource resource = (Resource) entity;
					try {
						List<HttpRange> httpRanges = HttpRange.parseRanges(rangeHeader);
						serverResponse.getServletResponse().setStatus(HttpStatus.PARTIAL_CONTENT.value());
						entity = HttpRange.toResourceRegions(httpRanges, resource);
						entityClass = entity.getClass();
						entityType = RESOURCE_REGION_LIST_TYPE;
					}
					catch (IllegalArgumentException ex) {
						serverResponse.getHeaders()
							.set(HttpHeaders.CONTENT_RANGE, "bytes */" + resource.contentLength());
						serverResponse.getServletResponse()
							.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
					}
				}
			}

			for (HttpMessageConverter<?> messageConverter : context.messageConverters()) {
				if (messageConverter instanceof GenericHttpMessageConverter genericMessageConverter) {
					if (genericMessageConverter.canWrite(entityType, entityClass, contentType)) {
						genericMessageConverter.write(entity, entityType, contentType, serverResponse);
						return;
					}
				}
				if (messageConverter.canWrite(entityClass, contentType)) {
					((HttpMessageConverter<Object>) messageConverter).write(entity, contentType, serverResponse);
					return;
				}
			}

			List<MediaType> producibleMediaTypes = producibleMediaTypes(context.messageConverters(), entityClass);
			throw new HttpMediaTypeNotAcceptableException(producibleMediaTypes);
		}

		@Nullable
		private static MediaType getContentType(HttpServletResponse response) {
			try {
				return MediaType.parseMediaType(response.getContentType()).removeQualityValue();
			}
			catch (InvalidMediaTypeException ex) {
				return null;
			}
		}

		protected void tryWriteEntityWithMessageConverters(Object entity, HttpServletRequest request,
				HttpServletResponse response, ServerResponse.Context context) throws ServletException, IOException {
			try {
				writeEntityWithMessageConverters(entity, request, response, context);
			}
			catch (IOException | ServletException ex) {
				handleError(ex, request, response, context);
			}
		}

		private static List<MediaType> producibleMediaTypes(List<HttpMessageConverter<?>> messageConverters,
				Class<?> entityClass) {

			return messageConverters.stream()
				.filter(messageConverter -> messageConverter.canWrite(entityClass, null))
				.flatMap(messageConverter -> messageConverter.getSupportedMediaTypes(entityClass).stream())
				.toList();
		}

	}

	/**
	 * {@link EntityResponse} implementation for asynchronous {@link CompletionStage}
	 * bodies.
	 */
	private static class CompletionStageEntityResponse<T> extends GatewayEntityResponse<CompletionStage<T>> {

		CompletionStageEntityResponse(HttpStatusCode statusCode, HttpHeaders headers,
				MultiValueMap<String, Cookie> cookies, CompletionStage<T> entity, Type entityType) {

			super(statusCode, headers, cookies, entity, entityType);
		}

		@Override
		protected ModelAndView writeToInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
				Context context) throws ServletException, IOException {

			DeferredResult<ServerResponse> deferredResult = createDeferredResult(servletRequest, servletResponse,
					context);
			GatewayAsyncServerResponse.writeAsync(servletRequest, servletResponse, deferredResult);
			return null;
		}

		private DeferredResult<ServerResponse> createDeferredResult(HttpServletRequest request,
				HttpServletResponse response, Context context) {

			DeferredResult<ServerResponse> result = new DeferredResult<>();
			entity().whenComplete((value, ex) -> {
				if (ex != null) {
					if (ex instanceof CompletionException && ex.getCause() != null) {
						ex = ex.getCause();
					}
					ServerResponse errorResponse = errorResponse(ex, request);
					if (errorResponse != null) {
						result.setResult(errorResponse);
					}
					else {
						result.setErrorResult(ex);
					}
				}
				else {
					try {
						tryWriteEntityWithMessageConverters(value, request, response, context);
						result.setResult(null);
					}
					catch (ServletException | IOException writeException) {
						result.setErrorResult(writeException);
					}
				}
			});
			return result;
		}

	}

}
