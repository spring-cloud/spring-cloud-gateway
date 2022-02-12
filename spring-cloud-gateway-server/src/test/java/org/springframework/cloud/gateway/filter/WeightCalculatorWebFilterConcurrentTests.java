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

package org.springframework.cloud.gateway.filter;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.netty.util.internal.ThreadLocalRandom;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.event.WeightDefinedEvent;
import org.springframework.cloud.gateway.support.WeightConfig;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Bug #1459: WeightCalculatorWebFilter is not thread safe
 *
 * @author Alexey Nakidkin
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("weights-concurrent")
@DirtiesContext
public class WeightCalculatorWebFilterConcurrentTests {

	@Value("${test.concurrent.execution.timeInSeconds:5}")
	private int maxTestTimeSeconds;

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Autowired
	private WeightCalculatorWebFilter weightCalculatorWebFilter;

	private ExecutorService executorService;

	private long startTime;

	@Before
	public void setUp() {
		executorService = Executors.newSingleThreadExecutor();
		startTime = System.currentTimeMillis();
	}

	@After
	public void teardown() {
		executorService.shutdown();
	}

	@Test
	@Ignore
	public void WeightCalculatorWebFilter_threadSafeTest() {
		generateEvents();

		ServerWebExchange serverWebExchangeMock = Mockito.mock(ServerWebExchange.class);
		WebFilterChain emptyWebFilterChain = serverWebExchange -> Mono.empty();

		while (isContinue()) {
			weightCalculatorWebFilter.filter(serverWebExchangeMock, emptyWebFilterChain);
		}
	}

	private boolean isContinue() {
		return (System.currentTimeMillis() - startTime) < TimeUnit.SECONDS.toMillis(maxTestTimeSeconds);
	}

	private void generateEvents() {
		executorService.execute(() -> {
			while (isContinue()) {
				eventPublisher.publishEvent(createWeightDefinedEvent());
			}
		});
	}

	private WeightDefinedEvent createWeightDefinedEvent() {
		int weight = ThreadLocalRandom.current().nextInt() & Integer.MAX_VALUE;
		WeightConfig config = new WeightConfig("group_1", UUID.randomUUID().toString(), weight);
		return new WeightDefinedEvent(new Object(), config);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(BaseWebClientTests.DefaultTestConfig.class)
	public static class CustomConfig {

	}

}
