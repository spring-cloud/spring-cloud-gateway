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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.server.mvc.common.KeyValues.KeyValue;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.core.log.LogMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;
import org.springframework.web.util.UriUtils;

import static org.springframework.cloud.gateway.server.mvc.common.MvcUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;
import static org.springframework.util.CollectionUtils.unmodifiableMultiValueMap;

// TODO: can TokenRelay be here and not cause CNFE?
public abstract class BeforeFilterFunctions {

	private static final Log log = LogFactory.getLog(BeforeFilterFunctions.class);

	private static final String REQUEST_HEADER_SIZE_ERROR_PREFIX = "Request Header/s size is larger than permissible limit (%s).";

	private static final String REQUEST_HEADER_SIZE_ERROR = " Request Header/s size for '%s' is %s.";

	private static final String REQUEST_SIZE_ERROR_MSG = "Request size is larger than permissible limit. Request size is %s where permissible limit is %s";

	/**
	 * Default CircuitBreaker Fallback Execution Exception Type header name.
	 */
	public static final String CB_EXECUTION_EXCEPTION_TYPE = "Execution-Exception-Type";

	/**
	 * Default CircuitBreaker Fallback Execution Exception Message header name.
	 */
	public static final String CB_EXECUTION_EXCEPTION_MESSAGE = "Execution-Exception-Message";

	/**
	 * Default CircuitBreaker Root Cause Execution Exception Type header name.
	 */
	public static final String CB_ROOT_CAUSE_EXCEPTION_TYPE = "Root-Cause-Exception-Type";

	/**
	 * Default CircuitBreaker Root Cause Execution Exception Message header name.
	 */
	public static final String CB_ROOT_CAUSE_EXCEPTION_MESSAGE = "Root-Cause-Exception-Message";

	private BeforeFilterFunctions() {
	}

	public static Function<ServerRequest, ServerRequest> adaptCachedBody() {
		return BodyFilterFunctions.adaptCachedBody();
	}

	public static Function<ServerRequest, ServerRequest> addRequestHeader(String name, String... values) {
		return request -> {
			String[] expandedValues = MvcUtils.expandMultiple(request, values);
			return ServerRequest.from(request).header(name, expandedValues).build();
		};
	}

	public static Function<ServerRequest, ServerRequest> addRequestHeadersIfNotPresent(String... values) {
		List<KeyValue> keyValues = Arrays.stream(values).map(KeyValue::valueOf).toList();
		return addRequestHeadersIfNotPresent(keyValues);
	}

	public static Function<ServerRequest, ServerRequest> addRequestHeadersIfNotPresent(List<KeyValue> keyValues) {
		HttpHeaders newHeaders = new HttpHeaders();
		keyValues.forEach(keyValue -> newHeaders.add(keyValue.getKey(), keyValue.getValue()));
		return request -> {
			ServerRequest.Builder requestBuilder = ServerRequest.from(request);
			newHeaders.forEach((newHeaderName, newHeaderValues) -> {
				boolean headerIsMissingOrBlank = request.headers()
					.asHttpHeaders()
					.getOrEmpty(newHeaderName)
					.stream()
					.allMatch(h -> !StringUtils.hasText(h));
				if (headerIsMissingOrBlank) {
					requestBuilder.headers(httpHeaders -> {
						List<String> expandedValues = MvcUtils.expandMultiple(request, newHeaderValues);
						httpHeaders.addAll(newHeaderName, expandedValues);
					});
				}
			});
			return requestBuilder.build();
		};
	}

	public static Function<ServerRequest, ServerRequest> addRequestParameter(String name, String... values) {
		return request -> {
			String[] expandedValues = MvcUtils.expandMultiple(request, values);
			return ServerRequest.from(request).param(name, expandedValues).build();
		};
	}

	public static Function<ServerRequest, ServerRequest> fallbackHeaders() {
		return fallbackHeaders(config -> {
		});
	}

	public static Function<ServerRequest, ServerRequest> fallbackHeaders(
			Consumer<FallbackHeadersConfig> configConsumer) {
		FallbackHeadersConfig config = new FallbackHeadersConfig();
		configConsumer.accept(config);
		return request -> request.attribute(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR)
			.map(Throwable.class::cast)
			.map(throwable -> ServerRequest.from(request).headers(httpHeaders -> {
				httpHeaders.add(config.getExecutionExceptionTypeHeaderName(), throwable.getClass().getName());
				if (throwable.getMessage() != null) {
					httpHeaders.add(config.getExecutionExceptionMessageHeaderName(), throwable.getMessage());
				}
				Throwable rootCause = getRootCause(throwable);
				if (rootCause != null) {
					httpHeaders.add(config.getRootCauseExceptionTypeHeaderName(), rootCause.getClass().getName());
					if (rootCause.getMessage() != null) {
						httpHeaders.add(config.getRootCauseExceptionMessageHeaderName(), rootCause.getMessage());
					}
				}
			}).build())
			.orElse(request);
	}

