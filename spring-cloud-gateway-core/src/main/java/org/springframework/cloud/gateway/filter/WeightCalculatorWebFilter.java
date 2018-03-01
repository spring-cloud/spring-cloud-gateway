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

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.gateway.event.WeightDefinedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.WEIGHT_ATTR;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class WeightCalculatorWebFilter implements WebFilter, Ordered, ApplicationListener<WeightDefinedEvent> {

	private static final Log log = LogFactory.getLog(WeightCalculatorWebFilter.class);

	public static final int WEIGHT_CALC_FILTER_ORDER = 10001;
	private Set<String> weightGroups = new CopyOnWriteArraySet<>();

	private Random random = new Random();
	private int order = WEIGHT_CALC_FILTER_ORDER;

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
	public void onApplicationEvent(WeightDefinedEvent event) {
		weightGroups.add(event.getGroupName());
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		Map<String, Double> weights = getWeights(exchange);

		weightGroups.forEach(group -> weights.put(group, random.nextDouble()));

		return chain.filter(exchange);
	}

	@NotNull
	private Map<String, Double> getWeights(ServerWebExchange exchange) {
		Map<String, Double> weights = exchange.getAttribute(WEIGHT_ATTR);

		if (weights == null) {
			weights = new ConcurrentHashMap<>();
			exchange.getAttributes().put(WEIGHT_ATTR, weights);
		}
		return weights;
	}

}
