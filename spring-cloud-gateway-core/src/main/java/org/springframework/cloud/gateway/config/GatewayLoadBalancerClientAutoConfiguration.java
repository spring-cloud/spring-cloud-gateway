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

package org.springframework.cloud.gateway.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.filter.LoadBalancerClientFilter;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.netflix.ribbon.RibbonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

/**
 * AutoConfiguration for {@link LoadBalancerClientFilter}.
 *
 * @author Spencer Gibb
 * @author Olga Maciaszek-Sharma
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ LoadBalancerClient.class, RibbonAutoConfiguration.class,
		DispatcherHandler.class })
@AutoConfigureAfter(RibbonAutoConfiguration.class)
@EnableConfigurationProperties(LoadBalancerProperties.class)
public class GatewayLoadBalancerClientAutoConfiguration {

	@Bean
	@ConditionalOnBean(LoadBalancerClient.class)
	@ConditionalOnMissingBean({ LoadBalancerClientFilter.class,
			ReactiveLoadBalancerClientFilter.class })
	public LoadBalancerClientFilter loadBalancerClientFilter(LoadBalancerClient client,
			LoadBalancerProperties properties) {
		return new LoadBalancerClientFilter(client, properties);
	}

}
