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

package org.springframework.cloud.gateway.filter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.event.PredicateArgsEvent;
import org.springframework.cloud.gateway.event.RouteDeletedEvent;
import org.springframework.cloud.gateway.filter.WeightCalculatorWebFilter.GroupWeightConfig;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.cloud.gateway.support.WeightConfig;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WeightCalculatorWebFilterTests {

	@Test
	public void testWeightCalculation() {
		WeightCalculatorWebFilter filter = createFilter();

		String grp1 = "group1";
		String grp2 = "group2";
		int grp1idx = 1;
		int grp2idx = 1;

		assertWeightCalculation(filter, grp1, grp1idx++, 1, asList(1.0));
		assertWeightCalculation(filter, grp2, grp2idx++, 1, asList(1.0));
		assertWeightCalculation(filter, grp1, grp1idx++, 3, asList(0.25, 0.75), 0.25);
		assertWeightCalculation(filter, grp2, grp2idx++, 1, asList(0.5, 0.5), 0.5);
		assertWeightCalculation(filter, grp1, grp1idx++, 6, asList(0.1, 0.3, 0.6), 0.1, 0.4);
		assertWeightCalculation(filter, grp2, grp2idx++, 2, asList(0.25, 0.25, 0.5), 0.25, 0.5);
		assertWeightCalculation(filter, grp2, grp2idx++, 4, asList(0.125, 0.125, 0.25, 0.5), 0.125, 0.25, 0.5);
	}

	private WeightCalculatorWebFilter createFilter() {
		return new WeightCalculatorWebFilter(null, new ConfigurationService(null, () -> null, () -> null));
	}

	private void assertWeightCalculation(WeightCalculatorWebFilter filter, String group, int item, int weight,
			List<Double> normalized, Double... middleRanges) {
		String routeId = route(item);

		filter.addWeightConfig(new WeightConfig(group, routeId, weight));

		Map<String, GroupWeightConfig> groupWeights = filter.getGroupWeights();
		assertThat(groupWeights).containsKey(group);

		GroupWeightConfig config = groupWeights.get(group);
		assertThat(config.group).isEqualTo(group);
		assertThat(config.weights).hasSize(item).containsEntry(routeId, weight);
		assertThat(config.normalizedWeights).hasSize(item);

		for (int i = 0; i < normalized.size(); i++) {
			assertThat(config.normalizedWeights).containsEntry(route(i + 1), normalized.get(i));
		}

		for (int i = 0; i < normalized.size(); i++) {
			assertThat(config.rangeIndexes).containsEntry(i, route(i + 1));
		}

		assertThat(config.ranges).hasSize(item + 1).startsWith(0.0).endsWith(1.0);

		if (middleRanges.length > 0) {
			assertThat(config.ranges).contains(middleRanges);
		}
	}

	private String route(int i) {
		return "route" + i;
	}

	@Test
	public void testChooseRouteWithRandom() {
		WeightCalculatorWebFilter filter = createFilter();
		filter.addWeightConfig(new WeightConfig("groupa", "route1", 1));
		filter.addWeightConfig(new WeightConfig("groupa", "route2", 3));
		filter.addWeightConfig(new WeightConfig("groupa", "route3", 6));

		Supplier<Double> random = mock(Supplier.class);

		when(random.get()).thenReturn(0.05).thenReturn(0.2).thenReturn(0.6);

		filter.setRandomSupplier(random);

		MockServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("http://localhost").build());

		WebFilterChain filterChain = mock(WebFilterChain.class);
		filter.filter(exchange, filterChain);
		Map<String, String> weights = WeightCalculatorWebFilter.getWeights(exchange);
		assertThat(weights).containsEntry("groupa", "route1");

		filter.filter(exchange, filterChain);
		weights = WeightCalculatorWebFilter.getWeights(exchange);
		assertThat(weights).containsEntry("groupa", "route2");

		filter.filter(exchange, filterChain);
		weights = WeightCalculatorWebFilter.getWeights(exchange);
		assertThat(weights).containsEntry("groupa", "route3");

		filter.onApplicationEvent(new RouteDeletedEvent(this, "route3"));
		assertThat(filter.getGroupWeights()).containsKey("groupa");
		GroupWeightConfig groupa = filter.getGroupWeights().get("groupa");
		assertThat(groupa.normalizedWeights).doesNotContainKey("route3");
		assertThat(groupa.weights).doesNotContainKey("route3");
	}

	@Test
	public void receivesPredicateArgsEvent() {
		TestWeightCalculatorWebFilter filter = new TestWeightCalculatorWebFilter();

		HashMap<String, Object> args = new HashMap<>();
		args.put("weight.group", "group1");
		args.put("weight.weight", "1");
		PredicateArgsEvent event = new PredicateArgsEvent(this, "routeA", args);
		filter.handle(event);

		WeightConfig weightConfig = filter.weightConfig;
		assertThat(weightConfig.getGroup()).isEqualTo("group1");
		assertThat(weightConfig.getRouteId()).isEqualTo("routeA");
		assertThat(weightConfig.getWeight()).isEqualTo(1);
	}

	class TestWeightCalculatorWebFilter extends WeightCalculatorWebFilter {

		private WeightConfig weightConfig;

		TestWeightCalculatorWebFilter() {
			super(null, new ConfigurationService(null, () -> null, () -> null));
		}

		@Override
		void addWeightConfig(WeightConfig weightConfig) {
			this.weightConfig = weightConfig;
		}

	}

}
