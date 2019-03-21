/*
 * Copyright 2013-2017 the original author or authors.
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

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Spencer Gibb
 */
public interface ShortcutConfigurable {

	enum ShortcutType {
		DEFAULT {
			@Override
			public Map<String, Object> normalize(Map<String, String> args, ShortcutConfigurable shortcutConf, SpelExpressionParser parser, BeanFactory beanFactory) {
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

		GATHER_LIST {
			@Override
			public Map<String, Object> normalize(Map<String, String> args, ShortcutConfigurable shortcutConf, SpelExpressionParser parser, BeanFactory beanFactory) {
				Map<String, Object> map = new HashMap<>();
				// field order should be of size 1
				List<String> fieldOrder = shortcutConf.shortcutFieldOrder();
				Assert.isTrue(fieldOrder != null
								&& fieldOrder.size() == 1,
						"Shortcut Configuration Type GATHER_LIST must have shortcutFieldOrder of size 1");
				String fieldName = fieldOrder.get(0);
				map.put(fieldName, args.values().stream()
						.map(value -> getValue(parser, beanFactory, value))
						.collect(Collectors.toList()));
				return map;
			}
		};

		public abstract Map<String, Object> normalize(Map<String, String> args, ShortcutConfigurable shortcutConf,
													  SpelExpressionParser parser, BeanFactory beanFactory);
	}

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
			StandardEvaluationContext context = new StandardEvaluationContext();
			context.setBeanResolver(new BeanFactoryResolver(beanFactory));
			Expression expression = parser.parseExpression(entryValue, new TemplateParserContext());
			value = expression.getValue(context);
		} else {
			value = entryValue;
		}
		return value;
	}

	default ShortcutType shortcutType() {
		return ShortcutType.DEFAULT;
	}

	/**
	 * Returns hints about the number of args and the order for shortcut parsing.
	 * @return
	 */
	default List<String> shortcutFieldOrder() {
		return Collections.emptyList();
	}

	default String shortcutFieldPrefix() {
		return "";
	}

}
