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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.server.mvc.common.WeightConfig;
import org.springframework.cloud.gateway.server.mvc.filter.WeightCalculatorFilter.GroupWeightConfig;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WeightCalculatorFilterTests {

	@Test
	public void testWeightCalculation() {
		WeightCalculatorFilter filter = createFilter();

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

	private WeightCalculatorFilter createFilter() {
		return new WeightCalculatorFilter();
	}

	private void assertWeightCalculation(WeightCalculatorFilter filter, String group, int item, int weight,
			List<Double> normalized, Double... middleRanges) {
		String routeId = route(item);

		filter.addWeightConfig(new WeightConfig(routeId, group, weight));

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
	public void testChooseRouteWithRandom() throws Exception {
		WeightCalculatorFilter filter = createFilter();
		filter.addWeightConfig(new WeightConfig("route1", "groupa", 1));
		filter.addWeightConfig(new WeightConfig("route2", "groupa", 3));
		filter.addWeightConfig(new WeightConfig("route3", "groupa", 6));

		Supplier<Double> random = mock(Supplier.class);

		when(random.get()).thenReturn(0.05).thenReturn(0.2).thenReturn(0.6);

		filter.setRandomSupplier(random);

		MockHttpServletRequest request = MockMvcRequestBuilders.get("http://localhost").buildRequest(null);

		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		Map<String, String> weights = WeightCalculatorFilter.getWeights(request);
		assertThat(weights).containsEntry("groupa", "route1");

		filter.doFilter(request, response, new MockFilterChain());
		weights = WeightCalculatorFilter.getWeights(request);
		assertThat(weights).containsEntry("groupa", "route2");

		filter.doFilter(request, response, new MockFilterChain());
		weights = WeightCalculatorFilter.getWeights(request);
		assertThat(weights).containsEntry("groupa", "route3");
	}

	// @Test
	// public void receivesPredicateArgsEvent() {
	// TestWeightCalculatorHandlerInterceptor filter = new
	// TestWeightCalculatorHandlerInterceptor();
	//
	// HashMap<String, Object> args = new HashMap<>();
	// args.put("weight.group", "group1");
	// args.put("weight.weight", "1");
	// PredicateArgsEvent event = new PredicateArgsEvent(this, "routeA", args);
	// filter.handle(event);
	//
	// WeightConfig weightConfig = filter.weightConfig;
	// assertThat(weightConfig.getGroup()).isEqualTo("group1");
	// assertThat(weightConfig.getRouteId()).isEqualTo("routeA");
	// assertThat(weightConfig.getWeight()).isEqualTo(1);
	// }

	class TestWeightCalculatorFilter extends WeightCalculatorFilter {

		private WeightConfig weightConfig;

		TestWeightCalculatorFilter() {
			super();
		}

		@Override
		void addWeightConfig(WeightConfig weightConfig) {
			this.weightConfig = weightConfig;
		}

	}

}
