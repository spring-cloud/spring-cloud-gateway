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

package org.springframework.cloud.gateway.filter.factory;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.metric.consumer.HealthCountsStream;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.netflix.hystrix.ReactiveHystrixCircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.filter.factory.ExceptionFallbackHandler.RETRIEVED_EXCEPTION;

/**
 * @author Ryan Baxter
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = { "debug=true",
		"spring.cloud.circuitbreaker.resilience4j.enabled=false" })
@ContextConfiguration(classes = SpringCloudCircuitBreakerTestConfig.class)
@DirtiesContext
public class SpringCloudCircuitBreakerHystrixFilterFactoryTests
		extends SpringCloudCircuitBreakerFilterFactoryTests {

	@Test
	public void hystrixFilterServiceUnavailable() {
		HealthCountsStream.reset();
		Hystrix.reset();
		ConfigurationManager.getConfigInstance()
				.setProperty("hystrix.command.failcmd.circuitBreaker.forceOpen", true);

		testClient.get().uri("/delay/3").header("Host", "www.sccbfailure.org").exchange()
				.expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

		HealthCountsStream.reset();
		Hystrix.reset();
		ConfigurationManager.getConfigInstance()
				.setProperty("hystrix.command.failcmd.circuitBreaker.forceOpen", false);
	}

	@Test
	public void hystrixFilterExceptionFallback() {
		testClient.get().uri("/delay/3")
				.header("Host", "www.circuitbreakerexceptionfallback.org").exchange()
				.expectStatus().isOk().expectHeader()
				.value(RETRIEVED_EXCEPTION, containsString("HystrixTimeoutException"));
	}

	@Test
	public void toStringFormat() {
		SpringCloudCircuitBreakerFilterFactory.Config config = new SpringCloudCircuitBreakerFilterFactory.Config()
				.setName("myname").setFallbackUri("forward:/myfallback");
		GatewayFilter filter = new SpringCloudCircuitBreakerHystrixFilterFactory(
				new ReactiveHystrixCircuitBreakerFactory(), null).apply(config);
		assertThat(filter.toString()).contains("myname").contains("forward:/myfallback");
	}

}
