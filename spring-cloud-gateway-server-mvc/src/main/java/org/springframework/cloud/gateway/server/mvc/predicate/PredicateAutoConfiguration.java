/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.predicate;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class PredicateAutoConfiguration {

	@Bean
	public PredicateBeanFactoryDiscoverer predicateBeanFactoryDiscoverer(BeanFactory beanFactory) {
		return new PredicateBeanFactoryDiscoverer(beanFactory);
	}

	@Bean
	MvcPredicateSupplier mvcPredicateSupplier() {
		return new MvcPredicateSupplier();
	}

	@Bean
	GatewayRequestPredicates.PredicateSupplier gatewayRequestPredicateSupplier() {
		return new GatewayRequestPredicates.PredicateSupplier();
	}

}
