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

import org.springframework.beans.BeanUtils;
import org.springframework.core.style.ToStringCreator;

public abstract class AbstractConfigurable<C> implements Configurable<C> {
	private Class<C> configClass;

	protected AbstractConfigurable(Class<C> configClass) {
		this.configClass = configClass;
	}

	public Class<C> getConfigClass() {
		return configClass;
	}

	@Override
	public C newConfig() {
		return BeanUtils.instantiateClass(this.configClass);
	}

	@Override
	public String toString() {
		return new ToStringCreator(this)
				.append("configClass", configClass)
				.toString();
	}
}
