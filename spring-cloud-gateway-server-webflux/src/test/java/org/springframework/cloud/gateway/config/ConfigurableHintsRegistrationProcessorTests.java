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

package org.springframework.cloud.gateway.config;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.GeneratedClass;
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.MethodReference;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.test.generate.TestGenerationContext;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerResilience4JFilterFactory;
import org.springframework.javapoet.TypeSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

/**
 * Tests for {@link ConfigurableHintsRegistrationProcessor}.
 *
 * @author Olga Maciaszek-Sharma
 */
class ConfigurableHintsRegistrationProcessorTests {

	private final ConfigurableHintsRegistrationProcessor processor = new ConfigurableHintsRegistrationProcessor();

	private final TestGenerationContext generationContext = new TestGenerationContext();

	private final BeanFactoryInitializationCode beanFactoryInitializationCode = new MockBeanFactoryInitializationCode(
			generationContext);

	private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

	@Test
	void shouldRegisterReflectionHintsForTypeAndSuperTypesAndGenerics() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
			.rootBeanDefinition(SpringCloudCircuitBreakerResilience4JFilterFactory.class)
			.getBeanDefinition();
		beanFactory.registerBeanDefinition("test", beanDefinition);

		BeanFactoryInitializationAotContribution contribution = processor.processAheadOfTime(beanFactory);
		assertThat(contribution).isNotNull();
		contribution.applyTo(generationContext, beanFactoryInitializationCode);

		RuntimeHints hints = generationContext.getRuntimeHints();
		assertThat(reflection().onType(SpringCloudCircuitBreakerResilience4JFilterFactory.class)).accepts(hints);
		assertThat(reflection().onType(SpringCloudCircuitBreakerFilterFactory.class)).accepts(hints);
		assertThat(reflection().onType(SpringCloudCircuitBreakerFilterFactory.Config.class)).accepts(hints);
	}

	@SuppressWarnings("NullableProblems")
	static class MockBeanFactoryInitializationCode implements BeanFactoryInitializationCode {

		private static final Consumer<TypeSpec.Builder> emptyTypeCustomizer = type -> {
		};

		private final GeneratedClass generatedClass;

		MockBeanFactoryInitializationCode(GenerationContext generationContext) {
			generatedClass = generationContext.getGeneratedClasses().addForFeature("Test", emptyTypeCustomizer);
		}

		@Override
		public GeneratedMethods getMethods() {
			return generatedClass.getMethods();
		}

		@Override
		public void addInitializer(MethodReference methodReference) {
			new ArrayList<>();
		}

	}

}
