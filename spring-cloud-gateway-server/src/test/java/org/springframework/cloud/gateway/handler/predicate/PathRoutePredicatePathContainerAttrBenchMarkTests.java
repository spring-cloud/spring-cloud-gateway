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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

public class PathRoutePredicatePathContainerAttrBenchMarkTests {

	private static List<Predicate<ServerWebExchange>> predicates;

	private static String PATH_PATTERN_PREFIX;

	private final static String HOST = "http://localhost:8080";

	private final static int ROUTES_NUM = 2000;

	static {
		predicates = new LinkedList<>();
		Random random = new Random();
		String path1 = String.format("%1$" + 20 + "s", random.nextInt()).replace(' ', '0');
		String path2 = String.format("%1$" + 10 + "s", random.nextInt()).replace(' ', '0');
		PATH_PATTERN_PREFIX = String.format("/%s/%s/", path1, path2);
		for (int i = 0; i < ROUTES_NUM; i++) {
			PathRoutePredicateFactory.Config config = new PathRoutePredicateFactory.Config()
				.setPatterns(Collections.singletonList(PATH_PATTERN_PREFIX + i))
				.setMatchTrailingSlash(true);
			Predicate<ServerWebExchange> predicate = new PathRoutePredicateFactory().apply(config);
			predicates.add(predicate);
		}
	}

	@Benchmark
	@Threads(2)
	@Fork(2)
	@BenchmarkMode(Mode.All)
	@Warmup(iterations = 1, time = 3)
	@Measurement(iterations = 10, time = 1)
	public void testPathContainerAttr() {
		Random random = new Random();
		MockServerHttpRequest request = MockServerHttpRequest
			.get(HOST + PATH_PATTERN_PREFIX + random.nextInt(ROUTES_NUM))
			.build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		for (Predicate<ServerWebExchange> predicate : predicates) {
			if (predicate.test(exchange)) {
				break;
			}
		}
	}

	public static void main(String[] args) throws Exception {
		org.openjdk.jmh.Main.main(args);
	}

}
