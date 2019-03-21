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
 *
 */

package org.springframework.cloud.gateway.event;

import java.util.Map;

import org.springframework.context.ApplicationEvent;

public class FilterArgsEvent extends ApplicationEvent {
	private String routeId;
	private final Map<String, Object> args;

	public FilterArgsEvent(Object source, String routeId, Map<String, Object> args) {
		super(source);
		this.routeId = routeId;
		this.args = args;
	}

	public String getRouteId() {
		return routeId;
	}

	public Map<String, Object> getArgs() {
		return args;
	}
}
