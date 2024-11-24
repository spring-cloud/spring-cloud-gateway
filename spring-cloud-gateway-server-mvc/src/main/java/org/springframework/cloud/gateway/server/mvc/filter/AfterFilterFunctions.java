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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.springframework.cloud.gateway.server.mvc.common.HttpStatusHolder;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayServerResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public abstract class AfterFilterFunctions {

	private AfterFilterFunctions() {
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> addResponseHeader(String name,
			String... values) {
		return (request, response) -> {
			String[] expandedValues = MvcUtils.expandMultiple(request, values);
			response.headers().addAll(name, Arrays.asList(expandedValues));
			return response;
		};
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> dedupeResponseHeader(String name) {
		return dedupeResponseHeader(name, DedupeStrategy.RETAIN_FIRST);
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> dedupeResponseHeader(String name,
			DedupeStrategy strategy) {
		Assert.hasText(name, "name must not be null or empty");
		Assert.notNull(strategy, "strategy must not be null");
		return (request, response) -> {
			dedupeHeaders(response.headers(), name, strategy);
			return response;
		};
	}

	private static void dedupeHeaders(HttpHeaders headers, String names, DedupeStrategy strategy) {
		if (headers == null || names == null || strategy == null) {
			return;
		}
		String[] tokens = StringUtils.tokenizeToStringArray(names, " ", true, true);
		for (String name : tokens) {
			dedupeHeader(headers, name.trim(), strategy);
		}
	}

	private static void dedupeHeader(HttpHeaders headers, String name, DedupeStrategy strategy) {
		List<String> values = headers.get(name);
		if (values == null || values.size() <= 1) {
			return;
		}
		switch (strategy) {
			case RETAIN_FIRST:
				headers.set(name, values.get(0));
				break;
			case RETAIN_LAST:
				headers.set(name, values.get(values.size() - 1));
				break;
			case RETAIN_UNIQUE:
				headers.put(name, new ArrayList<>(new LinkedHashSet<>(values)));
				break;
			default:
				break;
		}
	}

	public static <T, R> BiFunction<ServerRequest, ServerResponse, ServerResponse> modifyResponseBody(Class<T> inClass,
			Class<R> outClass, String newContentType,
			BodyFilterFunctions.RewriteResponseFunction<T, R> rewriteFunction) {
		return BodyFilterFunctions.modifyResponseBody(inClass, outClass, newContentType, rewriteFunction);
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> removeResponseHeader(String name) {
		return (request, response) -> {
			response.headers().remove(name);
			return response;
		};
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> rewriteLocationResponseHeader() {
		return RewriteLocationResponseHeaderFilterFunctions.rewriteLocationResponseHeader(config -> {
		});
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> rewriteLocationResponseHeader(
			Consumer<RewriteLocationResponseHeaderFilterFunctions.RewriteLocationResponseHeaderConfig> configConsumer) {
		return RewriteLocationResponseHeaderFilterFunctions.rewriteLocationResponseHeader(configConsumer);
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> rewriteResponseHeader(String name,
			String regexp, String originalReplacement) {
		String replacement = originalReplacement.replace("$\\", "$");
		Pattern pattern = Pattern.compile(regexp);
		return (request, response) -> {
			response.headers().computeIfPresent(name, (key, values) -> {
				List<String> rewrittenValues = values.stream()
					.map(value -> pattern.matcher(value).replaceAll(replacement))
					.toList();
				return new ArrayList<>(rewrittenValues);
			});
			return response;
		};
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> setResponseHeader(String name,
			String value) {
		return (request, response) -> {
			String expandedValue = MvcUtils.expand(request, value);
			response.headers().set(name, expandedValue);
			return response;
		};
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> setStatus(int statusCode) {
		return setStatus(new HttpStatusHolder(null, statusCode));
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> setStatus(String statusCode) {
		return setStatus(HttpStatusHolder.valueOf(statusCode));
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> setStatus(HttpStatusCode statusCode) {
		return setStatus(new HttpStatusHolder(statusCode, null));
	}

	public static BiFunction<ServerRequest, ServerResponse, ServerResponse> setStatus(HttpStatusHolder statusCode) {
		return (request, response) -> {
			if (response instanceof GatewayServerResponse res) {
				res.setStatusCode(statusCode.resolve());
			}
			return response;
		};
	}

	public enum DedupeStrategy {

		/**
		 * Default: Retain the first value only.
		 */
		RETAIN_FIRST,

		/**
		 * Retain the last value only.
		 */
		RETAIN_LAST,

		/**
		 * Retain all unique values in the order of their first encounter.
		 */
		RETAIN_UNIQUE

	}

}
