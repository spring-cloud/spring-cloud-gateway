/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.common;

import java.util.Map;

import org.springframework.cloud.gateway.server.mvc.common.ArgumentSupplier.ArgumentSuppliedEvent;
import org.springframework.context.ApplicationEvent;

public class AttributedArugmentSuppliedEvent<T> extends ApplicationEvent implements ArgumentSuppliedEvent<T> {

	private final ArgumentSuppliedEvent<T> event;

	private final Map<String, Object> attributes;

	public AttributedArugmentSuppliedEvent(ArgumentSuppliedEvent<T> event, Map<String, Object> attributes) {
		super(event.getSource());
		this.event = event;
		this.attributes = attributes;
	}

	@Override
	public Class<T> getType() {
		return event.getType();
	}

	@Override
	public T getArgument() {
		return event.getArgument();
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

}
