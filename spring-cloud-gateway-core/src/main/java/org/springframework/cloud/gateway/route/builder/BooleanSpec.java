/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.route.builder;

import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.route.builder.BooleanSpec.Operator.AND;
import static org.springframework.cloud.gateway.route.builder.BooleanSpec.Operator.OR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.toAsyncPredicate;

/**
 * A spec used to apply logical operators.
 */
public class BooleanSpec extends UriSpec {

	enum Operator { AND, OR, NEGATE }

	final AsyncPredicate<ServerWebExchange> predicate;

	public BooleanSpec(Route.AsyncBuilder routeBuilder, RouteLocatorBuilder.Builder builder) {
		super(routeBuilder, builder);
		// save current predicate useful in kotlin dsl
		predicate = routeBuilder.getPredicate();
	}

	/**
	 * Apply logical {@code and} operator.
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanOpSpec and() {
		return new BooleanOpSpec(routeBuilder, builder, AND);
	}

	/**
	 * Apply logical {@code or} operator.
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanOpSpec or() {
		return new BooleanOpSpec(routeBuilder, builder, OR);
	}

	/**
	 * Negate the logical operator.
	 * @return a {@link BooleanSpec} to be used to add logical operators
	 */
	public BooleanSpec negate() {
		this.routeBuilder.negate();
		return new BooleanSpec(routeBuilder, builder);
	}

	/**
	 * Add filters to the route definition.
	 * @param fn A {@link Function} that takes in a {@link GatewayFilterSpec} and returns a {@link UriSpec}
	 * @return a {@link UriSpec}
	 */
	public UriSpec filters(Function<GatewayFilterSpec, UriSpec> fn) {
		return fn.apply(new GatewayFilterSpec(routeBuilder, builder));
	}

	public static class BooleanOpSpec extends PredicateSpec {

		private Operator operator;

		BooleanOpSpec(Route.AsyncBuilder routeBuilder, RouteLocatorBuilder.Builder builder, Operator operator) {
			super(routeBuilder, builder);
			Assert.notNull(operator, "operator may not be null");
			this.operator = operator;
		}

		public BooleanSpec predicate(Predicate<ServerWebExchange> predicate) {
		    return asyncPredicate(toAsyncPredicate(predicate));
		}

		@Override
		public BooleanSpec asyncPredicate(AsyncPredicate<ServerWebExchange> predicate) {
			switch (this.operator) {
				case AND:
					this.routeBuilder.and(predicate);
					break;
				case OR:
					this.routeBuilder.or(predicate);
					break;
				case NEGATE:
					this.routeBuilder.negate();
			}
			return new BooleanSpec(this.routeBuilder, this.builder);
		}
	}

}
