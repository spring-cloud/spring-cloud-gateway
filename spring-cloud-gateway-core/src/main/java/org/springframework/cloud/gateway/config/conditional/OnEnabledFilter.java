/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.config.conditional;

import com.google.common.base.CaseFormat;

import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.support.NameUtils;

public class OnEnabledFilter extends OnEnabledComponent<GatewayFilterFactory<?>> {

	@Override
	protected String normalizeComponentName(
			Class<? extends GatewayFilterFactory<?>> filterClass) {
		String filterName = "";
		if (SpringCloudCircuitBreakerFilterFactory.class.isAssignableFrom(filterClass)) {
			filterName = SpringCloudCircuitBreakerFilterFactory.NAME;
		}
		else {
			filterName = NameUtils.normalizeFilterFactoryName(filterClass);
		}
		return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, filterName);
	}

	@Override
	protected String annotationName() {
		return ConditionalOnEnabledFilter.class.getName();
	}

}
