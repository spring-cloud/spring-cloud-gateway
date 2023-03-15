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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.simple.SimpleSpanBuilder;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.BDDAssertions.then;

class GatewayPropagatingSenderTracingObservationHandlerTests {

	SimpleTracer tracer = new SimpleTracer();

	Propagator propagator = new Propagator() {
		@Override
		public List<String> fields() {
			return Arrays.asList("foo", "bar");
		}

		@Override
		public <C> void inject(TraceContext context, C carrier, Setter<C> setter) {

		}

		@Override
		public <C> Span.Builder extract(C carrier, Getter<C> getter) {
			return new SimpleSpanBuilder(tracer);
		}
	};

	GatewayPropagatingSenderTracingObservationHandler handler = new GatewayPropagatingSenderTracingObservationHandler(
			tracer, propagator, Collections.singletonList("remote"));

	@Test
	void shouldRemovePropagationFieldsFromTheRequestBeforePropagating() {
		HttpHeaders headers = new HttpHeaders();
		headers.put("foo", Collections.singletonList("foo value"));
		headers.put("bar", Collections.singletonList("bar value"));
		headers.put("baz", Collections.singletonList("baz value"));
		headers.put("remote", Collections.singletonList("remote value"));
		MockServerHttpRequest request = MockServerHttpRequest.get("/get").build();
		MockServerWebExchange serverWebExchange = MockServerWebExchange.from(request);
		GatewayContext gatewayContext = new GatewayContext(headers, request, serverWebExchange);

		handler.onStart(gatewayContext);

		then(headers).doesNotContainKeys("foo", "bar").containsEntry("baz", Collections.singletonList("baz value"))
				.containsEntry("remote", Collections.singletonList("remote value"));
	}

}
