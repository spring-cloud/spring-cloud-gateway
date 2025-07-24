/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.route;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Fredrich Ombico
 */
@ExtendWith(MockitoExtension.class)
class RouteDefinitionMetricsTests {

	@Mock
	private MeterRegistry registry;

	@Mock
	private RouteDefinitionLocator routeDefinitionLocator;

	private RouteDefinitionMetrics routeDefinitionMetrics;

	private AtomicInteger routeDefinitionCount;

	@BeforeEach
	void setUp() {
		routeDefinitionCount = new AtomicInteger(0);
		when(registry.gauge(any(String.class), any(AtomicInteger.class))).thenReturn(routeDefinitionCount);
		routeDefinitionMetrics = new RouteDefinitionMetrics(registry, routeDefinitionLocator, "prefix.");
	}

	@Test
	void metricsPrefix() {
		assertThat(routeDefinitionMetrics.getMetricsPrefix()).isEqualTo("prefix");
	}

	@Test
	void shouldReportOneRoute() {
		List<RouteDefinition> oneRoute = Collections.singletonList(new RouteDefinition());
		when(routeDefinitionLocator.getRouteDefinitions()).thenReturn(Flux.fromStream(oneRoute.stream()));

		RefreshRoutesEvent refreshRoutesEvent = new RefreshRoutesEvent(this);
		routeDefinitionMetrics.onApplicationEvent(refreshRoutesEvent);

		assertThat(routeDefinitionCount.get()).isEqualTo(1);
	}

	@Test
	void shouldReportMultipleRoutes() {
		List<RouteDefinition> multipleRoutes = Arrays.asList(new RouteDefinition(), new RouteDefinition(),
				new RouteDefinition(), new RouteDefinition(), new RouteDefinition());
		when(routeDefinitionLocator.getRouteDefinitions()).thenReturn(Flux.fromStream(multipleRoutes.stream()));

		RefreshRoutesEvent refreshRoutesEvent = new RefreshRoutesEvent(this);
		routeDefinitionMetrics.onApplicationEvent(refreshRoutesEvent);

		assertThat(routeDefinitionCount.get()).isEqualTo(5);
	}

	@Test
	void shouldReportZeroIfNoRoutes() {
		RouteDefinition[] zeroRoutes = new RouteDefinition[0];
		when(routeDefinitionLocator.getRouteDefinitions()).thenReturn(Flux.fromArray(zeroRoutes));

		RefreshRoutesEvent refreshRoutesEvent = new RefreshRoutesEvent(this);
		routeDefinitionMetrics.onApplicationEvent(refreshRoutesEvent);

		assertThat(routeDefinitionCount.get()).isEqualTo(0);
	}

}
