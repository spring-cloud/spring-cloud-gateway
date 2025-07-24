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

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

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

}
