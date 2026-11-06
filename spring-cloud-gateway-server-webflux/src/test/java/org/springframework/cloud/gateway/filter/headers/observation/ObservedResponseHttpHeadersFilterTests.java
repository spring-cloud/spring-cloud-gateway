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

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.thenNoException;

class ObservedResponseHttpHeadersFilterTests {

	@Test
	void shouldDoNothingWhenObservationIsNoOp() {
		MockServerHttpRequest request = MockServerHttpRequest.get("/get").build();
		ServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR, Observation.NOOP);
		ObservedResponseHttpHeadersFilter filter = new ObservedResponseHttpHeadersFilter();

		thenNoException().isThrownBy(() -> filter.filter(new HttpHeaders(), exchange));
	}

	@Test
	void shouldStopTheObservationOfEachAttemptOfARetriedRequest() {
		TestObservationRegistry registry = TestObservationRegistry.create();
		MockServerHttpRequest request = MockServerHttpRequest.get("/get").build();
		ServerWebExchange exchange = MockServerWebExchange.from(request);
		ObservedRequestHttpHeadersFilter requestFilter = new ObservedRequestHttpHeadersFilter(registry);
		ObservedResponseHttpHeadersFilter responseFilter = new ObservedResponseHttpHeadersFilter();

		for (int attempt = 0; attempt < 2; attempt++) {
			requestFilter.filter(new HttpHeaders(), exchange);
			responseFilter.filter(new HttpHeaders(), exchange);

			TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasBeenStopped();
			registry.clear();
		}
	}

	@Test
	void shouldStopTheObservationOfAnAttemptThatFailedBeforeTheResponseWhenTheRequestIsRetried() {
		List<Observation.Context> stopped = new ArrayList<>();
		ObservationRegistry registry = ObservationRegistry.create();
		registry.observationConfig().observationHandler(new ObservationHandler<>() {
			@Override
			public void onStop(Observation.Context context) {
				stopped.add(context);
			}

			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}
		});
		MockServerHttpRequest request = MockServerHttpRequest.get("/get").build();
		ServerWebExchange exchange = MockServerWebExchange.from(request);
		ObservedRequestHttpHeadersFilter requestFilter = new ObservedRequestHttpHeadersFilter(registry);

		// the first attempt fails before any response headers arrive, then it is retried
		requestFilter.filter(new HttpHeaders(), exchange);
		Observation firstAttempt = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_OBSERVATION_ATTR);
		requestFilter.filter(new HttpHeaders(), exchange);

		assertThat(firstAttempt).isNotNull();
		assertThat(stopped).containsExactly(firstAttempt.getContext());
	}

}
