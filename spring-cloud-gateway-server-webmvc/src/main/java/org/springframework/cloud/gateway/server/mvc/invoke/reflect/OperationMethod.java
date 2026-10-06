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

package org.springframework.cloud.gateway.server.mvc.invoke.reflect;

import java.lang.reflect.Method;

import org.jspecify.annotations.Nullable;

import org.springframework.cloud.gateway.server.mvc.invoke.OperationParameters;

/**
 * Information describing a gateway operation method and its invocation target.
 *
 * @author zephyr45
 */
public interface OperationMethod {

	Method getMethod();

	/**
	 * Return the target object for the operation method.
	 * @return the target object, or {@code null} for a static method
	 */
	default @Nullable Object getTarget() {
		return null;
	}

	OperationParameters getParameters();

	default boolean isConfigurable() {
		return false;
	}

}
