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
import org.junit.jupiter.params.provider.ValueSource;
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

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	public void synchronousCompositionPreservesOrderAndShortCircuiting(boolean useOr) {
		List<Integer> tested = new ArrayList<>();
		AsyncPredicate<Object> left = new DefaultAsyncPredicate<>(o -> {
			tested.add(0);
			return !useOr;
		});
		AsyncPredicate<Object> right = new DefaultAsyncPredicate<>(o -> {
			tested.add(1);
			return useOr;
		});
		AsyncPredicate<Object> skipped = new DefaultAsyncPredicate<>(o -> tested.add(2));
		AsyncPredicate<Object> predicate = useOr ? left.or(right).or(skipped) : left.and(right).and(skipped);

		assertThat(predicate).isExactlyInstanceOf(DefaultAsyncPredicate.class);
		StepVerifier.create(predicate.apply(new Object())).expectNext(useOr).verifyComplete();
		assertThat(tested).containsExactly(0, 1);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	public void synchronousCompositionKeepsDescriptionAndVisitedNodes(boolean useOr) {
		GatewayPredicate left = new NamedGatewayPredicate("left");
		GatewayPredicate right = new NamedGatewayPredicate("right");
		AsyncPredicate<ServerWebExchange> predicate = useOr ? AsyncPredicate.from(left).or(AsyncPredicate.from(right))
				: AsyncPredicate.from(left).and(AsyncPredicate.from(right));
		List<HasConfig> visited = new ArrayList<>();

		predicate.accept(visited::add);

		assertThat(predicate).isExactlyInstanceOf(DefaultAsyncPredicate.class);
		assertThat(predicate).hasToString(useOr ? "(left || right)" : "(left && right)");
		assertThat(visited).containsExactly(left, right);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	public void mixedPredicateDelegatesKeepVisitedNodes(boolean useOr) {
		GatewayPredicate gateway = new NamedGatewayPredicate("gateway");
		AsyncPredicate<ServerWebExchange> plain = new DefaultAsyncPredicate<>(exchange -> !useOr);
		AsyncPredicate<ServerWebExchange> wrapped = AsyncPredicate.from(gateway);
		List<AsyncPredicate<ServerWebExchange>> predicates = useOr ? List.of(plain.or(wrapped), wrapped.or(plain))
				: List.of(plain.and(wrapped), wrapped.and(plain));

		for (AsyncPredicate<ServerWebExchange> predicate : predicates) {
			List<HasConfig> visited = new ArrayList<>();
			predicate.accept(visited::add);

			assertThat(predicate).isExactlyInstanceOf(useOr ? OrAsyncPredicate.class : AndAsyncPredicate.class);
			assertThat(visited).containsExactly(gateway);
			StepVerifier.create(predicate.apply(null)).expectNext(true).verifyComplete();
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	public void compositionWithAsynchronousPredicateStaysDeferred(boolean useOr) {
		List<Integer> tested = new ArrayList<>();
		AsyncPredicate<Object> synchronous = new DefaultAsyncPredicate<>(o -> !useOr);
		AsyncPredicate<Object> asynchronous = o -> Mono.fromSupplier(() -> tested.add(0));
		AsyncPredicate<Object> predicate = useOr ? synchronous.or(asynchronous) : synchronous.and(asynchronous);

		assertThat(predicate).isExactlyInstanceOf(useOr ? OrAsyncPredicate.class : AndAsyncPredicate.class);
		Publisher<Boolean> result = predicate.apply(new Object());
		assertThat(tested).isEmpty();
		StepVerifier.create(result).expectNext(true).verifyComplete();
		assertThat(tested).containsExactly(0);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	public void compositionDoesNotBypassOverriddenApply(boolean useOr) {
		AsyncPredicate<Object> overridden = new DefaultAsyncPredicate<>(o -> !useOr) {
			@Override
			public Publisher<Boolean> apply(Object o) {
				return Mono.just(useOr);
			}
		};
		AsyncPredicate<Object> synchronous = new DefaultAsyncPredicate<>(o -> !useOr);
		List<AsyncPredicate<Object>> predicates = useOr
				? List.of(synchronous.or(overridden), overridden.or(synchronous))
				: List.of(synchronous.and(overridden), overridden.and(synchronous));

		for (AsyncPredicate<Object> predicate : predicates) {
			StepVerifier.create(predicate.apply(new Object())).expectNext(useOr).verifyComplete();
		}
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