	private static Throwable getRootCause(Throwable throwable) {
		List<Throwable> list = getThrowableList(throwable);
		return list.isEmpty() ? null : list.get(list.size() - 1);
	}

	private static List<Throwable> getThrowableList(Throwable throwable) {
		List<Throwable> list = new ArrayList<>();
		while (throwable != null && !list.contains(throwable)) {
			list.add(throwable);
			throwable = throwable.getCause();
		}
		return list;
	}

	public static Function<ServerRequest, ServerRequest> mapRequestHeader(String fromHeader, String toHeader) {
		return request -> {
			if (request.headers().asHttpHeaders().containsKey(fromHeader)) {
				List<String> values = request.headers().header(fromHeader);
				return ServerRequest.from(request).header(toHeader, values.toArray(new String[0])).build();
			}
			return request;
		};
	}

	public static <T, R> Function<ServerRequest, ServerRequest> modifyRequestBody(Class<T> inClass, Class<R> outClass,
			String newContentType, BodyFilterFunctions.RewriteFunction<T, R> rewriteFunction) {
		return BodyFilterFunctions.modifyRequestBody(inClass, outClass, newContentType, rewriteFunction);
	}

	public static Function<ServerRequest, ServerRequest> prefixPath(String prefix) {
		final UriTemplate uriTemplate = new UriTemplate(prefix);

		return request -> {
			MvcUtils.addOriginalRequestUrl(request, request.uri());
			Map<String, Object> uriVariables = MvcUtils.getUriTemplateVariables(request);
			URI uri = uriTemplate.expand(uriVariables);

			String newPath = uri.getRawPath() + request.uri().getRawPath();

			URI prefixedUri = UriComponentsBuilder.fromUri(request.uri()).replacePath(newPath).build().toUri();
			return ServerRequest.from(request).uri(prefixedUri).build();
		};
	}

	public static Function<ServerRequest, ServerRequest> preserveHostHeader() {
		return request -> {
			MvcUtils.putAttribute(request, MvcUtils.PRESERVE_HOST_HEADER_ATTRIBUTE, true);
			return request;
		};
	}

	public static Function<ServerRequest, ServerRequest> removeRequestHeader(String name) {
		return request -> ServerRequest.from(request).headers(httpHeaders -> httpHeaders.remove(name)).build();
	}

	public static Function<ServerRequest, ServerRequest> removeRequestParameter(String name) {
		return request -> {
			MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>(request.params());
			queryParams.remove(name);

			MultiValueMap<String, String> encodedQueryParams = UriUtils.encodeQueryParams(queryParams);

			// remove from uri
			URI newUri = UriComponentsBuilder.fromUri(request.uri())
				.replaceQueryParams(unmodifiableMultiValueMap(encodedQueryParams))
				.build(true)
				.toUri();

			// remove resolved params from request
			return ServerRequest.from(request).params(params -> params.remove(name)).uri(newUri).build();
		};
	}

	public static Function<ServerRequest, ServerRequest> requestHeaderSize(String maxSize) {
		return requestHeaderSize(DataSize.parse(maxSize));
	}

	public static Function<ServerRequest, ServerRequest> requestHeaderSize(String maxSize, String errorHeaderName) {
		return requestHeaderSize(DataSize.parse(maxSize), errorHeaderName);
	}

	public static Function<ServerRequest, ServerRequest> requestHeaderSize(DataSize maxSize) {
		return requestHeaderSize(maxSize, "errorMessage");
	}

