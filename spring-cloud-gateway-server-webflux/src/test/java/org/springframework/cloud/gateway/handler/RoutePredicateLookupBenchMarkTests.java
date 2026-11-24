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
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import reactor.core.publisher.Flux;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
@Threads(2)
public class RoutePredicateLookupBenchMarkTests {

	@Param({ "10", "100", "1000" })
	private int routeCount;

	@Param({ "1", "2", "4" })
	private int predicateCount;

	private List<Route> routes;

	private ServerWebExchange exchange;

	private Route expectedRoute;

	@Setup(Level.Trial)
	public void setup() {
		this.exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/benchmark").build());
		List<Route> routes = new ArrayList<>(this.routeCount);
		for (int routeIndex = 0; routeIndex < this.routeCount; routeIndex++) {
			final int candidateRoute = routeIndex;
			AsyncPredicate<ServerWebExchange> predicate = AsyncPredicate.from(candidate -> true);
			for (int predicateIndex = 0; predicateIndex < this.predicateCount; predicateIndex++) {
				final boolean matchingPredicate = predicateIndex == this.predicateCount - 1;
				predicate = predicate
					.and(AsyncPredicate.from(candidate -> !matchingPredicate || candidateRoute == this.routeCount - 1));
			}
			routes.add(Route.async()
				.id("route-" + routeIndex)
				.uri("http://example.org")
				.asyncPredicate(predicate)
				.build());
		}
		this.routes = List.copyOf(routes);
		this.expectedRoute = this.routes.get(this.routeCount - 1);
	}

	@Benchmark
	public Route lookupRoute() {
		Route route = Flux.fromIterable(this.routes)
			.filterWhen(candidate -> candidate.getPredicate().apply(this.exchange))
			.next()
			.block();
		if (route != this.expectedRoute) {
			throw new IllegalStateException("Unexpected route");
		}
		return route;
	}

	public static void main(String[] args) throws Exception {
		org.openjdk.jmh.Main.main(args);
	}

}
