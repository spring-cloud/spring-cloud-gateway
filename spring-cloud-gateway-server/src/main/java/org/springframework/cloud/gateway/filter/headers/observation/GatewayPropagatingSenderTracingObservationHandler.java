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

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.micrometer.observation.Observation;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;

/**
 * Tracing header removing {@link PropagatingSenderTracingObservationHandler}.
 *
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
public class GatewayPropagatingSenderTracingObservationHandler
		extends PropagatingSenderTracingObservationHandler<GatewayContext> {

	private final Propagator propagator;

	private final List<String> remoteFieldsLowerCase;

	/**
	 * Creates a new instance of {@link PropagatingSenderTracingObservationHandler}.
	 * @param tracer the tracer to use to record events
	 * @param propagator the mechanism to propagate tracing information into the carrier
	 * @param remoteFields remote fields to be propagated over the wire
	 */
	public GatewayPropagatingSenderTracingObservationHandler(Tracer tracer, Propagator propagator,
			List<String> remoteFields) {
		super(tracer, propagator);
		this.propagator = propagator;
		this.remoteFieldsLowerCase = remoteFields.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
	}

	@Override
	public void onStart(GatewayContext context) {
		this.propagator.fields().stream()
				.filter(field -> !remoteFieldsLowerCase.contains(field.toLowerCase(Locale.ROOT)))
				.forEach(s -> Objects.requireNonNull(context.getCarrier()).remove(s));
		super.onStart(context);
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof GatewayContext;
	}

}
