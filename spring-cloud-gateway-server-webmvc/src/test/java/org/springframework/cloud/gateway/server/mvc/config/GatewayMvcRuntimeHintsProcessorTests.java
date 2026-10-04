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

package org.springframework.cloud.gateway.server.mvc.config;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.GeneratedClass;
import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.MethodReference;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.test.generate.TestGenerationContext;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions;
import org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.TypeSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;

/**
 * Tests for {@link GatewayMvcRuntimeHintsProcessor}.
 *
 * @author Ryan Baxter
 */
class GatewayMvcRuntimeHintsProcessorTests {

	private final GatewayMvcRuntimeHintsProcessor processor = new GatewayMvcRuntimeHintsProcessor();

	private final TestGenerationContext generationContext = new TestGenerationContext();

	private final BeanFactoryInitializationCode beanFactoryInitializationCode = new MockBeanFactoryInitializationCode(
			generationContext);

	private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

	@Test
	void shouldRegisterReflectionHintsForFiltersAndPredicates() {
		BeanFactoryInitializationAotContribution contribution = processor.processAheadOfTime(beanFactory);
		assertThat(contribution).isNotNull();

		assertThatCode(() -> contribution.applyTo(generationContext, beanFactoryInitializationCode))
			.doesNotThrowAnyException();

		RuntimeHints hints = generationContext.getRuntimeHints();
		assertThat(reflection().onType(FilterFunctions.class)).accepts(hints);
		assertThat(reflection().onType(GatewayRequestPredicates.class)).accepts(hints);
		assertThat(reflection().onType(RouteProperties.class)).accepts(hints);
		assertThat(reflection().onType(FilterProperties.class)).accepts(hints);
		assertThat(reflection().onType(PredicateProperties.class)).accepts(hints);
	}

	@Test
	void shouldRegisterNamedTypes() {
		assertThat(GatewayMvcRuntimeHintsProcessor.isRegistrableType(FilterFunctions.class)).isTrue();
		assertThat(GatewayMvcRuntimeHintsProcessor.isRegistrableType(RouteProperties.class)).isTrue();
		assertThat(GatewayMvcRuntimeHintsProcessor.isRegistrableType(NestedType.class)).isTrue();
	}

	/**
	 * Compiler-generated classes have no canonical name, so {@code TypeReference.of}
	 * rejects them. They can reach the processor because, on JDK 24 and later, class
	 * metadata is read with the JDK Class-File API, which reports classes such as the
	 * enum switch-map holder generated for {@code AfterFilterFunctions} as scan
	 * candidates.
	 */
	@Test
	void shouldNotRegisterSyntheticTypes() {
		Supplier<String> lambda = () -> "lambda";
		assertThat(lambda.getClass().isSynthetic()).isTrue();
		assertThat(GatewayMvcRuntimeHintsProcessor.isRegistrableType(lambda.getClass())).isFalse();
	}

	@Test
	void shouldNotRegisterAnonymousTypes() {
		Object anonymous = new Object() {
		};
		assertThat(anonymous.getClass().getCanonicalName()).isNull();
		assertThat(GatewayMvcRuntimeHintsProcessor.isRegistrableType(anonymous.getClass())).isFalse();
	}

	@Test
	void shouldNotRegisterLocalTypes() {
		class Local {

		}
		assertThat(Local.class.getCanonicalName()).isNull();
		assertThat(GatewayMvcRuntimeHintsProcessor.isRegistrableType(Local.class)).isFalse();
	}

	static class NestedType {

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
		public ClassName getClassName() {
			return ClassName.get(MockBeanFactoryInitializationCode.class);
		}

		@Override
		public void addInitializer(MethodReference methodReference) {
			new ArrayList<>();
		}

	}

}
