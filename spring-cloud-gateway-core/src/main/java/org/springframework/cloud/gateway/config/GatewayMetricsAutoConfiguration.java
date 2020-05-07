/*
 * Copyright 2013-2018 the original author or authors.
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

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayMetricsFilter;
import org.springframework.cloud.gateway.support.tagsprovider.GatewayHttpTagsProvider;
import org.springframework.cloud.gateway.support.tagsprovider.GatewayRouteTagsProvider;
import org.springframework.cloud.gateway.support.tagsprovider.GatewayTagsProvider;
import org.springframework.cloud.gateway.support.tagsprovider.PropertiesTagsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
@EnableConfigurationProperties(GatewayMetricsProperties.class)
@AutoConfigureBefore(HttpHandlerAutoConfiguration.class)
@AutoConfigureAfter({ MetricsAutoConfiguration.class,
		CompositeMeterRegistryAutoConfiguration.class })
@ConditionalOnClass({ DispatcherHandler.class, MeterRegistry.class,
		MetricsAutoConfiguration.class })
public class GatewayMetricsAutoConfiguration {

	@Bean
	public GatewayHttpTagsProvider gatewayHttpTagsProvider() {
		return new GatewayHttpTagsProvider();
	}

	@Bean
	public GatewayRouteTagsProvider gatewayRouteTagsProvider() {
		return new GatewayRouteTagsProvider();
	}

	@Bean
	public PropertiesTagsProvider propertiesTagsProvider(
			GatewayMetricsProperties gatewayMetricsProperties) {
		return new PropertiesTagsProvider(gatewayMetricsProperties.getTags());
	}

	@Bean
	@ConditionalOnBean(MeterRegistry.class)
	@ConditionalOnProperty(name = "spring.cloud.gateway.metrics.enabled",
			matchIfMissing = true)
	public GatewayMetricsFilter gatewayMetricFilter(MeterRegistry meterRegistry,
			List<GatewayTagsProvider> tagsProviders) {
		return new GatewayMetricsFilter(meterRegistry, tagsProviders);
	}

}
