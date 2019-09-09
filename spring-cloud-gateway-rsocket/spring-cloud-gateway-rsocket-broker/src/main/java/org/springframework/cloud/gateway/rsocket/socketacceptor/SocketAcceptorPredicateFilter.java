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

import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.rsocket.support.AsyncPredicate;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

public class SocketAcceptorPredicateFilter implements SocketAcceptorFilter, Ordered {

	private final AsyncPredicate<SocketAcceptorExchange> predicate;

	// TODO: change from List to Flux?
	public SocketAcceptorPredicateFilter(List<SocketAcceptorPredicate> predicates) {
		Assert.notNull(predicates, "predicates may not be null");
		if (predicates.isEmpty()) {
			predicate = exchange -> Mono.just(true);
		}
		else {
			AsyncPredicate<SocketAcceptorExchange> combined = predicates.get(0);
			for (SocketAcceptorPredicate p : predicates.subList(1, predicates.size())) {
				combined = combined.and(p);
			}
			predicate = combined;
		}
	}

	@Override
	public int getOrder() {
		return HIGHEST_PRECEDENCE + 10000;
	}

	@Override
	public Mono<Success> filter(SocketAcceptorExchange exchange,
			SocketAcceptorFilterChain chain) {
		return Mono.from(predicate.apply(exchange)).flatMap(test -> {
			if (test) {
				return chain.filter(exchange);
			}
			return Mono.empty();
		});
	}

}
