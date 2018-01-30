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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import javax.validation.ValidationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.tuple.Tuple;
import org.springframework.web.server.ServerWebExchange;

/**
 * The throttle predicate allows a certain rate of requests to pass. The rate is a double
 * between 0 and 1. Example: to allow 25% of the traffic set a rate of 0.25.
 *
 * @author Thomas Scheuchzer
 */
public class ThrottleRoutePredicateFactory implements RoutePredicateFactory {

	private static final Log log = LogFactory.getLog(ThrottleRoutePredicateFactory.class);

	public static final String RATE_KEY = "rate";

	@Override
	public List<String> argNames() {
		return Arrays.asList(RATE_KEY);
	}

	@Override
	public Predicate<ServerWebExchange> apply(final Tuple args) {
		final double rate = Double.valueOf(args.getString(RATE_KEY));
		return apply(rate);
	}

	public Predicate<ServerWebExchange> apply(final double rate) {
		if (rate < 0.0 || rate > 1.0) {
			throw new ValidationException(
					"Rate must be between 0 and 1 (both inclusive)");
		}

		final String rateString = String.valueOf(rate);
		final int fractals = rateString.substring(rateString.indexOf('.') + 1).length();
		final long divisor = (long) Math.pow(10, fractals);
		final long moduloThreshold = (int) (rate * divisor);
		final long greatestCommonDivisor = BigInteger.valueOf(divisor)
				.gcd(BigInteger.valueOf(moduloThreshold)).longValue();
		return new ThrottlePredicate(divisor / greatestCommonDivisor,
				moduloThreshold / greatestCommonDivisor);
	}

	public static class ThrottlePredicate implements Predicate<ServerWebExchange> {
		private final long divisor;
		private final long moduloThreshold;
		private final AtomicLong counter = new AtomicLong();
		// if the atomic long counter becomes a bottleneck an alternative would be a
		// LongAdder but the adder doesn't have a incrementAndGet. Therefore race
		// conditions could occur. But maybe a fast counter with eventually wrong values
		// is acceptable in this case.
		// private LongAdder counter = new LongAdder();

		private ThrottlePredicate(final long divisor, final long moduloThreshold) {
			this.divisor = divisor;
			this.moduloThreshold = moduloThreshold;
		}

		@Override
		public boolean test(final ServerWebExchange serverWebExchange) {
			final long value = counter.getAndIncrement();
			return value % divisor < moduloThreshold;
		}
	}
}
