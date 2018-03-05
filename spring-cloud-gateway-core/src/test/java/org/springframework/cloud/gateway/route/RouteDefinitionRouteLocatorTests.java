package org.springframework.cloud.gateway.route;

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
import org.springframework.cloud.gateway.support.ShortcutConfigurable;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.tuple.Tuple;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
public class RouteDefinitionRouteLocatorTests {

	private SpelExpressionParser parser;

	@Autowired
	BeanFactory beanFactory;

	@Test
	public void testGetTupleWithSpel() {
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

		Tuple tuple = RouteDefinitionRouteLocator.getTuple(shortcutConfigurable, args, parser, this.beanFactory);
		assertThat(tuple).isNotNull();
		assertThat(tuple.getValue("bean", Integer.class)).isEqualTo(42);
		assertThat(tuple.getString("arg1")).isEqualTo("val1");
	}

	@SpringBootConfiguration
	protected static class TestConfig {
		@Bean
		public Integer foo() {
			return 42;
		}
	}
}
