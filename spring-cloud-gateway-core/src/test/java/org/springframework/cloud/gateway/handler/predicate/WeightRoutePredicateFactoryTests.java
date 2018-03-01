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

import java.util.HashMap;
import java.util.function.Predicate;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.event.WeightDefinedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.WEIGHT_ATTR;

/**
 * @author Spencer Gibb
 */
public class WeightRoutePredicateFactoryTests {

	@Test
	public void predicateWork() {
		testWeight(0.0, 0.0, 0.5, true);
		testWeight(0.2, 0.0, 0.5, true);
		testWeight(0.5, 0.0, 0.5, false);
		testWeight(0.6, 0.0, 0.5, false);
		testWeight(-0.6, 0.0, 0.5, false);
		testWeight(0.1, 0.0, 0.0, false);
		testWeight("unknowngroup", 0.1, 0.0, 0.0, false, false);

		testWeightEx("test",-1.0, 0.5, true);
		testWeightEx("test",0.0, 1.5, true);
		testWeightEx("test",1.0, 0.5, true);
		testWeightEx(null, 0.0, 0.5, true);
		testWeightEx("",0.0, 0.5, true);
	}

	private void testWeight(double weight, double lowerBound, double upperBound, boolean expected) {
		testWeight("test", weight, lowerBound, upperBound, expected, false);
	}

	private void testWeightEx(String group, double lowerBound, double upperBound, boolean expectException) {
		testWeight(group, 0.2, lowerBound, upperBound, false, expectException);
	}

	private void testWeight(String group, double weight, double lowerBound, double upperBound, boolean expected, boolean expectException) {
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://localhosts"));
		HashMap<String, Double> weights = new HashMap<>();
		weights.put(group, weight);
		exchange.getAttributes().put(WEIGHT_ATTR, weights);

		ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
		Predicate<ServerWebExchange> predicate = null;
		try {
			WeightRoutePredicateFactory factory = new WeightRoutePredicateFactory();
			factory.setApplicationEventPublisher(publisher);
			predicate = factory.apply(group, lowerBound, upperBound);
		} catch (IllegalArgumentException e) {
			if (!expectException) {
				fail("Unexpected exception", e);
			} else {
				return;
			}
		}
		boolean result = predicate.test(exchange);

		ArgumentCaptor<WeightDefinedEvent> argument = ArgumentCaptor.forClass(WeightDefinedEvent.class);

		verify(publisher).publishEvent(argument.capture());

		assertThat(argument.getValue().getGroupName()).isEqualTo(group);

		assertThat(result)
				.as("Predicate value was wrong for weight %s, lower bound %s and upperbound %s",
						weight, lowerBound, upperBound)
				.isEqualTo(expected);
	}
}
