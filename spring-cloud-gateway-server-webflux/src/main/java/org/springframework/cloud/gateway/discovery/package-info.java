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

/**
 * Service discovery integration for Spring Cloud Gateway.
 *
 * <p>
 * This package provides support for dynamic route discovery using Spring Cloud's
 * {@link org.springframework.cloud.client.discovery.DiscoveryClient}. It enables
 * automatic route creation based on registered service instances in service registries
 * such as Eureka, Consul, or Kubernetes.
 * </p>
 *
 * <p>
 * Key components:
 * <ul>
 * <li>{@link org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocator}
 * - Locates route definitions from discovered services</li>
 * <li>{@link org.springframework.cloud.gateway.discovery.GatewayDiscoveryClientAutoConfiguration}
 * - Auto-configuration for discovery client integration</li>
 * </ul>
 * </p>
 *
 * @see org.springframework.cloud.client.discovery.DiscoveryClient
 * @see org.springframework.cloud.gateway.route.RouteDefinitionLocator
 */
@NullMarked
package org.springframework.cloud.gateway.discovery;

import org.jspecify.annotations.NullMarked;