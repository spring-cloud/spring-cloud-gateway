/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.filter;

import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public interface RSocketExchange {

	/**
	 * Return a mutable map of request attributes for the current exchange.
	 * @return current attributes.
	 */
	Map<String, Object> getAttributes();

	/**
	 * Return the request attribute value if present.
	 * @param name the attribute name
	 * @param <T> the attribute type
	 * @return the attribute value
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	default <T> T getAttribute(String name) {
		return (T) getAttributes().get(name);
	}

	/**
	 * Return the request attribute value or if not present raise an
	 * {@link IllegalArgumentException}.
	 * @param name the attribute name
	 * @param <T> the attribute type
	 * @return the attribute value
	 */
	@SuppressWarnings("unchecked")
	default <T> T getRequiredAttribute(String name) {
		T value = getAttribute(name);
		Assert.notNull(value, () -> "Required attribute '" + name + "' is missing");
		return value;
	}

	/**
	 * Return the request attribute value, or a default, fallback value.
	 * @param name the attribute name
	 * @param defaultValue a default value to return instead
	 * @param <T> the attribute type
	 * @return the attribute value
	 */
	@SuppressWarnings("unchecked")
	default <T> T getAttributeOrDefault(String name, T defaultValue) {
		return (T) getAttributes().getOrDefault(name, defaultValue);
	}

}
