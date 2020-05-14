/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.filter.ratelimit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * @author Emmanouil Gkatziouras
 */
@RunWith(MockitoJUnitRunner.class)
public class LocalRateLimiterUnitTests {

	private static final int DEFAULT_REPLENISH_RATE = 1;

	private static final int DEFAULT_REFRESH_PERIOD = 1;

	public static final String ROUTE_ID = "routeId";

	public static final String REQUEST_ID = "id";

	public static final String[] CONFIGURATION_SERVICE_BEANS = new String[0];

	@Mock
	private ApplicationContext applicationContext;

	private LocalRateLimiter localRateLimiter;

	@Before
	public void setUp() {
		when(applicationContext.getBeanNamesForType(ConfigurationService.class))
				.thenReturn(CONFIGURATION_SERVICE_BEANS);
		localRateLimiter = new LocalRateLimiter(DEFAULT_REPLENISH_RATE,
				DEFAULT_REFRESH_PERIOD);
	}

	@After
	public void tearDown() {
		Mockito.reset(applicationContext);
	}

	@Test(expected = IllegalStateException.class)
	public void shouldThrowWhenNotInitialized() {
		localRateLimiter.isAllowed(ROUTE_ID, REQUEST_ID);
	}

	@Test
	public void shouldReturnHeaders() {
		localRateLimiter.setApplicationContext(applicationContext);
		Mono<RateLimiter.Response> response = localRateLimiter.isAllowed(ROUTE_ID,
				REQUEST_ID);
		assertThat(response.block().getHeaders()).containsOnly(
				entry(localRateLimiter.getRemainingHeader(), "0"),
				entry(localRateLimiter.getRefreshPeriodHeader(),
						DEFAULT_REFRESH_PERIOD+ ""),
				entry(localRateLimiter.getReplenishRateHeader(),
						DEFAULT_REPLENISH_RATE + ""),
				entry(localRateLimiter.getRequestedTokensHeader(), "1"));
	}

}
