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

import org.springframework.context.ApplicationEvent;

public class DefaultArgumentSuppliedEvent<T> extends ApplicationEvent
		implements ArgumentSupplier.ArgumentSuppliedEvent<T> {

	private final Class<T> type;

	private final T argument;

	public DefaultArgumentSuppliedEvent(Object source, Class<T> type, T argument) {
		super(source);
		this.type = type;
		this.argument = argument;
	}

	@Override
	public Class<T> getType() {
		return type;
	}

	@Override
	public T getArgument() {
		return argument;
	}

}
