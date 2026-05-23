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

package org.springframework.cloud.gateway.handler;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.handler.AsyncPredicate.AndAsyncPredicate;
import org.springframework.cloud.gateway.handler.AsyncPredicate.NegateAsyncPredicate;
import org.springframework.cloud.gateway.handler.AsyncPredicate.OrAsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate.AndGatewayPredicate;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate.NegateGatewayPredicate;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate.OrGatewayPredicate;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.mock.http.server.reactive.MockServerHttpRequest.get;

/**
 * Tests for {@link AsyncPredicate} and {@link GatewayPredicate} equality contracts.
 *
 * Relates to: https://github.com/spring-cloud/spring-cloud-gateway/issues/3236
 */
class AsyncPredicateEqualsTests {

	// -----------------------------------------------------------------------
	// DefaultAsyncPredicate equality
	// -----------------------------------------------------------------------

	@Test
	void defaultAsyncPredicateSameInstanceIsEqual() {
		AsyncPredicate<ServerWebExchange> predicate = buildPathPredicate("/api");
		assertThat(predicate).isEqualTo(predicate);
	}

	@Test
	void defaultAsyncPredicateWithSameDelegateIsEqual() {
		GatewayPredicate delegate = buildGatewayPredicate("/api");
		AsyncPredicate<ServerWebExchange> p1 = new AsyncPredicate.DefaultAsyncPredicate<>(delegate);
		AsyncPredicate<ServerWebExchange> p2 = new AsyncPredicate.DefaultAsyncPredicate<>(delegate);
		assertThat(p1).isEqualTo(p2);
		assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
	}

	@Test
	void defaultAsyncPredicateWithDifferentDelegateIsNotEqual() {
		AsyncPredicate<ServerWebExchange> p1 = buildPathPredicate("/api");
		AsyncPredicate<ServerWebExchange> p2 = buildPathPredicate("/other");
		assertThat(p1).isNotEqualTo(p2);
	}

	@Test
	void defaultAsyncPredicateIsNotEqualToNull() {
		AsyncPredicate<ServerWebExchange> predicate = buildPathPredicate("/api");
		assertThat(predicate).isNotEqualTo(null);
	}

	// -----------------------------------------------------------------------
	// NegateAsyncPredicate equality
	// -----------------------------------------------------------------------

	@Test
	void negateAsyncPredicateWithSameInnerPredicateIsEqual() {
		AsyncPredicate<ServerWebExchange> inner = buildPathPredicate("/api");
		NegateAsyncPredicate<ServerWebExchange> p1 = new NegateAsyncPredicate<>(inner);
		NegateAsyncPredicate<ServerWebExchange> p2 = new NegateAsyncPredicate<>(inner);
		assertThat(p1).isEqualTo(p2);
		assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
	}

	@Test
	void negateAsyncPredicateWithDifferentInnerPredicateIsNotEqual() {
		NegateAsyncPredicate<ServerWebExchange> p1 = new NegateAsyncPredicate<>(buildPathPredicate("/a"));
		NegateAsyncPredicate<ServerWebExchange> p2 = new NegateAsyncPredicate<>(buildPathPredicate("/b"));
		assertThat(p1).isNotEqualTo(p2);
	}

	// -----------------------------------------------------------------------
	// AndAsyncPredicate equality
	// -----------------------------------------------------------------------

	@Test
	void andAsyncPredicateWithSameComponentsIsEqual() {
		AsyncPredicate<ServerWebExchange> left = buildPathPredicate("/api");
		AsyncPredicate<ServerWebExchange> right = buildPathPredicate("/v1");
		AndAsyncPredicate<ServerWebExchange> p1 = new AndAsyncPredicate<>(left, right);
		AndAsyncPredicate<ServerWebExchange> p2 = new AndAsyncPredicate<>(left, right);
		assertThat(p1).isEqualTo(p2);
		assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
	}

	@Test
	void andAsyncPredicateWithDifferentLeftIsNotEqual() {
		AsyncPredicate<ServerWebExchange> right = buildPathPredicate("/v1");
		AndAsyncPredicate<ServerWebExchange> p1 = new AndAsyncPredicate<>(buildPathPredicate("/api"), right);
		AndAsyncPredicate<ServerWebExchange> p2 = new AndAsyncPredicate<>(buildPathPredicate("/other"), right);
		assertThat(p1).isNotEqualTo(p2);
	}

	@Test
	void andAsyncPredicateWithDifferentRightIsNotEqual() {
		AsyncPredicate<ServerWebExchange> left = buildPathPredicate("/api");
		AndAsyncPredicate<ServerWebExchange> p1 = new AndAsyncPredicate<>(left, buildPathPredicate("/v1"));
		AndAsyncPredicate<ServerWebExchange> p2 = new AndAsyncPredicate<>(left, buildPathPredicate("/v2"));
		assertThat(p1).isNotEqualTo(p2);
	}

	// -----------------------------------------------------------------------
	// OrAsyncPredicate equality
	// -----------------------------------------------------------------------

