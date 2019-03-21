/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.socketacceptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.RSocket;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.cloud.gateway.rsocket.filter.RSocketFilter.Success;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class SocketAcceptorPredicateFilterTests {

	@Test
	public void noPredicateWorks() {
		Mono<Success> result = runFilter(Collections.emptyList());
		StepVerifier.create(result).expectNext(Success.INSTANCE).verifyComplete();
	}

	@Test
	public void singleTruePredicateWorks() {
		TestPredicate predicate = new TestPredicate(true);
		Mono<Success> result = runFilter(predicate);
		StepVerifier.create(result).expectNext(Success.INSTANCE).verifyComplete();
		assertThat(predicate.invoked()).isTrue();
	}

	@Test
	public void singleFalsePredicateWorks() {
		TestPredicate predicate = new TestPredicate(false);
		Mono<Success> result = runFilter(predicate);
		StepVerifier.create(result).verifyComplete();

		assertThat(predicate.invoked()).isTrue();
	}

	@Test
	public void multipleFalsePredicateWorks() {
		TestPredicate predicate = new TestPredicate(false);
		TestPredicate predicate2 = new TestPredicate(false);
		Mono<Success> result = runFilter(predicate, predicate2);
		StepVerifier.create(result).verifyComplete();

		assertThat(predicate.invoked()).isTrue();
		assertThat(predicate2.invoked()).isTrue(); // Async predicates don't short circuit
	}

	@Test
	public void multiplePredicatesNoSuccessWorks() {
		TestPredicate truePredicate = new TestPredicate(true);
		TestPredicate falsePredicate = new TestPredicate(false);
		Mono<Success> result = runFilter(truePredicate, falsePredicate);
		StepVerifier.create(result).verifyComplete();
		assertThat(truePredicate.invoked()).isTrue();
		assertThat(falsePredicate.invoked()).isTrue();
	}

	@Test
	public void multiplePredicatesSuccessWorks() {
		TestPredicate truePredicate = new TestPredicate(true);
		TestPredicate truePredicate2 = new TestPredicate(true);
		Mono<Success> result = runFilter(truePredicate, truePredicate2);
		StepVerifier.create(result).expectNext(Success.INSTANCE).verifyComplete();
		assertThat(truePredicate.invoked()).isTrue();
		assertThat(truePredicate2.invoked()).isTrue();
	}

	private Mono<Success> runFilter(SocketAcceptorPredicate predicate) {
		return runFilter(Collections.singletonList(predicate));
	}

	private Mono<Success> runFilter(SocketAcceptorPredicate... predicates) {
		return runFilter(Arrays.asList(predicates));
	}

	private Mono<Success> runFilter(List<SocketAcceptorPredicate> predicates) {
		SocketAcceptorPredicateFilter filter = new SocketAcceptorPredicateFilter(
				predicates);
		SocketAcceptorExchange exchange = new SocketAcceptorExchange(
				mock(ConnectionSetupPayload.class), mock(RSocket.class));
		SocketAcceptorFilterChain filterChain = new SocketAcceptorFilterChain(
				Collections.singletonList(filter));
		return filter.filter(exchange, filterChain);
	}

	private class TestPredicate implements SocketAcceptorPredicate {

		private boolean invoked = false;

		private final Mono<Boolean> test;

		TestPredicate(boolean value) {
			test = Mono.just(value);
		}

		@Override
		public Publisher<Boolean> apply(SocketAcceptorExchange exchange) {
			invoked = true;
			return test;
		}

		public boolean invoked() {
			return invoked;
		}

	}

}
