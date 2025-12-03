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

package org.springframework.cloud.gateway.config;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.TracingProperties;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayPropagatingSenderTracingObservationHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.reactive.DispatcherHandler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".observability.enabled", matchIfMissing = true)
@AutoConfigureAfter({ GatewayMetricsAutoConfiguration.class, MicrometerTracingAutoConfiguration.class })
@ConditionalOnClass({ DispatcherHandler.class, Tracer.class, TracingProperties.class,
		MicrometerTracingAutoConfiguration.class })
public class GatewayTracingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ Tracer.class, Propagator.class, TracingProperties.class })
	@Order(Ordered.HIGHEST_PRECEDENCE + 5)
	GatewayPropagatingSenderTracingObservationHandler gatewayPropagatingSenderTracingObservationHandler(Tracer tracer,
			Propagator propagator, TracingProperties tracingProperties) {
		return new GatewayPropagatingSenderTracingObservationHandler(tracer, propagator,
				tracingProperties.getBaggage().getRemoteFields());
	}

}
