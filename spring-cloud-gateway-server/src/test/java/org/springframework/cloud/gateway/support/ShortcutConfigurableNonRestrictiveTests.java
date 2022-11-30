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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.support.ShortcutConfigurable.ShortcutType;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.cloud.gateway.restrictive-property-accessor.enabled=false")
public class ShortcutConfigurableNonRestrictiveTests {

	@Autowired
	BeanFactory beanFactory;

	@Autowired
	ConfigurableEnvironment env;

	private SpelExpressionParser parser;

	@Test
	public void testNormalizeDefaultTypeWithSpelAndPropertyReferenceEnabled() {
		parser = new SpelExpressionParser();
		ShortcutConfigurable shortcutConfigurable = new ShortcutConfigurable() {
			@Override
			public List<String> shortcutFieldOrder() {
				return Arrays.asList("bean", "arg1");
			}
		};
		Map<String, String> args = new HashMap<>();
		args.put("barproperty", "#{@bar.getInt}");
		args.put("arg1", "val1");
		Map<String, Object> map = ShortcutType.DEFAULT.normalize(args, shortcutConfigurable, parser, this.beanFactory);
		assertThat(map).isNotNull().containsEntry("barproperty", 42).containsEntry("arg1", "val1");
	}

	@Test
	public void testNormalizeDefaultTypeWithSpelAndMethodReferenceEnabled() {
		parser = new SpelExpressionParser();
		ShortcutConfigurable shortcutConfigurable = new ShortcutConfigurable() {
			@Override
			public List<String> shortcutFieldOrder() {
				return Arrays.asList("bean", "arg1");
			}
		};
		Map<String, String> args = new HashMap<>();
		args.put("barmethod", "#{@bar.myMethod}");
		args.put("arg1", "val1");
		Map<String, Object> map = ShortcutType.DEFAULT.normalize(args, shortcutConfigurable, parser, this.beanFactory);
		assertThat(map).isNotNull().containsEntry("barmethod", 42).containsEntry("arg1", "val1");
	}

	@SpringBootConfiguration
	protected static class TestConfig {

		@Bean
		public Integer foo() {
			return 42;
		}

		@Bean
		public Bar bar() {
			return new Bar();
		}

	}

	protected static class Bar {

		public int getInt() {
			return 42;
		}

		public int myMethod() {
			return 42;
		}

	}

}
