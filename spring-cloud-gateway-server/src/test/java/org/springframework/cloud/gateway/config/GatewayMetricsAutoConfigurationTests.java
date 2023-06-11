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
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.TracingProperties;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayMetricsFilter;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayPropagatingSenderTracingObservationHandler;
import org.springframework.cloud.gateway.filter.headers.observation.ObservationClosingWebExceptionHandler;
import org.springframework.cloud.gateway.filter.headers.observation.ObservedRequestHttpHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.observation.ObservedResponseHttpHeadersFilter;
import org.springframework.cloud.gateway.route.RouteDefinitionMetrics;
import org.springframework.cloud.gateway.support.tagsprovider.GatewayTagsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ingyu Hwang
 */
public class GatewayMetricsAutoConfigurationTests {

	@Nested
	@SpringBootTest(classes = Config.class)
	public class EnabledByDefault {

		@Autowired(required = false)
		private GatewayMetricsFilter filter;

		@Autowired(required = false)
		private RouteDefinitionMetrics routeDefinitionMetrics;

		@Autowired(required = false)
		private List<GatewayTagsProvider> tagsProviders;

		@Autowired
		private BeanFactory beanFactory;

		@Test
		public void gatewayMetricsBeansExists() {
			assertThat(filter).isNotNull();
			assertThat(filter.getMetricsPrefix()).isEqualTo("spring.cloud.gateway");
			assertThat(tagsProviders).isNotEmpty();
		}

		@Test
		public void routeDefinitionMetricsBeanExists() {
			assertThat(routeDefinitionMetrics).isNotNull();
			assertThat(routeDefinitionMetrics.getMetricsPrefix()).isEqualTo("spring.cloud.gateway");
		}

		@Test
		public void observabilityBeansExist() {
			assertThat(beanFactory.getBean(ObservedRequestHttpHeadersFilter.class)).isNotNull();
			assertThat(beanFactory.getBean(ObservedResponseHttpHeadersFilter.class)).isNotNull();
			assertThat(beanFactory.getBean(ObservationClosingWebExceptionHandler.class)).isNotNull();
			assertThat(beanFactory.getBean(GatewayPropagatingSenderTracingObservationHandler.class)).isNotNull();
		}

	}

	@Nested
	@SpringBootTest(classes = Config.class, properties = "spring.cloud.gateway.metrics.enabled=false")
	public class DisabledByProperty {

		@Autowired(required = false)
		private GatewayMetricsFilter filter;

		@Autowired(required = false)
		private RouteDefinitionMetrics routeDefinitionMetrics;

		@Test
		public void gatewayMetricsBeanMissing() {
			assertThat(filter).isNull();
		}

		@Test
		public void routeDefinitionMetricsBeanMissing() {
			assertThat(routeDefinitionMetrics).isNull();
		}

	}

	@Nested
	@SpringBootTest(classes = Config.class, properties = "spring.cloud.gateway.observability.enabled=false")
	public class ObservabilityDisabledByProperty {

		@Autowired
		private BeanFactory beanFactory;

		@Test
		public void observabilityBeansMissing() {
			assertThat(beanFactory.getBeanProvider(ObservedRequestHttpHeadersFilter.class).getIfAvailable(() -> null))
					.isNull();
			assertThat(beanFactory.getBeanProvider(ObservedResponseHttpHeadersFilter.class).getIfAvailable(() -> null))
					.isNull();
			assertThat(
					beanFactory.getBeanProvider(ObservationClosingWebExceptionHandler.class).getIfAvailable(() -> null))
							.isNull();
			assertThat(beanFactory.getBeanProvider(GatewayPropagatingSenderTracingObservationHandler.class)
					.getIfAvailable(() -> null)).isNull();
		}

	}

	@Nested
	@SpringBootTest(classes = CustomTagsProviderConfig.class,
			properties = "spring.cloud.gateway.metrics.prefix=myprefix.")
	public class AddCustomTagsProvider {

		@Autowired(required = false)
		private GatewayMetricsFilter filter;

		@Autowired(required = false)
		private RouteDefinitionMetrics routeDefinitionMetrics;

		@Autowired(required = false)
		private List<GatewayTagsProvider> tagsProviders;

		@Test
		public void gatewayMetricsBeansExists() {
			assertThat(filter).isNotNull();
			assertThat(filter.getMetricsPrefix()).isEqualTo("myprefix");
			assertThat(tagsProviders).extracting("class").contains(CustomTagsProviderConfig.EmptyTagsProvider.class);
		}

		@Test
		public void routeDefinitionMetricsBeanExists() {
			assertThat(routeDefinitionMetrics).isNotNull();
			assertThat(routeDefinitionMetrics.getMetricsPrefix()).isEqualTo("myprefix");
		}

	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@AutoConfigureObservability
	protected static class Config {

		@Bean
		Tracer tracer() {
			return Mockito.mock(Tracer.class);
		}

		@Bean
		Propagator propagator() {
			return Mockito.mock(Propagator.class);
		}

		@Bean
		TracingProperties tracingProperties() {
			return new TracingProperties();
		}

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