	public static Function<ServerRequest, ServerRequest> requestHeaderSize(DataSize maxSize, String errorHeaderName) {
		Assert.notNull(maxSize, "maxSize may not be null");
		Assert.isTrue(maxSize.toBytes() > 0, "maxSize must be greater than 0");
		Assert.hasText(errorHeaderName, "errorHeaderName may not be empty");
		return request -> {
			HashMap<String, Long> longHeaders = new HashMap<>();

			request.headers().asHttpHeaders().forEach((key, values) -> {
				long headerSizeInBytes = 0L;
				headerSizeInBytes += key.getBytes().length;
				for (String value : values) {
					headerSizeInBytes += value.getBytes().length;
				}
				if (headerSizeInBytes > maxSize.toBytes()) {
					longHeaders.put(key, headerSizeInBytes);
				}
			});

			if (!longHeaders.isEmpty()) {
				StringBuilder errorMessage = new StringBuilder(
						String.format(REQUEST_HEADER_SIZE_ERROR_PREFIX, maxSize));
				longHeaders.forEach((header, size) -> errorMessage
					.append(String.format(REQUEST_HEADER_SIZE_ERROR, header, DataSize.of(size, DataUnit.BYTES))));

				throw new ResponseStatusException(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE, errorMessage.toString()) {
					@Override
					public HttpHeaders getHeaders() {
						HttpHeaders httpHeaders = new HttpHeaders();
						httpHeaders.add(errorHeaderName, errorMessage.toString());
						return httpHeaders;
					}
				};

			}

			return request;
		};
	}

	public static Function<ServerRequest, ServerRequest> requestHeaderToRequestUri(String name) {
		return request -> {
			if (request.headers().asHttpHeaders().containsKey(name)) {
				String newUri = request.headers().firstHeader(name);
				try {
					MvcUtils.setRequestUrl(request, new URI(newUri));
				}
				catch (URISyntaxException e) {
					log.info(LogMessage.format("Request url is invalid : url=%s", newUri), e);
				}
			}
			return request;
		};
	}

	public static Function<ServerRequest, ServerRequest> requestSize(String maxSize) {
		return requestSize(DataSize.parse(maxSize));
	}

