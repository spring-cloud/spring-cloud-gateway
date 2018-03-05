/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.support.AbstractConfigurable;
import org.springframework.tuple.Tuple;

public abstract class AbstractGatewayFilterFactory<C>
		extends AbstractConfigurable<C> implements GatewayFilterFactory<C> {

	@SuppressWarnings("unchecked")
	public AbstractGatewayFilterFactory() {
		super((Class<C>) Object.class);
	}

	public AbstractGatewayFilterFactory(Class<C> configClass) {
		super(configClass);
	}

	@Override
	public boolean isConfigurable() {
		return true;
	}

	@Override
	public GatewayFilter apply(Tuple args) {
		throw new UnsupportedOperationException("apply(Tuple) not supported");
	}

	public static class NameConfig {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
