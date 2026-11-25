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

package org.springframework.cloud.gateway.support;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AbstractStatefulConfigurable}.
 *
 * <p>
 * The per-route config map is written while routes are built, which can happen on
 * several threads at once (for example a custom refresh overlapping the refresh on
 * startup). Concurrent writes must not drop entries: a missing entry makes
 * {@code RedisRateLimiter#isAllowed} throw for every request on that route until the
 * next refresh.
 *
 * @author Jaeyeob Jung
 */
public class AbstractStatefulConfigurableTests {

	private static final int THREADS = 8;

	private static final int ROUNDS = 500;

	private static final int ROUTES = 25;

	@Test
	public void concurrentWritesKeepEveryRouteConfig() throws Exception {
		List<String> routeIds = IntStream.range(0, ROUTES).mapToObj(i -> "route-" + i).toList();
		ExecutorService executor = Executors.newFixedThreadPool(THREADS);
		try {
			for (int round = 0; round < ROUNDS; round++) {
				TestConfigurable configurable = new TestConfigurable();
				CyclicBarrier startTogether = new CyclicBarrier(THREADS);
				// every thread writes the same route ids into an initially empty map
				Callable<Void> populate = () -> {
					startTogether.await();
					for (String routeId : routeIds) {
						configurable.getConfig().put(routeId, new Object());
					}
					return null;
				};
				List<Future<Void>> results = executor.invokeAll(Collections.nCopies(THREADS, populate));
				for (Future<Void> result : results) {
					result.get();
				}
				assertThat(configurable.getConfig()).as("round %d", round).containsOnlyKeys(routeIds);
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	static class TestConfigurable extends AbstractStatefulConfigurable<Object> {

		TestConfigurable() {
			super(Object.class);
		}

	}

}
