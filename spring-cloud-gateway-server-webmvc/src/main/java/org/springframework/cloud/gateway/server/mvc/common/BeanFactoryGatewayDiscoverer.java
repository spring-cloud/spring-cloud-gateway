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

package org.springframework.cloud.gateway.server.mvc.common;

import java.util.List;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;

public abstract class BeanFactoryGatewayDiscoverer extends AbstractGatewayDiscoverer {

	protected final BeanFactory beanFactory;

	protected BeanFactoryGatewayDiscoverer(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	protected <T> List<T> loadSuppliers(Class<T> supplierClass) {
		ObjectProvider<T> beanProvider = beanFactory.getBeanProvider(supplierClass);
		return beanProvider.orderedStream().toList();
	}

}
