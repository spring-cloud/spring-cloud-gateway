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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.event.WeightDefinedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.tuple.Tuple;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.WEIGHT_ATTR;

/**
 * @author Spencer Gibb
 */
public class WeightRoutePredicateFactory implements RoutePredicateFactory, ApplicationEventPublisherAware {

	private static final Log log = LogFactory.getLog(WeightRoutePredicateFactory.class);

	public static final String GROUP_KEY = "group";
	public static final String LOWER_BOUND_KEY = "lower";
	public static final String UPPER_BOUND_KEY = "upper";

	private ApplicationEventPublisher publisher;

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public List<String> argNames() {
		return Arrays.asList(GROUP_KEY, LOWER_BOUND_KEY, UPPER_BOUND_KEY);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Tuple args) {
		String group = args.getString(GROUP_KEY);
		double lowerBound = args.getDouble(LOWER_BOUND_KEY);
		double upperBound = 1.0;
		if (args.hasFieldName(UPPER_BOUND_KEY)) {
			upperBound = args.getDouble(UPPER_BOUND_KEY);
		}

		return apply(group, lowerBound, upperBound);
	}

	public Predicate<ServerWebExchange> apply(String group, double lowerBound, double upperBound) {
		Assert.hasLength(group, GROUP_KEY + " must not be null or empty");
		Assert.isTrue(lowerBound >= 0, LOWER_BOUND_KEY + " must be greater than or equal to zero");
		Assert.isTrue(upperBound <= 1.0, UPPER_BOUND_KEY + " must be less than or equal to one");
		Assert.isTrue(lowerBound <= upperBound, LOWER_BOUND_KEY + " must be less than " + UPPER_BOUND_KEY);

		if (this.publisher != null) {
			this.publisher.publishEvent(new WeightDefinedEvent(this, group));
		}

		return exchange -> {
			Map<String, Double> weights = exchange.getAttributeOrDefault(WEIGHT_ATTR,
					Collections.emptyMap());

			String key = group.toLowerCase();
			if (weights.containsKey(key)) {

				if (log.isTraceEnabled()) {
					log.trace("in weight: "+ lowerBound + " : " + weights.get(key) + " : " + upperBound);
				}

				return lowerBound <= weights.get(key) && weights.get(key) < upperBound;
			}

			return false;
		};
	}
}
