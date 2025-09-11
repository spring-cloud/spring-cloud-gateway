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

package org.springframework.cloud.gateway.filter.cors;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.event.RefreshRoutesResultEvent;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CorsGatewayFilterApplicationListener}.
 *
 * <p>
 * This test verifies that the merged CORS configurations - composed of per-route metadata
 * and at the global level - maintain insertion order, as defined by the use of
 * {@link LinkedHashMap}. Preserving insertion order helps for predictable and
 * deterministic CORS behavior when resolving multiple matching path patterns.
 * </p>
 *
 * <p>
 * The test builds actual {@link Route} instances with {@code Path} predicates and
 * verifies that the resulting configuration map passed to
 * {@link RoutePredicateHandlerMapping#setCorsConfigurations(Map)} respects the declared
 * order of:
 * <ul>
 * <li>Route-specific CORS configurations (in the order the routes are discovered)</li>
 * <li>Global CORS configurations (in insertion order)</li>
 * </ul>
 * </p>
 *
 * @author Yavor Chamov
 */
@ExtendWith(MockitoExtension.class)
class CorsGatewayFilterApplicationListenerTests {

	private static final String GLOBAL_PATH_1 = "/global1";

	private static final String GLOBAL_PATH_2 = "/global2";

	private static final String ROUTE_PATH_1 = "/route1";

	private static final String ROUTE_PATH_2 = "/route2";

	private static final String ORIGIN_GLOBAL_1 = "https://global1.com";

	private static final String ORIGIN_GLOBAL_2 = "https://global2.com";

	private static final String ORIGIN_ROUTE_1 = "https://route1.com";

	private static final String ORIGIN_ROUTE_2 = "https://route2.com";

	private static final String ROUTE_ID_1 = "route1";

	private static final String ROUTE_ID_2 = "route2";

	private static final String ROUTE_URI = "https://spring.io";

	private static final String METADATA_KEY = "cors";

	private static final String ALLOWED_ORIGINS_KEY = "allowedOrigins";

	@Mock
	private RoutePredicateHandlerMapping handlerMapping;

	@Mock
	private RouteLocator routeLocator;

	@Captor
	private ArgumentCaptor<Map<String, CorsConfiguration>> corsConfigurations;

	private GlobalCorsProperties globalCorsProperties;

	private CorsGatewayFilterApplicationListener listener;

	@BeforeEach
	void setUp() {
		globalCorsProperties = new GlobalCorsProperties();
		listener = new CorsGatewayFilterApplicationListener(globalCorsProperties, handlerMapping, routeLocator);
	}

	@Test
	void testOnApplicationEvent_preservesInsertionOrder_withRealRoutes() {

		globalCorsProperties.getCorsConfigurations().put(GLOBAL_PATH_1, createCorsConfig(ORIGIN_GLOBAL_1));
		globalCorsProperties.getCorsConfigurations().put(GLOBAL_PATH_2, createCorsConfig(ORIGIN_GLOBAL_2));

		Route route1 = buildRoute(ROUTE_ID_1, ROUTE_PATH_1, ORIGIN_ROUTE_1);
		Route route2 = buildRoute(ROUTE_ID_2, ROUTE_PATH_2, ORIGIN_ROUTE_2);

		when(routeLocator.getRoutes()).thenReturn(Flux.just(route1, route2));

		listener.onApplicationEvent(new RefreshRoutesResultEvent(this));

		Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {

			verify(handlerMapping).setCorsConfigurations(corsConfigurations.capture());

			Map<String, CorsConfiguration> mergedCorsConfigurations = corsConfigurations.getValue();
			assertThat(mergedCorsConfigurations.keySet()).containsExactly(ROUTE_PATH_1, ROUTE_PATH_2, GLOBAL_PATH_1,
					GLOBAL_PATH_2);
			assertThat(mergedCorsConfigurations.get(GLOBAL_PATH_1).getAllowedOrigins())
				.containsExactly(ORIGIN_GLOBAL_1);
			assertThat(mergedCorsConfigurations.get(GLOBAL_PATH_2).getAllowedOrigins())
				.containsExactly(ORIGIN_GLOBAL_2);
			assertThat(mergedCorsConfigurations.get(ROUTE_PATH_1).getAllowedOrigins()).containsExactly(ORIGIN_ROUTE_1);
			assertThat(mergedCorsConfigurations.get(ROUTE_PATH_2).getAllowedOrigins()).containsExactly(ORIGIN_ROUTE_2);
		});
	}

	private CorsConfiguration createCorsConfig(String origin) {

		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(origin));
		return config;
	}

	private Route buildRoute(String id, String path, String allowedOrigin) {

		return Route.async()
			.id(id)
			.uri(ROUTE_URI)
			.predicate(new PathRoutePredicateFactory(new WebFluxProperties())
				.apply(config -> config.setPatterns(List.of(path))))
			.metadata(METADATA_KEY, Map.of(ALLOWED_ORIGINS_KEY, List.of(allowedOrigin)))
			.build();
	}

}
