/*
 * Copyright 2012-present the original author or authors.
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
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.springframework.cloud.gateway.server.mvc.invoke.OperationParameter;
import org.springframework.cloud.gateway.server.mvc.invoke.OperationParameters;
import org.springframework.core.ParameterNameDiscoverer;

/**
 * {@link OperationParameters} created from an {@link OperationMethod}.
 *
 * @author Phillip Webb
 */
class OperationMethodParameters implements OperationParameters {

	private final List<OperationParameter> operationParameters;

	/**
	 * Create a new {@link OperationMethodParameters} instance.
	 * @param method the source method
	 * @param parameterNameDiscoverer the parameter name discoverer
	 */
	OperationMethodParameters(Method method, ParameterNameDiscoverer parameterNameDiscoverer) {
		Objects.requireNonNull(method, "Method must not be null");
		Objects.requireNonNull(parameterNameDiscoverer, "ParameterNameDiscoverer must not be null");
		@Nullable String @Nullable [] parameterNames = parameterNameDiscoverer.getParameterNames(method);
		Parameter[] parameters = method.getParameters();
		this.operationParameters = getOperationParameters(parameters, parameterNames);
	}

	private List<OperationParameter> getOperationParameters(Parameter[] parameters,
			@Nullable String @Nullable [] names) {
		List<OperationParameter> operationParameters = new ArrayList<>(parameters.length);
		Objects.requireNonNull(names, "Parameter names must not be null");
		for (int i = 0; i < names.length; i++) {
			String name = names[i];
			Objects.requireNonNull(name, "Parameter name must not be null");
			operationParameters.add(new OperationMethodParameter(name, parameters[i]));
		}
		return Collections.unmodifiableList(operationParameters);
	}

	@Override
	public int getParameterCount() {
		return this.operationParameters.size();
	}

	@Override
	public OperationParameter get(int index) {
		return this.operationParameters.get(index);
	}

	@Override
	public Iterator<OperationParameter> iterator() {
		return this.operationParameters.iterator();
	}

	@Override
	public Stream<OperationParameter> stream() {
		return this.operationParameters.stream();
	}

}
