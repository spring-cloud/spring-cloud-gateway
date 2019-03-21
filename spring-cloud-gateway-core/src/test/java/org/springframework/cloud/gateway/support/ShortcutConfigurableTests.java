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

package org.springframework.cloud.gateway.support;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.support.ShortcutConfigurable.ShortcutType;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ShortcutConfigurableTests {

	private SpelExpressionParser parser;

	@Autowired
	BeanFactory beanFactory;

	@Test
	public void testNormalizeDefaultTypeWithSpel() {
		parser = new SpelExpressionParser();
		ShortcutConfigurable shortcutConfigurable = new ShortcutConfigurable() {
			@Override
			public List<String> shortcutFieldOrder() {
				return Arrays.asList("bean", "arg1");
			}
		};
		Map<String, String> args = new HashMap<>();
		args.put("bean", "#{@foo}");
		args.put("arg1", "val1");
		Map<String, Object> map = ShortcutType.DEFAULT.normalize(args, shortcutConfigurable, parser, this.beanFactory);
		assertThat(map).isNotNull()
				.containsEntry("bean", 42)
				.containsEntry("arg1", "val1");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testNormalizeGatherListTypeWithSpel() {
		parser = new SpelExpressionParser();
		ShortcutConfigurable shortcutConfigurable = new ShortcutConfigurable() {
			@Override
			public List<String> shortcutFieldOrder() {
				return Arrays.asList("values");
			}

			@Override
			public ShortcutType shortcutType() {
				return ShortcutType.GATHER_LIST;
			}
		};
		Map<String, String> args = new HashMap<>();
		args.put("1", "#{@foo}");
		args.put("2", "val1");
		args.put("3", "val2");
		Map<String, Object> map = ShortcutType.GATHER_LIST.normalize(args, shortcutConfigurable, parser, this.beanFactory);
		assertThat(map).isNotNull().containsKey("values");
		assertThat((List)map.get("values"))
				.containsExactly(42, "val1", "val2");
	}

	@SpringBootConfiguration
	protected static class TestConfig {
		@Bean
		public Integer foo() {
			return 42;
		}
	}
}
