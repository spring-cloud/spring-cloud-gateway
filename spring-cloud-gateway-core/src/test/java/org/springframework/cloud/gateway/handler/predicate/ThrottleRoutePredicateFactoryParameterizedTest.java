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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.LongStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Thomas Scheuchzer
 */
@RunWith(Parameterized.class)
public class ThrottleRoutePredicateFactoryParameterizedTest {

	@Parameterized.Parameters(name = "{index}: rate {0} should match {2} requests out of {1}")
	public static Iterable<Object[]> data() {
		return Arrays.asList(new Object[][] { { 0, 100, 0 }, { 0.001, 1000, 1 },
				{ 0.01, 1000, 10 }, { 0.01, 100, 1 }, { 0.1, 100, 10 }, { 0.5, 100, 50 },
				{ 0.125, 1000, 125 }, { 0.125, 100, 13 }, { 0.125, 10, 2 },
				{ 1, 100, 100 } });
	}

	@Parameterized.Parameter(0)
	public double rate;
	@Parameterized.Parameter(1)
	public long totalCalls;
	@Parameterized.Parameter(2)
	public long matchingCalls;

	private ServerWebExchange exchange = null; // good enough for this test

	@Test
	public void throttle() {
		final Predicate<ServerWebExchange> predicate = new ThrottleRoutePredicateFactory()
				.apply(rate);
		final long matchedCound = LongStream.range(0, totalCalls)
				.filter(x -> predicate.test(exchange)).count();
		assertThat(matchedCound, is(matchingCalls));
	}

}