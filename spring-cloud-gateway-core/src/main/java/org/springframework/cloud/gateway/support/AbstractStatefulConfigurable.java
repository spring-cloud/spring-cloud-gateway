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

package org.springframework.cloud.gateway.support;

import java.util.HashMap;
import java.util.Map;

import org.springframework.core.style.ToStringCreator;

public abstract class AbstractStatefulConfigurable<C> extends AbstractConfigurable<C> implements StatefulConfigurable<C> {
	private Map<String, C> config = new HashMap<>();

	protected AbstractStatefulConfigurable(Class<C> configClass) {
		super(configClass);
	}

	@Override
	public Map<String, C> getConfig() {
		return this.config;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this)
				.append("config", config)
				.append("configClass", getConfigClass())
				.toString();
	}
}
