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

package org.springframework.cloud.gateway.test;

import org.junit.Assert;
import org.junit.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;

public class RouteConstructionIntegrationTests {

	@Test
	public void routesWithVerificationShouldFail() {
		Assert.assertThrows(Throwable.class, () -> {
			new SpringApplicationBuilder(TestConfig.class).profiles("verification-route")
					.run();
		});
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestConfig {

		@Bean
		public TestFilterGatewayFilterFactory testFilterGatewayFilterFactory() {
			return new TestFilterGatewayFilterFactory();
		}

	}

	public static class TestFilterGatewayFilterFactory
			extends AbstractGatewayFilterFactory<TestFilterGatewayFilterFactory.Config> {

		public TestFilterGatewayFilterFactory() {
			super(Config.class);
		}

		@Override
		public GatewayFilter apply(Config config) {
			throw new AssertionError("Stop right now!");
		}

		public static class Config {

			private String arg1;

			public String getArg1() {
				return arg1;
			}

			public void setArg1(String arg1) {
				this.arg1 = arg1;
			}

		}

	}

}
