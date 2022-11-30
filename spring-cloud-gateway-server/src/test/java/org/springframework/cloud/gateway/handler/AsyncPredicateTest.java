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

package org.springframework.cloud.gateway.handler;

import java.util.function.Predicate;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class AsyncPredicateTest {

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
			Assertions.assertTrue(tested);
		}

		@DisplayName("predicate must not have been tested")
		public void assertUntested() {
			Assertions.assertFalse(tested);
		}

	}

}
