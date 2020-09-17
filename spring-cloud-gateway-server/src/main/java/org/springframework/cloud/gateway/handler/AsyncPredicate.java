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

import java.util.function.Function;
import java.util.function.Predicate;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Ben Hale
 */
public interface AsyncPredicate<T> extends Function<T, Publisher<Boolean>> {

	default AsyncPredicate<T> and(AsyncPredicate<? super T> other) {
		return new AndAsyncPredicate<>(this, other);
	}

	default AsyncPredicate<T> negate() {
		return new NegateAsyncPredicate<>(this);
	}

	default AsyncPredicate<T> not(AsyncPredicate<? super T> other) {
		return new NegateAsyncPredicate<>(other);
	}

	default AsyncPredicate<T> or(AsyncPredicate<? super T> other) {
		return new OrAsyncPredicate<>(this, other);
	}

	static AsyncPredicate<ServerWebExchange> from(Predicate<? super ServerWebExchange> predicate) {
		return new DefaultAsyncPredicate<>(GatewayPredicate.wrapIfNeeded(predicate));
	}

	class DefaultAsyncPredicate<T> implements AsyncPredicate<T> {

		private final Predicate<T> delegate;

		public DefaultAsyncPredicate(Predicate<T> delegate) {
			this.delegate = delegate;
		}

		@Override
		public Publisher<Boolean> apply(T t) {
			return Mono.just(delegate.test(t));
		}

		@Override
		public String toString() {
			return this.delegate.toString();
		}

	}

	class NegateAsyncPredicate<T> implements AsyncPredicate<T> {

		private final AsyncPredicate<? super T> predicate;

		public NegateAsyncPredicate(AsyncPredicate<? super T> predicate) {
			Assert.notNull(predicate, "predicate AsyncPredicate must not be null");
			this.predicate = predicate;
		}

		@Override
		public Publisher<Boolean> apply(T t) {
			return Mono.from(predicate.apply(t)).map(b -> !b);
		}

		@Override
		public String toString() {
			return String.format("!(%s)", this.predicate);
		}

	}

	class AndAsyncPredicate<T> implements AsyncPredicate<T> {

		private final AsyncPredicate<? super T> left;

		private final AsyncPredicate<? super T> right;

		public AndAsyncPredicate(AsyncPredicate<? super T> left, AsyncPredicate<? super T> right) {
			Assert.notNull(left, "Left AsyncPredicate must not be null");
			Assert.notNull(right, "Right AsyncPredicate must not be null");
			this.left = left;
			this.right = right;
		}

		@Override
		public Publisher<Boolean> apply(T t) {
			return Mono.from(left.apply(t)).flatMap(result -> !result ? Mono.just(false) : Mono.from(right.apply(t)));
		}

		@Override
		public String toString() {
			return String.format("(%s && %s)", this.left, this.right);
		}

	}

	class OrAsyncPredicate<T> implements AsyncPredicate<T> {

		private final AsyncPredicate<? super T> left;

		private final AsyncPredicate<? super T> right;

		public OrAsyncPredicate(AsyncPredicate<? super T> left, AsyncPredicate<? super T> right) {
			Assert.notNull(left, "Left AsyncPredicate must not be null");
			Assert.notNull(right, "Right AsyncPredicate must not be null");
			this.left = left;
			this.right = right;
		}

		@Override
		public Publisher<Boolean> apply(T t) {
			return Mono.from(left.apply(t)).flatMap(result -> result ? Mono.just(true) : Mono.from(right.apply(t)));
		}

		@Override
		public String toString() {
			return String.format("(%s || %s)", this.left, this.right);
		}

	}

}
