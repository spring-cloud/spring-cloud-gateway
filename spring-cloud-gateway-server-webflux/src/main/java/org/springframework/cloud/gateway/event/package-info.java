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
 * Event publishing and handling for Spring Cloud Gateway.
 *
 * <p>
 * This package provides event-driven capabilities for gateway lifecycle and route
 * management. It enables applications to react to gateway events such as route
 * refreshes, route definitions being added or removed, and other gateway state changes.
 * </p>
 *
 * <p>
 * Key components:
 * <ul>
 * <li>Event classes for various gateway lifecycle events</li>
 * <li>Event publishers for notifying listeners of gateway state changes</li>
 * <li>Integration with Spring's event publishing mechanism</li>
 * </ul>
 * </p>
 *
 * <p>
 * Applications can listen to these events by implementing
 * {@link org.springframework.context.ApplicationListener} or using the
 * {@link org.springframework.context.event.EventListener} annotation.
 * </p>
 *
 * @see org.springframework.context.ApplicationEvent
 * @see org.springframework.context.ApplicationListener
 */
@NullMarked
package org.springframework.cloud.gateway.event;

import org.jspecify.annotations.NullMarked;