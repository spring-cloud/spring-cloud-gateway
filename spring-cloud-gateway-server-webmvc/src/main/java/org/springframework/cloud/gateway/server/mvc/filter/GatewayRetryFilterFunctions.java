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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.RetryConfig;
import org.springframework.cloud.gateway.server.mvc.filter.RetryFilterFunctions.RetryException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.CompositeRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * @deprecated This filter is based on
 * <a href="https://github.com/spring-projects/spring-retry">Spring Retry</a> which has
 * been placed in maintenance-only mode. If/when Spring Retry is no longer maintained,
 * this filter will be removed. It is suggested you use
 * {@link FrameworkRetryFilterFunctions} instead.
 */
@Deprecated
public abstract class GatewayRetryFilterFunctions {

	private GatewayRetryFilterFunctions() {
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> retry(int retries) {
		return retry(config -> config.setRetries(retries));
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> retry(Consumer<RetryConfig> configConsumer) {
		RetryConfig config = new RetryConfig();
		configConsumer.accept(config);
		return retry(config);
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> retry(RetryConfig config) {
		RetryTemplateBuilder retryTemplateBuilder = RetryTemplate.builder();
		CompositeRetryPolicy compositeRetryPolicy = new CompositeRetryPolicy();
		Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
		config.getExceptions().forEach(exception -> retryableExceptions.put(exception, true));
		SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy(config.getRetries(), retryableExceptions);
		compositeRetryPolicy
			.setPolicies(Arrays.asList(simpleRetryPolicy, new HttpRetryPolicy(config)).toArray(new RetryPolicy[0]));
		RetryTemplate retryTemplate = retryTemplateBuilder.customPolicy(compositeRetryPolicy).build();
		return (request, next) -> retryTemplate.execute(context -> {
			if (config.isCacheBody()) {
				MvcUtils.getOrCacheBody(request);
			}
			reset(request);
			ServerResponse serverResponse = next.handle(request);

			if (isRetryableStatusCode(serverResponse.statusCode(), config)
					&& isRetryableMethod(request.method(), config)) {
				// use this to transfer information to HttpStatusRetryPolicy
				throw new RetryException(request, serverResponse);
			}
			return serverResponse;
		});
	}

	private static void reset(ServerRequest request) throws IOException {
		ClientHttpResponse clientHttpResponse = MvcUtils.getAttribute(request, MvcUtils.CLIENT_RESPONSE_ATTR);
		if (clientHttpResponse != null) {
			clientHttpResponse.close();
			MvcUtils.putAttribute(request, MvcUtils.CLIENT_RESPONSE_ATTR, null);
		}
	}

	private static boolean isRetryableStatusCode(HttpStatusCode httpStatus, RetryConfig config) {
		return config.getSeries().stream().anyMatch(series -> HttpStatus.Series.resolve(httpStatus.value()) == series);
	}

	private static boolean isRetryableMethod(HttpMethod method, RetryConfig config) {
		return config.getMethods().contains(method);
	}

	public static class HttpRetryPolicy extends NeverRetryPolicy {

		private final RetryConfig config;

		public HttpRetryPolicy(RetryConfig config) {
			this.config = config;
		}

		@Override
		public boolean canRetry(RetryContext context) {
			// TODO: custom exception
			if (context.getLastThrowable() instanceof RetryException e) {
				return isRetryableStatusCode(e.getResponse().statusCode(), config)
						&& isRetryableMethod(e.getRequest().method(), config);
			}
			return super.canRetry(context);
		}

	}

}