	public static Function<ServerRequest, ServerRequest> requestSize(DataSize maxSize) {
		Assert.notNull(maxSize, "maxSize may not be null");
		Assert.isTrue(maxSize.toBytes() > 0, "maxSize must be greater than 0");
		return request -> {
			if (request.headers().asHttpHeaders().containsKey(HttpHeaders.CONTENT_LENGTH)) {
				long contentLength = request.headers().asHttpHeaders().getContentLength();
				if (contentLength > maxSize.toBytes()) {
					String errorMessage = String.format(REQUEST_SIZE_ERROR_MSG, DataSize.ofBytes(contentLength),
							maxSize);
					throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, errorMessage) {
						@Override
						public HttpHeaders getHeaders() {
							HttpHeaders httpHeaders = new HttpHeaders();
							// TODO: customize header name
							httpHeaders.add("errorMessage", errorMessage);
							return httpHeaders;
						}
					};
				}
			}
			return request;
		};
	}

	public static Function<ServerRequest, ServerRequest> rewritePath(String regexp, String replacement) {
		String normalizedReplacement = replacement.replace("$\\", "$");
		Pattern pattern = Pattern.compile(regexp);
		return request -> {
			MvcUtils.addOriginalRequestUrl(request, request.uri());
			String path = request.uri().getPath();
			String newPath = pattern.matcher(path).replaceAll(normalizedReplacement);

			URI rewrittenUri = UriComponentsBuilder.fromUri(request.uri())
				.replacePath(newPath)
				.encode()
				.build()
				.toUri();

			ServerRequest modified = ServerRequest.from(request).uri(rewrittenUri).build();

			return modified;
		};
	}

	public static Function<ServerRequest, ServerRequest> rewriteRequestParameter(String name, String replacement) {
		return request -> {
			MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>(request.params());
			if (queryParams.containsKey(name)) {
				queryParams.remove(name);
				queryParams.add(name, replacement);
			}

			MultiValueMap<String, String> encodedQueryParams = UriUtils.encodeQueryParams(queryParams);
			URI rewrittenUri = UriComponentsBuilder.fromUri(request.uri())
				.replaceQueryParams(unmodifiableMultiValueMap(encodedQueryParams))
				.build(true)
				.toUri();

			return ServerRequest.from(request).params(params -> {
				params.remove(name);
				params.add(name, replacement);
			}).uri(rewrittenUri).build();
		};
	}

	public static Function<ServerRequest, ServerRequest> routeId(String routeId) {
		return request -> {
			MvcUtils.setRouteId(request, routeId);
			return request;
		};
	}

	public static Function<ServerRequest, ServerRequest> setPath(String path) {
		UriTemplate uriTemplate = new UriTemplate(path);

		return request -> {
			MvcUtils.addOriginalRequestUrl(request, request.uri());
			Map<String, Object> uriVariables = MvcUtils.getUriTemplateVariables(request);
			URI uri = uriTemplate.expand(uriVariables);

			URI newUri = UriComponentsBuilder.fromUri(request.uri()).replacePath(uri.getRawPath()).build(true).toUri();
			return ServerRequest.from(request).uri(newUri).build();
		};
	}

	public static Function<ServerRequest, ServerRequest> setRequestHeader(String name, String value) {
		return request -> {
			String expandedValue = MvcUtils.expand(request, value);
			return ServerRequest.from(request).headers(httpHeaders -> httpHeaders.set(name, expandedValue)).build();
		};
	}

	public static Function<ServerRequest, ServerRequest> setRequestHostHeader(String host) {
		return request -> {
			String expandedValue = MvcUtils.expand(request, host);
			ServerRequest modified = ServerRequest.from(request).headers(httpHeaders -> {
				httpHeaders.remove(HttpHeaders.HOST);
				httpHeaders.set(HttpHeaders.HOST, expandedValue);
			}).build();

			// Make sure the header we just set is preserved
			modified.attributes().put(MvcUtils.PRESERVE_HOST_HEADER_ATTRIBUTE, true);
			return modified;
		};
	}

	public static Function<ServerRequest, ServerRequest> stripPrefix() {
		return stripPrefix(1);
	}

	public static Function<ServerRequest, ServerRequest> stripPrefix(int parts) {
		return request -> {
			MvcUtils.addOriginalRequestUrl(request, request.uri());
			// TODO: gateway url attributes
			String path = request.uri().getRawPath();
			// TODO: begin duplicate code from StripPrefixGatewayFilterFactory
			String[] originalParts = StringUtils.tokenizeToStringArray(path, "/");

			// all new paths start with /
			StringBuilder newPath = new StringBuilder("/");
			for (int i = 0; i < originalParts.length; i++) {
				if (i >= parts) {
					// only append slash if this is the second part or greater
					if (newPath.length() > 1) {
						newPath.append('/');
					}
					newPath.append(originalParts[i]);
				}
			}
			if (newPath.length() > 1 && path.endsWith("/")) {
				newPath.append('/');
			}
			// TODO: end duplicate code from StripPrefixGatewayFilterFactory

			URI prefixedUri = UriComponentsBuilder.fromUri(request.uri())
				.replacePath(newPath.toString())
				.build(true)
				.toUri();

			return ServerRequest.from(request).uri(prefixedUri).build();
		};
	}

	public static Function<ServerRequest, ServerRequest> uri(String uri) {
		return uri(URI.create(uri));
	}

	public static Function<ServerRequest, ServerRequest> uri(URI uri) {
		return request -> {
			MvcUtils.setRequestUrl(request, uri);
			return request;
		};
	}

	public static class FallbackHeadersConfig {

		private String executionExceptionTypeHeaderName = CB_EXECUTION_EXCEPTION_TYPE;

		private String executionExceptionMessageHeaderName = CB_EXECUTION_EXCEPTION_MESSAGE;

		private String rootCauseExceptionTypeHeaderName = CB_ROOT_CAUSE_EXCEPTION_TYPE;

		private String rootCauseExceptionMessageHeaderName = CB_ROOT_CAUSE_EXCEPTION_MESSAGE;

		public String getExecutionExceptionTypeHeaderName() {
			return executionExceptionTypeHeaderName;
		}

		public void setExecutionExceptionTypeHeaderName(String executionExceptionTypeHeaderName) {
			this.executionExceptionTypeHeaderName = executionExceptionTypeHeaderName;
		}

		public String getExecutionExceptionMessageHeaderName() {
			return executionExceptionMessageHeaderName;
		}

		public void setExecutionExceptionMessageHeaderName(String executionExceptionMessageHeaderName) {
			this.executionExceptionMessageHeaderName = executionExceptionMessageHeaderName;
		}

		public String getRootCauseExceptionTypeHeaderName() {
			return rootCauseExceptionTypeHeaderName;
		}

		public void setRootCauseExceptionTypeHeaderName(String rootCauseExceptionTypeHeaderName) {
			this.rootCauseExceptionTypeHeaderName = rootCauseExceptionTypeHeaderName;
		}

		public String getRootCauseExceptionMessageHeaderName() {
			return rootCauseExceptionMessageHeaderName;
		}

		public void setRootCauseExceptionMessageHeaderName(String rootCauseExceptionMessageHeaderName) {
			this.rootCauseExceptionMessageHeaderName = rootCauseExceptionMessageHeaderName;
		}

	}

}
