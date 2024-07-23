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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.server.mvc.common.ArgumentSupplier.ArgumentSuppliedEvent;
import org.springframework.cloud.gateway.server.mvc.common.AttributedArugmentSuppliedEvent;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.common.WeightConfig;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.style.ToStringCreator;

import static org.springframework.cloud.gateway.server.mvc.common.MvcUtils.WEIGHT_ATTR;

/**
 * @author Spencer Gibb
 * @author Alexey Nakidkin
 */
@SuppressWarnings("unchecked")
public class WeightCalculatorFilter implements Filter, Ordered, SmartApplicationListener {

	/**
	 * Order of Weight Calculator Web filter.
	 */
	public static final int WEIGHT_CALC_FILTER_ORDER = 10001;

	private static final Log log = LogFactory.getLog(WeightCalculatorFilter.class);

	private Supplier<Double> randomSupplier = null;

	private int order = WEIGHT_CALC_FILTER_ORDER;

	private Map<String, GroupWeightConfig> groupWeights = new ConcurrentHashMap<>();

	public WeightCalculatorFilter() {

	}

	/* for testing */
	static Map<String, String> getWeights(ServletRequest request) {
		Map<String, String> weights = (Map<String, String>) request.getAttribute(WEIGHT_ATTR);

		if (weights == null) {
			weights = new ConcurrentHashMap<>();
			request.setAttribute(WEIGHT_ATTR, weights);
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

	public void setRandomSupplier(Supplier<Double> randomSupplier) {
		this.randomSupplier = randomSupplier;
	}

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return ArgumentSuppliedEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public boolean supportsSourceType(Class<?> sourceType) {
		return true;
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ArgumentSuppliedEvent argumentSuppliedEvent) {
			if (argumentSuppliedEvent.getType() == WeightConfig.class) {
				WeightConfig weightConfig = (WeightConfig) argumentSuppliedEvent.getArgument();
				if (weightConfig.getRouteId() == null
						&& argumentSuppliedEvent instanceof AttributedArugmentSuppliedEvent<?> attributed) {
					String routeId = (String) attributed.getAttributes().get(MvcUtils.GATEWAY_ROUTE_ID_ATTR);
					weightConfig.setRouteId(routeId);
				}

				addWeightConfig(weightConfig);
			}
		}
	}

	/* for testing */ void addWeightConfig(WeightConfig weightConfig) {
		// Assert.hasText(weightConfig.getRouteId(), "routeId is required");

		String group = weightConfig.getGroup();
		GroupWeightConfig config;
		// only create new GroupWeightConfig rather than modify
		// and put at end of calculations. This avoids concurency problems
		// later during filter execution.
		if (groupWeights.containsKey(group)) {
			config = new GroupWeightConfig(groupWeights.get(group));
		}
		else {
			config = new GroupWeightConfig(group);
		}

		config.weights.put(weightConfig.getRouteId(), weightConfig.getWeight());

		// recalculate

		// normalize weights
		int weightsSum = 0;

		for (Integer weight : config.weights.values()) {
			weightsSum += weight;
		}

		final AtomicInteger index = new AtomicInteger(0);
		for (Map.Entry<String, Integer> entry : config.weights.entrySet()) {
			String routeId = entry.getKey();
			Integer weight = entry.getValue();
			Double nomalizedWeight = weight / (double) weightsSum;
			config.normalizedWeights.put(routeId, nomalizedWeight);

			// recalculate rangeIndexes
			config.rangeIndexes.put(index.getAndIncrement(), routeId);
		}

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
		// only update after all calculations
		groupWeights.put(group, config);
	}

	/* for testing */ Map<String, GroupWeightConfig> getGroupWeights() {
		return groupWeights;
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		Map<String, String> weights = getWeights(request);

		for (String group : groupWeights.keySet()) {
			GroupWeightConfig config = groupWeights.get(group);

			if (config == null) {
				if (log.isDebugEnabled()) {
					log.debug("No GroupWeightConfig found for group: " + group);
				}
				continue; // nothing we can do, but this is odd
			}

			/*
			 * Usually, multiple threads accessing the same random object will have some
			 * performance problems, so we can use ThreadLocalRandom by default
			 */
			double r;
			if (this.randomSupplier != null) {
				r = randomSupplier.get();
			}
			else {
				r = ThreadLocalRandom.current().nextDouble();
			}

			List<Double> ranges = config.ranges;

			if (log.isTraceEnabled()) {
				log.trace("Weight for group: " + group + ", ranges: " + ranges + ", r: " + r);
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

		chain.doFilter(request, response);
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

		GroupWeightConfig(GroupWeightConfig other) {
			this.group = other.group;
			this.weights = new LinkedHashMap<>(other.weights);
			this.normalizedWeights = new LinkedHashMap<>(other.normalizedWeights);
			this.rangeIndexes = new LinkedHashMap<>(other.rangeIndexes);
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("group", group)
				.append("weights", weights)
				.append("normalizedWeights", normalizedWeights)
				.append("rangeIndexes", rangeIndexes)
				.toString();
		}

	}

}
