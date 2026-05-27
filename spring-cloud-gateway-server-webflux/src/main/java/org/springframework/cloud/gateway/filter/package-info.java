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
 * WITHOUT WARRANTIES or conditions of ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Gateway filters for request and response processing.
 *
 * <p>
 * This package contains the core filter implementations for Spring Cloud Gateway.
 * Filters are used to modify incoming requests and outgoing responses as they pass
 * through the gateway. They can be applied globally or scoped to specific routes.
 * </p>
 *
 * <p>
 * Filter types include:
 * <ul>
 * <li><strong>Global Filters:</strong> Applied to all routes</li>
 * <li><strong>route Filters:</strong> Applied to specific routes</li>
 * <li><strong>Pre-filters:</strong> Execute before the request is routed</li>
 * <li><strong>Post-filters:</strong> Execute after the response is received</li>
 * </ul>
 * </p>
 *
 * <p>
 * Common filter implementations:
 * <ul>
 * <li>{@link org.springframework.cloud.gateway.filter.NettyRoutingFilter}
 * - Routes requests using Netty HTTP client</li>
 * <li>{@link org.springframework.cloud.gateway.filter.ForwardRoutingFilter}
 * - Forwards requests to local endpoints</li>
 * <li>{@link org.springframework.cloud.gateway.filter.FunctionRoutingFilter}
 * - Routes requests to Spring Cloud Function endpoints</li>
 * </ul>
 * </p>
 *
 * @see org.springframework.cloud.gateway.filter.GatewayFilter
 * @see org.springframework.cloud.gateway.filter.GlobalFilter
 */
@NullMarked
package org.springframework.cloud.gateway.filter;

import org.jspecify.annotations.NullMarked;