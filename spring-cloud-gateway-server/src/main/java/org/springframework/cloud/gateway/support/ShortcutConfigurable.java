/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.env.Environment;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.ConstructorResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.OperatorOverloader;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypeComparator;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.TypedValue;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * @author Spencer Gibb
 */
public interface ShortcutConfigurable {

	static String normalizeKey(String key, int entryIdx, ShortcutConfigurable argHints, Map<String, String> args) {
		// RoutePredicateFactory has name hints and this has a fake key name
		// replace with the matching key hint
		if (key.startsWith(NameUtils.GENERATED_NAME_PREFIX) && !argHints.shortcutFieldOrder().isEmpty()
				&& entryIdx < args.size() && entryIdx < argHints.shortcutFieldOrder().size()) {
			key = argHints.shortcutFieldOrder().get(entryIdx);
		}
		return key;
	}

	static Object getValue(SpelExpressionParser parser, BeanFactory beanFactory, String entryValue) {
		Object value;
		String rawValue = entryValue;
		if (rawValue != null) {
			rawValue = rawValue.trim();
		}
		if (rawValue != null && rawValue.startsWith("#{") && entryValue.endsWith("}")) {
			// assume it's spel
			GatewayEvaluationContext context = new GatewayEvaluationContext(beanFactory);
			Expression expression = parser.parseExpression(entryValue, new TemplateParserContext());
			value = expression.getValue(context);
		}
		else {
			value = entryValue;
		}
		return value;
	}

	default ShortcutType shortcutType() {
		return ShortcutType.DEFAULT;
	}

	/**
	 * Returns hints about the number of args and the order for shortcut parsing.
	 * @return the list of hints
	 */
	default List<String> shortcutFieldOrder() {
		return Collections.emptyList();
	}

	default String shortcutFieldPrefix() {
		return "";
	}

	enum ShortcutType {

		/**
		 * Default shortcut type.
		 */
		DEFAULT {
			@Override
			public Map<String, Object> normalize(Map<String, String> args, ShortcutConfigurable shortcutConf,
					SpelExpressionParser parser, BeanFactory beanFactory) {
				Map<String, Object> map = new HashMap<>();
				int entryIdx = 0;
				for (Map.Entry<String, String> entry : args.entrySet()) {
					String key = normalizeKey(entry.getKey(), entryIdx, shortcutConf, args);
					Object value = getValue(parser, beanFactory, entry.getValue());

					map.put(key, value);
					entryIdx++;
				}
				return map;
			}
		},

		/**
		 * List shortcut type.
		 */
		GATHER_LIST {
			@Override
			public Map<String, Object> normalize(Map<String, String> args, ShortcutConfigurable shortcutConf,
					SpelExpressionParser parser, BeanFactory beanFactory) {
				Map<String, Object> map = new HashMap<>();
				// field order should be of size 1
				List<String> fieldOrder = shortcutConf.shortcutFieldOrder();
				Assert.isTrue(fieldOrder != null && fieldOrder.size() == 1,
						"Shortcut Configuration Type GATHER_LIST must have shortcutFieldOrder of size 1");
				String fieldName = fieldOrder.get(0);
				map.put(fieldName, args.values().stream().map(value -> getValue(parser, beanFactory, value))
						.collect(Collectors.toList()));
				return map;
			}
		},

		/**
		 * List is all elements except last which is a boolean flag.
		 */
		GATHER_LIST_TAIL_FLAG {
			@Override
			public Map<String, Object> normalize(Map<String, String> args, ShortcutConfigurable shortcutConf,
					SpelExpressionParser parser, BeanFactory beanFactory) {
				Map<String, Object> map = new HashMap<>();
				// field order should be of size 1
				List<String> fieldOrder = shortcutConf.shortcutFieldOrder();
				Assert.isTrue(fieldOrder != null && fieldOrder.size() == 2,
						"Shortcut Configuration Type GATHER_LIST_HEAD must have shortcutFieldOrder of size 2");
				List<String> values = new ArrayList<>(args.values());
				if (!values.isEmpty()) {
					// strip boolean flag if last entry is true or false
					int lastIdx = values.size() - 1;
					String lastValue = values.get(lastIdx);
					if (lastValue.equalsIgnoreCase("true") || lastValue.equalsIgnoreCase("false")) {
						values = values.subList(0, lastIdx);
						map.put(fieldOrder.get(1), getValue(parser, beanFactory, lastValue));
					}
				}
				String fieldName = fieldOrder.get(0);
				map.put(fieldName, values.stream().map(value -> getValue(parser, beanFactory, value))
						.collect(Collectors.toList()));
				return map;
			}
		};

		public abstract Map<String, Object> normalize(Map<String, String> args, ShortcutConfigurable shortcutConf,
				SpelExpressionParser parser, BeanFactory beanFactory);

	}

	class GatewayEvaluationContext implements EvaluationContext {

		private final BeanFactoryResolver beanFactoryResolver;

		private final SimpleEvaluationContext delegate;

		public GatewayEvaluationContext(BeanFactory beanFactory) {
			this.beanFactoryResolver = new BeanFactoryResolver(beanFactory);
			Environment env = beanFactory.getBean(Environment.class);
			boolean restrictive = env.getProperty("spring.cloud.gateway.restrictive-property-accessor.enabled",
					Boolean.class, true);
			if (restrictive) {
				delegate = SimpleEvaluationContext.forPropertyAccessors(new RestrictivePropertyAccessor())
						.withMethodResolvers((context, targetObject, name, argumentTypes) -> null).build();
			}
			else {
				delegate = SimpleEvaluationContext.forReadOnlyDataBinding().build();
			}
		}

		@Override
		public TypedValue getRootObject() {
			return delegate.getRootObject();
		}

		@Override
		public List<PropertyAccessor> getPropertyAccessors() {
			return delegate.getPropertyAccessors();
		}

		@Override
		public List<ConstructorResolver> getConstructorResolvers() {
			return delegate.getConstructorResolvers();
		}

		@Override
		public List<MethodResolver> getMethodResolvers() {
			return delegate.getMethodResolvers();
		}

		@Override
		@Nullable
		public BeanResolver getBeanResolver() {
			return this.beanFactoryResolver;
		}

		@Override
		public TypeLocator getTypeLocator() {
			return delegate.getTypeLocator();
		}

		@Override
		public TypeConverter getTypeConverter() {
			return delegate.getTypeConverter();
		}

		@Override
		public TypeComparator getTypeComparator() {
			return delegate.getTypeComparator();
		}

		@Override
		public OperatorOverloader getOperatorOverloader() {
			return delegate.getOperatorOverloader();
		}

		@Override
		public void setVariable(String name, Object value) {
			delegate.setVariable(name, value);
		}

		@Override
		@Nullable
		public Object lookupVariable(String name) {
			return delegate.lookupVariable(name);
		}

	}

	class RestrictivePropertyAccessor extends ReflectivePropertyAccessor {

		@Override
		public boolean canRead(EvaluationContext context, Object target, String name) {
			return false;
		}

	}

}
