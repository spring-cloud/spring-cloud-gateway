/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.gateway.event.PredicateArgsEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.WEIGHT_ATTR;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class WeightCalculatorWebFilter implements WebFilter, Ordered, ApplicationListener<PredicateArgsEvent> {

	private static final Log log = LogFactory.getLog(WeightCalculatorWebFilter.class);

	public static final int WEIGHT_CALC_FILTER_ORDER = 10001;

	private Random random = new Random();
	private int order = WEIGHT_CALC_FILTER_ORDER;

	private Map<String, WeightConfig> groupWeights = new ConcurrentHashMap<>();

	@Override
	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public void setRandom(Random random) {
		this.random = random;
	}

	@Override
	public void onApplicationEvent(PredicateArgsEvent event) {
		Map<String, Object> args = event.getArgs();

		if (args.isEmpty() || !hasRelevantKey(args)) {
			return;
		}

		String group = (String) args.get("weight.group");
		String weight = (String) args.get("weight.value");
		Assert.notNull(group, "weight.group may not be null");
		Assert.notNull(weight, "weight.value may not be null");
		addWeightConfig(group, event.getRouteId(), Integer.parseInt(weight));
	}

	private boolean hasRelevantKey(Map<String, Object> args) {
		return args.keySet().stream()
				.anyMatch(key -> key.startsWith("weight."));
	}

	/* for testing */ void addWeightConfig(String group, String newRouteId, int newWeight) {
		WeightConfig c = groupWeights.get(group);
		if (c == null) {
			c = new WeightConfig(group);
			groupWeights.put(group, c);
		}
		WeightConfig config = c;
		config.weights.put(newRouteId, newWeight);

		//recalculate

		// normalize weights
		int weightsSum = config.weights.values().stream().mapToInt(Integer::intValue).sum();

		final AtomicInteger index = new AtomicInteger(0);
		config.weights.forEach((routeId, weight) -> {
			Double nomalizedWeight = weight / (double) weightsSum;
			config.normalizedWeights.put(routeId, nomalizedWeight);

			// recalculate rangeIndexes
			config.rangeIndexes.put(index.getAndIncrement(), routeId);
		});

		//TODO: calculate ranges
		config.ranges.clear();

		config.ranges.add(0.0);

		List<Double> values = new ArrayList<>(config.normalizedWeights.values());
		for (int i = 0; i < values.size(); i++) {
			Double currentWeight = values.get(i);
			Double previousRange = config.ranges.get(i);
			Double range = previousRange + currentWeight;
			config.ranges.add(range);
		}
	}

	/* for testing */ Map<String, WeightConfig> getGroupWeights() {
		return groupWeights;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		Map<String, String> weights = getWeights(exchange);

		groupWeights.forEach((group, config) -> {
			double r = this.random.nextDouble();

			List<Double> ranges = config.ranges;
			for (int i = 0; i < ranges.size() - 1; i++) {
				if (r >= ranges.get(i) && r < ranges.get(i+1)) {
					String routeId = config.rangeIndexes.get(i);
					weights.put(group, routeId);
					break;
				}
			}
		});

		return chain.filter(exchange);
	}

	@NotNull
	/* for testing */ static Map<String, String> getWeights(ServerWebExchange exchange) {
		Map<String, String> weights = exchange.getAttribute(WEIGHT_ATTR);

		if (weights == null) {
			weights = new ConcurrentHashMap<>();
			exchange.getAttributes().put(WEIGHT_ATTR, weights);
		}
		return weights;
	}

	/* for testing */ static class WeightConfig {
		String group;

		LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();

		LinkedHashMap<String, Double> normalizedWeights = new LinkedHashMap<>();

		LinkedHashMap<Integer, String> rangeIndexes = new LinkedHashMap<>();
		List<Double> ranges = new ArrayList<>();

		WeightConfig(String group) {
			this.group = group;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this)
					.append("group", group)
					.append("rangeIndexes", rangeIndexes)
					.append("weights", weights)
					.append("normalizedWeights", normalizedWeights)
					.toString();
		}
	}

}
