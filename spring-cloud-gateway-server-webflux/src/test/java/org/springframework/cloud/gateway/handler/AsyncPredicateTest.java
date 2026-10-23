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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.cloud.gateway.handler.AsyncPredicate.AndAsyncPredicate;
import org.springframework.cloud.gateway.handler.AsyncPredicate.DefaultAsyncPredicate;
import org.springframework.cloud.gateway.handler.AsyncPredicate.OrAsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.cloud.gateway.support.HasConfig;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncPredicateTest {

	@Test
	public void andOfSynchronousPredicatesIsSynchronous() {
		List<Integer> tested = new ArrayList<>();
		AsyncPredicate<Object> predicate = new DefaultAsyncPredicate<>(o -> tested.add(0))
			.and(new DefaultAsyncPredicate<>(o -> !tested.add(1)))
			.and(new DefaultAsyncPredicate<>(o -> tested.add(2)));

		assertThat(predicate).isExactlyInstanceOf(DefaultAsyncPredicate.class);
		StepVerifier.create(predicate.apply(new Object())).expectNext(false).verifyComplete();
		assertThat(tested).containsExactly(0, 1);
	}

	@Test
	public void andOfSynchronousPredicatesKeepsDescriptionAndVisitedNodes() {
		GatewayPredicate left = new NamedGatewayPredicate("left");
		GatewayPredicate right = new NamedGatewayPredicate("right");
		AsyncPredicate<ServerWebExchange> predicate = AsyncPredicate.from(left).and(AsyncPredicate.from(right));
		List<HasConfig> visited = new ArrayList<>();

		predicate.accept(visited::add);

		assertThat(predicate).hasToString("(left && right)");
		assertThat(visited).containsExactly(left, right);
	}

	@Test
	public void andOfMixedPredicateDelegatesKeepsVisitedNodes() {
		GatewayPredicate gateway = new NamedGatewayPredicate("gateway");
		AsyncPredicate<ServerWebExchange> plain = new DefaultAsyncPredicate<>(exchange -> true);
		AsyncPredicate<ServerWebExchange> wrapped = AsyncPredicate.from(gateway);

		for (AsyncPredicate<ServerWebExchange> predicate : List.of(plain.and(wrapped), wrapped.and(plain))) {
			List<HasConfig> visited = new ArrayList<>();
			predicate.accept(visited::add);

			assertThat(predicate).isExactlyInstanceOf(AndAsyncPredicate.class);
			assertThat(visited).containsExactly(gateway);
			StepVerifier.create(predicate.apply(null)).expectNext(true).verifyComplete();
		}
	}

	@Test
	public void andWithAsynchronousPredicateStaysAsynchronous() {
		AsyncPredicate<Object> predicate = new DefaultAsyncPredicate<>(o -> true).and(o -> Mono.just(false));

		assertThat(predicate).isExactlyInstanceOf(AndAsyncPredicate.class);
		StepVerifier.create(predicate.apply(new Object())).expectNext(false).verifyComplete();
	}

	@Test
	public void andDoesNotBypassOverriddenApply() {
		AsyncPredicate<Object> overridden = new DefaultAsyncPredicate<>(o -> true) {
			@Override
			public Publisher<Boolean> apply(Object o) {
				return Mono.just(false);
			}
		};
		AsyncPredicate<Object> synchronous = new DefaultAsyncPredicate<>(o -> true);

		StepVerifier.create(synchronous.and(overridden).apply(new Object())).expectNext(false).verifyComplete();
		StepVerifier.create(overridden.and(synchronous).apply(new Object())).expectNext(false).verifyComplete();
	}

	@Test
	public void andPredicateShouldNotTestRightOperatorIfLeftOperatorIsFalse() {
		TestAsyncPredicate<Object> left = new TestAsyncPredicate<>(o -> false);
		TestAsyncPredicate<Object> right = new TestAsyncPredicate<>(o -> true);
		Publisher<Boolean> andTest = left.and(right).apply(new Object());

		StepVerifier.create(andTest).expectNext(false).expectComplete().verify();

		left.assertTested();
		right.assertUntested();
	}

	@Test
	public void andPredicateShouldTestRightOperatorIfLeftOperatorIsTrue() {
		TestAsyncPredicate<Object> left = new TestAsyncPredicate<>(o -> true);
		TestAsyncPredicate<Object> right = new TestAsyncPredicate<>(o -> false);
		Publisher<Boolean> andTest = left.and(right).apply(new Object());

		StepVerifier.create(andTest).expectNext(false).expectComplete().verify();

		left.assertTested();
		right.assertTested();
	}

	@ParameterizedTest
	@CsvSource({ "false, false, false", "false, true, true", "true, false, true", "true, true, true" })
	public void orOfSynchronousPredicatesIsSynchronous(boolean leftResult, boolean rightResult, boolean expected) {
		List<Integer> tested = new ArrayList<>();
		AsyncPredicate<Object> left = new DefaultAsyncPredicate<>(o -> {
			tested.add(0);
			return leftResult;
		});
		AsyncPredicate<Object> right = new DefaultAsyncPredicate<>(o -> {
			tested.add(1);
			return rightResult;
		});
		AsyncPredicate<Object> predicate = left.or(right);

		assertThat(predicate).isExactlyInstanceOf(DefaultAsyncPredicate.class);
		Publisher<Boolean> result = predicate.apply(new Object());
		assertThat(tested).containsExactlyElementsOf(leftResult ? List.of(0) : List.of(0, 1));
		StepVerifier.create(result).expectNext(expected).verifyComplete();
	}

	@Test
	public void orChainOfSynchronousPredicatesIsSynchronous() {
		List<Integer> tested = new ArrayList<>();
		AsyncPredicate<Object> predicate = new DefaultAsyncPredicate<>(o -> !tested.add(0))
			.or(new DefaultAsyncPredicate<>(o -> tested.add(1)))
			.or(new DefaultAsyncPredicate<>(o -> tested.add(2)));

		assertThat(predicate).isExactlyInstanceOf(DefaultAsyncPredicate.class);
		StepVerifier.create(predicate.apply(new Object())).expectNext(true).verifyComplete();
		assertThat(tested).containsExactly(0, 1);
	}

	@Test
	public void orOfSynchronousPredicatesKeepsDescriptionAndVisitedNodes() {
		GatewayPredicate left = new NamedGatewayPredicate("left");
		GatewayPredicate right = new NamedGatewayPredicate("right");
		AsyncPredicate<ServerWebExchange> predicate = AsyncPredicate.from(left).or(AsyncPredicate.from(right));
		List<HasConfig> visited = new ArrayList<>();

		predicate.accept(visited::add);

		assertThat(predicate).hasToString("(left || right)");
		assertThat(visited).containsExactly(left, right);
	}

	@Test
	public void orOfMixedPredicateDelegatesKeepsVisitedNodes() {
		GatewayPredicate gateway = new NamedGatewayPredicate("gateway");
		AsyncPredicate<ServerWebExchange> plain = new DefaultAsyncPredicate<>(exchange -> false);
		AsyncPredicate<ServerWebExchange> wrapped = AsyncPredicate.from(gateway);

		for (AsyncPredicate<ServerWebExchange> predicate : List.of(plain.or(wrapped), wrapped.or(plain))) {
			List<HasConfig> visited = new ArrayList<>();
			predicate.accept(visited::add);

			assertThat(predicate).isExactlyInstanceOf(OrAsyncPredicate.class);
			assertThat(visited).containsExactly(gateway);
			StepVerifier.create(predicate.apply(null)).expectNext(true).verifyComplete();
		}
	}

	@Test
	public void orWithAsynchronousPredicateStaysAsynchronous() {
		List<Integer> tested = new ArrayList<>();
		AsyncPredicate<Object> synchronousFalse = new DefaultAsyncPredicate<>(o -> false);
		AsyncPredicate<Object> asynchronousTrue = o -> Mono.defer(() -> Mono.just(tested.add(0)));
		AsyncPredicate<Object> predicate = synchronousFalse.or(asynchronousTrue);

		assertThat(predicate).isExactlyInstanceOf(OrAsyncPredicate.class);
		Publisher<Boolean> result = predicate.apply(new Object());
		assertThat(tested).isEmpty();
		StepVerifier.create(result).expectNext(true).verifyComplete();
		assertThat(tested).containsExactly(0);

		AsyncPredicate<Object> asynchronousFalse = o -> Mono.defer(() -> Mono.just(false));
		predicate = asynchronousFalse.or(new DefaultAsyncPredicate<>(o -> tested.add(1)));
		assertThat(predicate).isExactlyInstanceOf(OrAsyncPredicate.class);
		result = predicate.apply(new Object());
		assertThat(tested).containsExactly(0);
		StepVerifier.create(result).expectNext(true).verifyComplete();
		assertThat(tested).containsExactly(0, 1);
	}

	@Test
	public void orDoesNotBypassOverriddenApply() {
		AsyncPredicate<Object> overridden = new DefaultAsyncPredicate<>(o -> false) {
			@Override
			public Publisher<Boolean> apply(Object o) {
				return Mono.just(true);
			}
		};
		AsyncPredicate<Object> synchronous = new DefaultAsyncPredicate<>(o -> false);

		assertThat(synchronous.or(overridden)).isExactlyInstanceOf(OrAsyncPredicate.class);
		assertThat(overridden.or(synchronous)).isExactlyInstanceOf(OrAsyncPredicate.class);
		StepVerifier.create(synchronous.or(overridden).apply(new Object())).expectNext(true).verifyComplete();
		StepVerifier.create(overridden.or(synchronous).apply(new Object())).expectNext(true).verifyComplete();
	}

	@Test
	public void orPredicateShouldNotTestRightOperatorIfLeftOperatorIsTrue() {
		TestAsyncPredicate<Object> left = new TestAsyncPredicate<>(o -> true);
		TestAsyncPredicate<Object> right = new TestAsyncPredicate<>(o -> false);
		Publisher<Boolean> orTest = left.or(right).apply(new Object());

		StepVerifier.create(orTest).expectNext(true).expectComplete().verify();

		left.assertTested();
		right.assertUntested();
	}

	@Test
	public void orPredicateShouldTestRightOperatorIfLeftOperatorIsFalse() {
		TestAsyncPredicate<Object> left = new TestAsyncPredicate<>(o -> false);
		TestAsyncPredicate<Object> right = new TestAsyncPredicate<>(o -> true);
		Publisher<Boolean> orTest = left.or(right).apply(new Object());

		StepVerifier.create(orTest).expectNext(true).expectComplete().verify();

		left.assertTested();
		right.assertTested();
	}

	@Test
	public void negateOperatorWorks() {
		TestAsyncPredicate<Object> falsePredicate = new TestAsyncPredicate<>(o -> false);
		TestAsyncPredicate<Object> truePredicate = new TestAsyncPredicate<>(o -> true);
		Publisher<Boolean> falseNot = falsePredicate.negate().apply(new Object());
		Publisher<Boolean> trueNot = truePredicate.negate().apply(new Object());

		StepVerifier.create(falseNot).expectNext(true).expectComplete().verify();
		StepVerifier.create(trueNot).expectNext(false).expectComplete().verify();

		falsePredicate.assertTested();
		truePredicate.assertTested();
	}

	private record NamedGatewayPredicate(String name) implements GatewayPredicate {

		@Override
		public boolean test(ServerWebExchange exchange) {
			return true;
		}

		@Override
		public String toString() {
			return this.name;
		}

	}

	/**
	 * An AsyncPredicate decorator that records if the apply method was called.
	 */
	private final static class TestAsyncPredicate<T> implements AsyncPredicate<T> {

		private final Predicate<T> delegate;

		private boolean tested = false;

		private TestAsyncPredicate(Predicate<T> predicate) {
			this.delegate = predicate;
		}

		@Override
		public Publisher<Boolean> apply(T t) {
			tested = true;
			return Mono.just(delegate.test(t));
		}

		@DisplayName("predicate must have been tested")
		public void assertTested() {
			assertThat(tested).isTrue();
		}

		@DisplayName("predicate must not have been tested")
		public void assertUntested() {
			assertThat(tested).isFalse();
		}

	}

}
