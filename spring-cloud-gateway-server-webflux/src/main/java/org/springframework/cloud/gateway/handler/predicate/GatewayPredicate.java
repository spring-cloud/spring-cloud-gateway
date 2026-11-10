/*
 * Copyright 2013-present the original author or authors.
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

import java.util.Objects;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.support.HasConfig;
import org.springframework.cloud.gateway.support.Visitor;
import org.springframework.web.server.ServerWebExchange;

/**
 * A {@link Predicate} that is specific to Spring Cloud Gateway, providing additional
 * methods for composing predicates and visiting them.
 */
public interface GatewayPredicate extends Predicate<ServerWebExchange>, HasConfig {

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

	default void accept(Visitor visitor) {
		visitor.visit(this);
	}

	static GatewayPredicate wrapIfNeeded(Predicate<? super ServerWebExchange> other) {
		GatewayPredicate right;
		if (other instanceof GatewayPredicate gatewayPredicate) {
			right = gatewayPredicate;
		}
		else {
			right = new GatewayPredicateWrapper(other);
		}
		return right;
	}

	class GatewayPredicateWrapper implements GatewayPredicate {

		private final Predicate<? super ServerWebExchange> delegate;

		public GatewayPredicateWrapper(Predicate<? super ServerWebExchange> delegate) {
			Objects.requireNonNull(delegate, "delegate GatewayPredicate must not be null");
			this.delegate = delegate;
		}

		@Override
		public boolean test(ServerWebExchange exchange) {
			return this.delegate.test(exchange);
		}

		@Override
		public void accept(Visitor visitor) {
			if (delegate instanceof GatewayPredicate gatewayPredicate) {
				gatewayPredicate.accept(visitor);
			}
		}

		@Override
		public String toString() {
			return this.delegate.getClass().getSimpleName();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof GatewayPredicateWrapper)) {
				return false;
			}
			GatewayPredicateWrapper that = (GatewayPredicateWrapper) o;
			return Objects.equals(this.delegate, that.delegate);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(this.delegate);
		}

	}

	class NegateGatewayPredicate implements GatewayPredicate {

		private final GatewayPredicate predicate;

		public NegateGatewayPredicate(GatewayPredicate predicate) {
			Objects.requireNonNull(predicate, "predicate GatewayPredicate must not be null");
			this.predicate = predicate;
		}

		@Override
		public boolean test(ServerWebExchange exchange) {
			return !this.predicate.test(exchange);
		}

		@Override
		public void accept(Visitor visitor) {
			predicate.accept(visitor);
		}

		@Override
		public String toString() {
			return String.format("!%s", this.predicate);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof NegateGatewayPredicate)) {
				return false;
			}
			NegateGatewayPredicate that = (NegateGatewayPredicate) o;
			return Objects.equals(this.predicate, that.predicate);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(this.predicate);
		}

	}

	class AndGatewayPredicate implements GatewayPredicate {

		private final GatewayPredicate left;

		private final GatewayPredicate right;

		public AndGatewayPredicate(GatewayPredicate left, GatewayPredicate right) {
			Objects.requireNonNull(left, "Left GatewayPredicate must not be null");
			Objects.requireNonNull(right, "Right GatewayPredicate must not be null");
			this.left = left;
			this.right = right;
		}

		@Override
		public boolean test(ServerWebExchange exchange) {
			return this.left.test(exchange) && this.right.test(exchange);
		}

		@Override
		public void accept(Visitor visitor) {
			left.accept(visitor);
			right.accept(visitor);
		}

		@Override
		public String toString() {
			return String.format("(%s && %s)", this.left, this.right);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof AndGatewayPredicate)) {
				return false;
			}
			AndGatewayPredicate that = (AndGatewayPredicate) o;
			return Objects.equals(this.left, that.left) && Objects.equals(this.right, that.right);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.left, this.right);
		}

	}

	class OrGatewayPredicate implements GatewayPredicate {

		private final GatewayPredicate left;

		private final GatewayPredicate right;

		public OrGatewayPredicate(GatewayPredicate left, GatewayPredicate right) {
			Objects.requireNonNull(left, "Left GatewayPredicate must not be null");
			Objects.requireNonNull(right, "Right GatewayPredicate must not be null");
			this.left = left;
			this.right = right;
		}

		@Override
		public boolean test(ServerWebExchange exchange) {
			return this.left.test(exchange) || this.right.test(exchange);
		}

		@Override
		public void accept(Visitor visitor) {
			left.accept(visitor);
			right.accept(visitor);
		}

		@Override
		public String toString() {
			return String.format("(%s || %s)", this.left, this.right);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof OrGatewayPredicate)) {
				return false;
			}
			OrGatewayPredicate that = (OrGatewayPredicate) o;
			return Objects.equals(this.left, that.left) && Objects.equals(this.right, that.right);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.left, this.right);
		}

	}

}
