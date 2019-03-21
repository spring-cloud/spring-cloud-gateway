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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Arrays;
import java.util.List;

import javax.validation.constraints.NotEmpty;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.annotation.Validated;

public abstract class AbstractNameValueGatewayFilterFactory extends AbstractGatewayFilterFactory<AbstractNameValueGatewayFilterFactory.NameValueConfig> {

	public AbstractNameValueGatewayFilterFactory() {
		super(NameValueConfig.class);
	}

	public List<String> shortcutFieldOrder() {
        return Arrays.asList(GatewayFilter.NAME_KEY, GatewayFilter.VALUE_KEY);
    }


	@Validated
	public static class NameValueConfig {
		@NotEmpty
		protected String name;
		@NotEmpty
		protected String value;

		public String getName() {
			return name;
		}

		public NameValueConfig setName(String name) {
			this.name = name;
			return this;
		}

		public String getValue() {
			return value;
		}

		public NameValueConfig setValue(String value) {
			this.value = value;
			return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this)
					.append("name", name)
					.append("value", value)
					.toString();
		}
	}
}
