/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.event.PredicateArgsEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.event.WeightDefinedEvent;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.ConfigurationUtils;
import org.springframework.cloud.gateway.support.WeightConfig;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.Validator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.WEIGHT_ATTR;

/**
 * @author Spencer Gibb
 */
public class WeightCalculatorWebFilter
		implements WebFilter, Ordered, SmartApplicationListener {

	/**
	 * Order of Weight Calculator Web filter.
	 */
	public static final int WEIGHT_CALC_FILTER_ORDER = 10001;

	private static final Log log = LogFactory.getLog(WeightCalculatorWebFilter.class);

	private final Validator validator;

	private final ObjectProvider<RouteLocator> routeLocator;

	private Random random = new Random();

	private int order = WEIGHT_CALC_FILTER_ORDER;

	private Map<String, GroupWeightConfig> groupWeights = new ConcurrentHashMap<>();

	/* for testing */ WeightCalculatorWebFilter() {
		this(null, null);
	}

	@Deprecated
	public WeightCalculatorWebFilter(Validator validator) {
		this(validator, null);
	}

	public WeightCalculatorWebFilter(Validator validator,
			ObjectProvider<RouteLocator> routeLocator) {
		this.validator = validator;
		this.routeLocator = routeLocator;
	}

	/* for testing */
	static Map<String, String> getWeights(ServerWebExchange exchange) {
		Map<String, String> weights = exchange.getAttribute(WEIGHT_ATTR);

		if (weights == null) {
			weights = new ConcurrentHashMap<>();
			exchange.getAttributes().put(WEIGHT_ATTR, weights);
		}
		return weights;
	}

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
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return PredicateArgsEvent.class.isAssignableFrom(eventType) || // config file
				WeightDefinedEvent.class.isAssignableFrom(eventType) || // java dsl
				RefreshRoutesEvent.class.isAssignableFrom(eventType); // force
																		// initialization
	}

	@Override
	public boolean supportsSourceType(Class<?> sourceType) {
		return true;
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof PredicateArgsEvent) {
			handle((PredicateArgsEvent) event);
		}
		else if (event instanceof WeightDefinedEvent) {
			addWeightConfig(((WeightDefinedEvent) event).getWeightConfig());
		}
		else if (event instanceof RefreshRoutesEvent && routeLocator != null) {
			routeLocator.ifAvailable(locator -> locator.getRoutes().subscribe()); // forces
																					// initialization
		}

	}

	public void handle(PredicateArgsEvent event) {
		Map<String, Object> args = event.getArgs();

		if (args.isEmpty() || !hasRelevantKey(args)) {
			return;
		}

		WeightConfig config = new WeightConfig(event.getRouteId());

		ConfigurationUtils.bind(config, args, WeightConfig.CONFIG_PREFIX,
				WeightConfig.CONFIG_PREFIX, validator);

		addWeightConfig(config);
	}

	private boolean hasRelevantKey(Map<String, Object> args) {
		return args.keySet().stream()
				.anyMatch(key -> key.startsWith(WeightConfig.CONFIG_PREFIX + "."));
	}

	/* for testing */ void addWeightConfig(WeightConfig weightConfig) {
		String group = weightConfig.getGroup();
		GroupWeightConfig c = groupWeights.get(group);
		if (c == null) {
			c = new GroupWeightConfig(group);
			groupWeights.put(group, c);
		}
		GroupWeightConfig config = c;
		config.weights.put(weightConfig.getRouteId(), weightConfig.getWeight());

		// recalculate

		// normalize weights
		int weightsSum = config.weights.values().stream().mapToInt(Integer::intValue)
				.sum();

		final AtomicInteger index = new AtomicInteger(0);
		config.weights.forEach((routeId, weight) -> {
			Double nomalizedWeight = weight / (double) weightsSum;
			config.normalizedWeights.put(routeId, nomalizedWeight);

			// recalculate rangeIndexes
			config.rangeIndexes.put(index.getAndIncrement(), routeId);
		});

		// TODO: calculate ranges
		config.ranges.clear();

		config.ranges.add(0.0);

		List<Double> values = new ArrayList<>(config.normalizedWeights.values());
		for (int i = 0; i < values.size(); i++) {
			Double currentWeight = values.get(i);
			Double previousRange = config.ranges.get(i);
			Double range = previousRange + currentWeight;
			config.ranges.add(range);
		}

		if (log.isTraceEnabled()) {
			log.trace("Recalculated group weight config " + config);
		}
	}

	/* for testing */ Map<String, GroupWeightConfig> getGroupWeights() {
		return groupWeights;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		Map<String, String> weights = getWeights(exchange);

		for (String group : groupWeights.keySet()) {
			GroupWeightConfig config = groupWeights.get(group);

			if (config == null) {
				if (log.isDebugEnabled()) {
					log.debug("No GroupWeightConfig found for group: " + group);
				}
				continue; // nothing we can do, but this is odd
			}

			double r = this.random.nextDouble();

			List<Double> ranges = config.ranges;

			if (log.isTraceEnabled()) {
				log.trace("Weight for group: " + group + ", ranges: " + ranges + ", r: "
						+ r);
			}

			for (int i = 0; i < ranges.size() - 1; i++) {
				if (r >= ranges.get(i) && r < ranges.get(i + 1)) {
					String routeId = config.rangeIndexes.get(i);
					weights.put(group, routeId);
					break;
				}
			}
		}

		if (log.isTraceEnabled()) {
			log.trace("Weights attr: " + weights);
		}

		return chain.filter(exchange);
	}

	/* for testing */ static class GroupWeightConfig {

		String group;

		LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();

		LinkedHashMap<String, Double> normalizedWeights = new LinkedHashMap<>();

		LinkedHashMap<Integer, String> rangeIndexes = new LinkedHashMap<>();

		List<Double> ranges = new ArrayList<>();

		GroupWeightConfig(String group) {
			this.group = group;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("group", group)
					.append("weights", weights)
					.append("normalizedWeights", normalizedWeights)
					.append("rangeIndexes", rangeIndexes).toString();
		}

	}

}
