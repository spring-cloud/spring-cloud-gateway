/*
 * Copyright 2013-2017 the original author or authors.
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
 */

package org.springframework.cloud.gateway.route.builder;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import java.util.function.Predicate;

import static org.springframework.cloud.gateway.route.builder.BooleanSpec.Operator.*;

public class BooleanSpec extends GatewayFilterSpec {

	enum Operator { AND, OR, NEGATE }

	public BooleanSpec(Route.Builder routeBuilder, RouteLocatorBuilder.Builder builder) {
		super(routeBuilder, builder);
	}

	public BooleanOpSpec and() {
		return new BooleanOpSpec(routeBuilder, builder, AND);
	}

	public BooleanOpSpec or() {
		return new BooleanOpSpec(routeBuilder, builder, OR);
	}

	public BooleanOpSpec negate() {
		return new BooleanOpSpec(routeBuilder, builder, NEGATE);
	}

	public static class BooleanOpSpec extends PredicateSpec {

		private Operator operator;

		BooleanOpSpec(Route.Builder routeBuilder, RouteLocatorBuilder.Builder builder, Operator operator) {
			super(routeBuilder, builder);
			Assert.notNull(operator, "operator may not be null");
			this.operator = operator;
		}

		@Override
		public BooleanSpec predicate(Predicate<ServerWebExchange> predicate) {
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
			return gatewayFilterBuilder();
		}
	}
}
