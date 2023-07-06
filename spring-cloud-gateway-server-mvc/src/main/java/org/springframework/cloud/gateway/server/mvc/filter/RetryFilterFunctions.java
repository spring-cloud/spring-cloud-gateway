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

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.CompositeRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

public abstract class RetryFilterFunctions {

	private RetryFilterFunctions() {
	}

	public static HandlerFilterFunction<ServerResponse, ServerResponse> retry(int retries) {
		RetryTemplateBuilder retryTemplateBuilder = RetryTemplate.builder();
		return (request, next) -> {
			CompositeRetryPolicy compositeRetryPolicy = new CompositeRetryPolicy();
			// TODO: better configuration of exceptions
			SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy(retries, Map.of(IOException.class, true,
					TimeoutException.class, true, HttpServerErrorException.class, true));
			compositeRetryPolicy.setPolicies(
					Arrays.asList(simpleRetryPolicy, new HttpStatusRetryPolicy()).toArray(new RetryPolicy[0]));
			RetryTemplate retryTemplate = retryTemplateBuilder.customPolicy(compositeRetryPolicy).build();
			return retryTemplate.execute(context -> {
				ServerResponse serverResponse = next.handle(request);
				// TODO: better status code check and configuration
				if (serverResponse.statusCode().is5xxServerError()) {
					throw new HttpServerErrorException(serverResponse.statusCode());
				}
				return serverResponse;
			});
		};
	}

	static class HttpStatusRetryPolicy extends NeverRetryPolicy {

		@Override
		public boolean canRetry(RetryContext context) {
			// TODO: custom exception
			// TODO: better status code check and configuration
			if (context.getLastThrowable() instanceof HttpServerErrorException e) {
				return e.getStatusCode().is5xxServerError();
			}
			return super.canRetry(context);
		}

	}

}
