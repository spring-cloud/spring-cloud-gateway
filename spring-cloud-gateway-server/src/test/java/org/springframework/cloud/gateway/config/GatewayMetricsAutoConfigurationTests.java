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

package org.springframework.cloud.gateway.config;

import java.util.List;

import io.micrometer.core.instrument.Tags;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayMetricsFilter;
import org.springframework.cloud.gateway.support.tagsprovider.GatewayTagsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ingyu Hwang
 */
@RunWith(Enclosed.class)
public class GatewayMetricsAutoConfigurationTests {

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class)
	public static class EnabledByDefault {

		@Autowired(required = false)
		private GatewayMetricsFilter filter;

		@Autowired(required = false)
		private List<GatewayTagsProvider> tagsProviders;

		@Test
		public void gatewayMetricsBeansExists() {
			assertThat(filter).isNotNull();
			assertThat(filter.getMetricsPrefix()).isEqualTo("spring.cloud.gateway");
			assertThat(tagsProviders).isNotEmpty();
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class, properties = "spring.cloud.gateway.metrics.enabled=false")
	public static class DisabledByProperty {

		@Autowired(required = false)
		private GatewayMetricsFilter filter;

		@Test
		public void gatewayMetricsBeanMissing() {
			assertThat(filter).isNull();
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = CustomTagsProviderConfig.class,
			properties = "spring.cloud.gateway.metrics.prefix=myprefix.")
	public static class AddCustomTagsProvider {

		@Autowired(required = false)
		private GatewayMetricsFilter filter;

		@Autowired(required = false)
		private List<GatewayTagsProvider> tagsProviders;

		@Test
		public void gatewayMetricsBeansExists() {
			assertThat(filter).isNotNull();
			assertThat(filter.getMetricsPrefix()).isEqualTo("myprefix");
			assertThat(tagsProviders).extracting("class").contains(CustomTagsProviderConfig.EmptyTagsProvider.class);
		}

	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class Config {

	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class CustomTagsProviderConfig {

		@Bean
		public GatewayTagsProvider emptyTagsProvider() {
			return new EmptyTagsProvider();
		}

		protected static class EmptyTagsProvider implements GatewayTagsProvider {

			@Override
			public Tags apply(ServerWebExchange exchange) {
				return Tags.empty();
			}

		}

	}

}
