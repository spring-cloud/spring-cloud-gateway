/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.config.conditional;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnEnabledComponentTests {

	private OnEnabledComponent<Object> onEnabledComponent;

	private MockEnvironment environment;

	private ConditionContext conditionContext;

	@BeforeEach
	void setUp() {
		this.onEnabledComponent = createOnEnabledComponent("test-class");
		this.environment = new MockEnvironment();
		this.conditionContext = mock(ConditionContext.class);
	}

	@Test
	public void shouldMatchComponent() {
		when(conditionContext.getEnvironment()).thenReturn(environment);

		ConditionOutcome outcome = onEnabledComponent.getMatchOutcome(conditionContext,
				mockMetaData(EnabledComponent.class));

		assertThat(outcome.isMatch()).isTrue();
	}

	@Test
	public void shouldNotMatchDisabledComponent() {
		String componentName = "disabled-component";
		this.onEnabledComponent = createOnEnabledComponent(componentName);
		when(conditionContext.getEnvironment()).thenReturn(environment);
		environment.setProperty("spring.cloud.gateway." + componentName + ".enabled", "false");

		ConditionOutcome outcome = onEnabledComponent.getMatchOutcome(conditionContext,
				mockMetaData(DisabledComponent.class));

		assertThat(outcome.isMatch()).isFalse();
		assertThat(outcome.getMessage()).contains("DisabledComponent").contains("bean is not available");
	}

	private AnnotatedTypeMetadata mockMetaData(Class<?> value) {
		AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);
		when(metadata.getAnnotationAttributes(eq(ConditionalOnEnabledFilter.class.getName())))
				.thenReturn(Collections.singletonMap("value", value));
		return metadata;
	}

	private OnEnabledComponent<Object> createOnEnabledComponent(String componentName) {
		return new OnEnabledComponent<Object>() {
			@Override
			protected String normalizeComponentName(Class<?> componentClass) {
				return componentName;
			}

			@Override
			protected Class<?> annotationClass() {
				return ConditionalOnEnabledFilter.class;
			}

			@Override
			protected Class<?> defaultValueClass() {
				return Object.class;
			}
		};
	}

	protected static class EnabledComponent {

	}

	protected static class DisabledComponent {

	}

}
