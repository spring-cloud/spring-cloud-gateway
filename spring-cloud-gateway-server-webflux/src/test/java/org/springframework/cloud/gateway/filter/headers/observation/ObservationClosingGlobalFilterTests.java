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

package org.springframework.cloud.gateway.filter.headers.observation;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Kai Gülzau
 */
class ObservationClosingGlobalFilterTests {

	ObservationClosingGlobalFilter filter = new ObservationClosingGlobalFilter();

	MockServerHttpRequest request = MockServerHttpRequest.get("/get").build();

	ServerWebExchange exchange = MockServerWebExchange.from(request);

	Observation observation = Mockito.mock(Observation.class);

	GatewayFilterChain pendingChain = serverWebExchange -> Mono.never();

	@Test
	void shouldStopTheObservationIfItWasNotStoppedPreviouslyAndTheExchangeWasCancelled() {
		exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR, observation);

		StepVerifier.create(filter.filter(exchange, pendingChain)).thenCancel().verify();

		Mockito.verify(observation).stop();
		assertThat((Object) exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR)).isNull();
		assertThat((String) exchange.getAttribute(ObservedResponseHttpHeadersFilter.OBSERVATION_STOPPED)).isNotNull();
	}

	@Test
	void shouldNotStopTheObservationAgainWhenTheResponseAlreadyStoppedIt() {
		exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR, observation);
		new ObservedResponseHttpHeadersFilter().filter(new HttpHeaders(), exchange);

		StepVerifier.create(filter.filter(exchange, pendingChain)).thenCancel().verify();

		Mockito.verify(observation, Mockito.times(1)).stop();
	}

	@Test
	void shouldDoNothingWhenThereIsNoObservation() {
		StepVerifier.create(filter.filter(exchange, pendingChain)).thenCancel().verify();

		assertThat((String) exchange.getAttribute(ObservedResponseHttpHeadersFilter.OBSERVATION_STOPPED)).isNull();
	}

	@Test
	void shouldStopTheObservationWhenTheExchangeCompletesWithoutResponseHeaders() {
		// e.g. a websocket session: WebsocketRoutingFilter never runs the response headers
		// filters
		exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR, observation);

		StepVerifier.create(filter.filter(exchange, serverWebExchange -> Mono.empty())).verifyComplete();

		Mockito.verify(observation).stop();
	}

}
