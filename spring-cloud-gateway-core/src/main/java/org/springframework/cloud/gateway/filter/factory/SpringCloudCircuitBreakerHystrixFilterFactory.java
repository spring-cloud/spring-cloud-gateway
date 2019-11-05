/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory;

import com.netflix.hystrix.exception.HystrixRuntimeException;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.support.ServiceUnavailableException;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ResponseStatusException;

/**
 * @author Ryan Baxter
 */
public class SpringCloudCircuitBreakerHystrixFilterFactory
		extends SpringCloudCircuitBreakerFilterFactory {

	public SpringCloudCircuitBreakerHystrixFilterFactory(
			ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory,
			ObjectProvider<DispatcherHandler> dispatcherHandlerProvider) {
		super(reactiveCircuitBreakerFactory, dispatcherHandlerProvider);
	}

	@Override
	protected Mono<Void> handleErrorWithoutFallback(Throwable throwable) {
		if (throwable instanceof HystrixRuntimeException) {
			HystrixRuntimeException e = (HystrixRuntimeException) throwable;
			HystrixRuntimeException.FailureType failureType = e.getFailureType();

			switch (failureType) {
			case TIMEOUT:
				return Mono.error(new TimeoutException());
			case SHORTCIRCUIT:
				return Mono.error(new ServiceUnavailableException());
			case COMMAND_EXCEPTION: {
				Throwable cause = e.getCause();

				/*
				 * We forsake here the null check for cause as HystrixRuntimeException
				 * will always have a cause if the failure type is COMMAND_EXCEPTION.
				 */
				if (cause instanceof ResponseStatusException
						|| AnnotatedElementUtils.findMergedAnnotation(cause.getClass(),
								ResponseStatus.class) != null) {
					return Mono.error(cause);
				}
			}
			default:
				break;
			}
		}
		return Mono.error(throwable);
	}

}
