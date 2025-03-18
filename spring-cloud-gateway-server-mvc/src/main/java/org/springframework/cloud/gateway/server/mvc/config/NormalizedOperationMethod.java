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

package org.springframework.cloud.gateway.server.mvc.config;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.gateway.server.mvc.common.Configurable;
import org.springframework.cloud.gateway.server.mvc.common.NameUtils;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.cloud.gateway.server.mvc.invoke.OperationParameter;
import org.springframework.cloud.gateway.server.mvc.invoke.OperationParameters;
import org.springframework.cloud.gateway.server.mvc.invoke.reflect.DefaultOperationMethod;
import org.springframework.cloud.gateway.server.mvc.invoke.reflect.OperationMethod;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class NormalizedOperationMethod implements OperationMethod {

	private final OperationMethod delegate;

	private final Map<String, Object> normalizedArgs;

	/**
	 * Create a new {@link DefaultOperationMethod} instance.
	 * @param method the source method
	 */
	public NormalizedOperationMethod(OperationMethod delegate, Map<String, Object> args) {
		this.delegate = delegate;
		normalizedArgs = normalizeArgs(args);
	}

	@Override
	public Method getMethod() {
		return delegate.getMethod();
	}

	public boolean isConfigurable() {
		MergedAnnotation<Configurable> configurable = MergedAnnotations.from(delegate.getMethod())
			.get(Configurable.class);
		return configurable.isPresent() && delegate.getParameters().getParameterCount() == 1;
	}

	@Override
	public OperationParameters getParameters() {
		return delegate.getParameters();
	}

	public Map<String, Object> getNormalizedArgs() {
		return normalizedArgs;
	}

	@Override
	public String toString() {
		return delegate.toString();
	}

	private Map<String, Object> normalizeArgs(Map<String, Object> operationArgs) {
		if (hasGeneratedKey(operationArgs)) {
			MergedAnnotation<Shortcut> shortcutMergedAnnotation = MergedAnnotations.from(delegate.getMethod())
				.get(Shortcut.class);
			if (shortcutMergedAnnotation.isPresent()) {
				Shortcut shortcut = shortcutMergedAnnotation.synthesize();
				String[] fieldOrder = getFieldOrder(shortcut);
				return switch (shortcut.type()) {
					case DEFAULT -> {
						Map<String, Object> map = new HashMap<>();
						int entryIdx = 0;
						for (Map.Entry<String, Object> entry : operationArgs.entrySet()) {
							String key = normalizeKey(entry.getKey(), entryIdx, operationArgs, fieldOrder);
							// TODO: support spel?
							// getValue(parser, beanFactory, entry.getValue());
							Object value = entry.getValue();
							map.put(key, value);
							entryIdx++;
						}
						yield map;
					}
					case LIST -> {
						Map<String, Object> map = new HashMap<>();
						// field order should be of size 1
						Assert.isTrue(fieldOrder != null && fieldOrder.length == 1,
								"Shortcut Configuration Type GATHER_LIST must have shortcutFieldOrder of size 1");
						String fieldName = fieldOrder[0];
						// No need to serialize here since that happens on invoke
						map.put(fieldName, StringUtils.collectionToCommaDelimitedString(operationArgs.values()));
						yield map;
					}
					default -> throw new IllegalArgumentException("Unknown Shortcut type " + shortcut.type());
				};
			}
		}
		return operationArgs;
	}

	private String[] getFieldOrder(Shortcut shortcut) {
		String[] fieldOrder = shortcut.fieldOrder();
		if (fieldOrder.length == 0) {
			List<String> paramNames = getParameters().stream().map(OperationParameter::getName).toList();
			fieldOrder = paramNames.toArray(new String[0]);
		}
		return fieldOrder;
	}

	private static boolean hasGeneratedKey(Map<String, Object> operationArgs) {
		return operationArgs.keySet().stream().anyMatch(key -> key.startsWith(NameUtils.GENERATED_NAME_PREFIX));
	}

	static String normalizeKey(String key, int entryIdx, Map<String, Object> args, String[] fieldOrder) {
		// RoutePredicateFactory has name hints and this has a fake key name
		// replace with the matching key hint
		if (key.startsWith(NameUtils.GENERATED_NAME_PREFIX) && fieldOrder.length > 0 && entryIdx < args.size()
				&& entryIdx < fieldOrder.length) {
			key = fieldOrder[entryIdx];
		}
		return key;
	}

}
