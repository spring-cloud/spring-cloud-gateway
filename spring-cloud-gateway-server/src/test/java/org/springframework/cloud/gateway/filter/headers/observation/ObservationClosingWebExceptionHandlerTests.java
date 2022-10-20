/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers.observation;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThatNoException;

class ObservationClosingWebExceptionHandlerTests {

	ObservationClosingWebExceptionHandler handler = new ObservationClosingWebExceptionHandler();

	MockServerHttpRequest request = MockServerHttpRequest.get("/get").build();

	ServerWebExchange exchange = MockServerWebExchange.from(request);

	@Test
	void shouldDoNothingWhenObservationAlreadyStopped() {
		exchange.getAttributes().put(ObservedResponseHttpHeadersFilter.OBSERVATION_STOPPED, "true");
		exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR,
				"if this attribute will be attempted to be retrieved ClassCast will be thrown");

		assertThatNoException().isThrownBy(() -> handler.handle(exchange, new RuntimeException()));
	}

	@Test
	void shouldDoNothingWhenThereIsNoObservation() {
		assertThatNoException().isThrownBy(() -> handler.handle(exchange, new RuntimeException()));
	}

	@Test
	void shouldStopTheObservationIfItWasNotStoppedPreviouslyAndThereWasAnError() {
		Observation observation = Mockito.mock(Observation.class);
		exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR, observation);
		RuntimeException runtimeException = new RuntimeException();

		assertThatNoException().isThrownBy(() -> handler.handle(exchange, runtimeException));
		Mockito.verify(observation).error(runtimeException);
		Mockito.verify(observation).stop();
	}

}