	@Test
	void orAsyncPredicateWithSameComponentsIsEqual() {
		AsyncPredicate<ServerWebExchange> left = buildPathPredicate("/api");
		AsyncPredicate<ServerWebExchange> right = buildPathPredicate("/v1");
		OrAsyncPredicate<ServerWebExchange> p1 = new OrAsyncPredicate<>(left, right);
		OrAsyncPredicate<ServerWebExchange> p2 = new OrAsyncPredicate<>(left, right);
		assertThat(p1).isEqualTo(p2);
		assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
	}

	@Test
	void orAsyncPredicateWithDifferentComponentsIsNotEqual() {
		AsyncPredicate<ServerWebExchange> left = buildPathPredicate("/api");
		OrAsyncPredicate<ServerWebExchange> p1 = new OrAsyncPredicate<>(left, buildPathPredicate("/v1"));
		OrAsyncPredicate<ServerWebExchange> p2 = new OrAsyncPredicate<>(left, buildPathPredicate("/v2"));
		assertThat(p1).isNotEqualTo(p2);
	}

	// -----------------------------------------------------------------------
	// GatewayPredicate inner class equality
	// -----------------------------------------------------------------------

	@Test
	void negateGatewayPredicateWithSameInnerIsEqual() {
		GatewayPredicate inner = buildGatewayPredicate("/api");
		NegateGatewayPredicate p1 = new NegateGatewayPredicate(inner);
		NegateGatewayPredicate p2 = new NegateGatewayPredicate(inner);
		assertThat(p1).isEqualTo(p2);
		assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
	}

	@Test
	void andGatewayPredicateWithSameComponentsIsEqual() {
		GatewayPredicate left = buildGatewayPredicate("/api");
		GatewayPredicate right = buildGatewayPredicate("/v1");
		AndGatewayPredicate p1 = new AndGatewayPredicate(left, right);
		AndGatewayPredicate p2 = new AndGatewayPredicate(left, right);
		assertThat(p1).isEqualTo(p2);
		assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
	}

	@Test
	void orGatewayPredicateWithSameComponentsIsEqual() {
		GatewayPredicate left = buildGatewayPredicate("/api");
		GatewayPredicate right = buildGatewayPredicate("/v1");
		OrGatewayPredicate p1 = new OrGatewayPredicate(left, right);
		OrGatewayPredicate p2 = new OrGatewayPredicate(left, right);
		assertThat(p1).isEqualTo(p2);
		assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
	}

	// -----------------------------------------------------------------------
	// Composite predicate (via .and() / .or() / .negate()) equality
	// -----------------------------------------------------------------------

	@Test
	void compositeAndPredicateBuiltFromSamePartsIsEqual() {
		GatewayPredicate left = buildGatewayPredicate("/api");
		GatewayPredicate right = buildGatewayPredicate("/v1");

		AsyncPredicate<ServerWebExchange> p1 = AsyncPredicate.from(left).and(AsyncPredicate.from(right));
		AsyncPredicate<ServerWebExchange> p2 = AsyncPredicate.from(left).and(AsyncPredicate.from(right));

		assertThat(p1).isEqualTo(p2);
		assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
	}

	@Test
	void compositeOrPredicateBuiltFromSamePartsIsEqual() {
		GatewayPredicate left = buildGatewayPredicate("/api");
		GatewayPredicate right = buildGatewayPredicate("/v1");

		AsyncPredicate<ServerWebExchange> p1 = AsyncPredicate.from(left).or(AsyncPredicate.from(right));
		AsyncPredicate<ServerWebExchange> p2 = AsyncPredicate.from(left).or(AsyncPredicate.from(right));

		assertThat(p1).isEqualTo(p2);
		assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
	}

	@Test
	void negatedPredicateBuiltFromSamePartIsEqual() {
		GatewayPredicate inner = buildGatewayPredicate("/api");

		AsyncPredicate<ServerWebExchange> p1 = AsyncPredicate.from(inner).negate();
		AsyncPredicate<ServerWebExchange> p2 = AsyncPredicate.from(inner).negate();

		assertThat(p1).isEqualTo(p2);
		assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
	}

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	/**
	 * Creates an AsyncPredicate wrapping a simple path-matching GatewayPredicate.
	 * Uses a concrete GatewayPredicate so equals() can compare by config value.
	 */
	private AsyncPredicate<ServerWebExchange> buildPathPredicate(String path) {
		return AsyncPredicate.from(buildGatewayPredicate(path));
	}

	/**
	 * Builds a simple GatewayPredicate that checks if the request path starts with the
	 * given prefix. Overrides equals/hashCode so two instances with the same prefix
	 * are considered equal — this mirrors what factory-created predicates should do.
	 */
	private GatewayPredicate buildGatewayPredicate(String pathPrefix) {
		return new GatewayPredicate() {
			@Override
			public boolean test(ServerWebExchange exchange) {
				return exchange.getRequest().getPath().value().startsWith(pathPrefix);
			}

			@Override
			public boolean equals(Object o) {
				if (this == o) return true;
				if (!(o instanceof GatewayPredicate other)) return false;
				// Compare by the toString representation which encodes the path
				return this.toString().equals(other.toString());
			}

			@Override
			public int hashCode() {
				return pathPrefix.hashCode();
			}

			@Override
			public String toString() {
				return "PathPredicate[" + pathPrefix + "]";
			}
		};
	}

}
