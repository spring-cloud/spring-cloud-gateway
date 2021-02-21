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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

public interface GatewayPredicate extends Predicate<ServerWebExchange> {

	@Override
	default Predicate<ServerWebExchange> and(Predicate<? super ServerWebExchange> other) {
		return new AndGatewayPredicate(this, wrapIfNeeded(other));
	}

	@Override
	default Predicate<ServerWebExchange> negate() {
		return new NegateGatewayPredicate(this);
	}

	@Override
	default Predicate<ServerWebExchange> or(Predicate<? super ServerWebExchange> other) {
		return new OrGatewayPredicate(this, wrapIfNeeded(other));
	}

	static GatewayPredicate wrapIfNeeded(Predicate<? super ServerWebExchange> other) {
		GatewayPredicate right;

		if (other instanceof GatewayPredicate) {
			right = (GatewayPredicate) other;
		}
		else {
			right = new GatewayPredicateWrapper(other);
		}
		return right;
	}

	class GatewayPredicateWrapper implements GatewayPredicate {

		private final Predicate<? super ServerWebExchange> delegate;

		public GatewayPredicateWrapper(Predicate<? super ServerWebExchange> delegate) {
			Assert.notNull(delegate, "delegate GatewayPredicate must not be null");
			this.delegate = delegate;
		}

		@Override
		public boolean test(ServerWebExchange exchange) {
			return this.delegate.test(exchange);
		}

		@Override
		public String toString() {
			return this.delegate.getClass().getSimpleName();
		}

	}

	class NegateGatewayPredicate implements GatewayPredicate {

		private final GatewayPredicate predicate;

		public NegateGatewayPredicate(GatewayPredicate predicate) {
			Assert.notNull(predicate, "predicate GatewayPredicate must not be null");
			this.predicate = predicate;
		}

		@Override
		public boolean test(ServerWebExchange t) {
			return !this.predicate.test(t);
		}

		@Override
		public String toString() {
			return String.format("!%s", this.predicate);
		}

	}

	class AndGatewayPredicate implements GatewayPredicate {

		private final GatewayPredicate left;

		private final GatewayPredicate right;

		public AndGatewayPredicate(GatewayPredicate left, GatewayPredicate right) {
			Assert.notNull(left, "Left GatewayPredicate must not be null");
			Assert.notNull(right, "Right GatewayPredicate must not be null");
			this.left = left;
			this.right = right;
		}

		@Override
		public boolean test(ServerWebExchange t) {
			return (this.left.test(t) && this.right.test(t));
		}

		@Override
		public String toString() {
			return String.format("(%s && %s)", this.left, this.right);
		}

	}

	class OrGatewayPredicate implements GatewayPredicate {

		private final GatewayPredicate left;

		private final GatewayPredicate right;

		public OrGatewayPredicate(GatewayPredicate left, GatewayPredicate right) {
			Assert.notNull(left, "Left GatewayPredicate must not be null");
			Assert.notNull(right, "Right GatewayPredicate must not be null");
			this.left = left;
			this.right = right;
		}

		@Override
		public boolean test(ServerWebExchange t) {
			return (this.left.test(t) || this.right.test(t));
		}

		@Override
		public String toString() {
			return String.format("(%s || %s)", this.left, this.right);
		}

	}

}
