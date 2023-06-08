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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.cloud.gateway.server.mvc.invoke.reflect.OperationMethod;
import org.springframework.core.io.support.SpringFactoriesLoader;

public abstract class AbstractGatewayDiscoverer {

	protected volatile Map<String, OperationMethod> operations;

	public <T extends Supplier<Collection<Method>>, R> void doDiscover(Class<T> supplierClass, Class<R> returnType) {
		List<T> suppliers = loadSuppliers(supplierClass);

		List<Method> methods = new ArrayList<>();
		for (Supplier<Collection<Method>> supplier : suppliers) {
			methods.addAll(supplier.get());
		}

		operations = new HashMap<>();
		for (Method method : methods) {
			// TODO: replace with a BiPredicate of some kind
			if (method.getReturnType().isAssignableFrom(returnType)) {
				addOperationMethod(operations, method);
			}
		}
	}

	protected void addOperationMethod(Map<String, OperationMethod> predicateOperations, Method method) {
		OperationMethod operationMethod = new OperationMethod(method);
		String key = method.getName();
		if (predicateOperations.containsKey(key)) {
			System.err.println("Replacing factory: " + key);
		}
		predicateOperations.put(key, operationMethod);
		System.err.println("OperationMethod " + operationMethod);
	}

	public abstract void discover();

	protected <T> List<T> loadSuppliers(Class<T> supplierClass) {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

		List<T> suppliers = SpringFactoriesLoader.loadFactories(supplierClass, classLoader);
		return suppliers;
	}

	public Map<String, OperationMethod> getOperations() {
		if (operations == null || operations.isEmpty()) {
			discover();
		}
		return operations;
	}

}
