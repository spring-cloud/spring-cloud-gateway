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

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.springframework.boot.autoconfigure.condition.ConditionMessage.forCondition;

public abstract class OnEnabledComponent<T> extends SpringBootCondition
		implements ConfigurationCondition {

	private static final String PREFIX = "spring.cloud.gateway.";

	private static final String SUFFIX = ".enabled";

	@Override
	public ConfigurationPhase getConfigurationPhase() {
		return ConfigurationPhase.REGISTER_BEAN;
	}

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context,
			AnnotatedTypeMetadata metadata) {
		Class<? extends T> candidate = (Class<? extends T>) metadata
				.getAnnotationAttributes(annotationName()).get("value");
		return determineOutcome(candidate, context.getEnvironment());
	}

	private ConditionOutcome determineOutcome(Class<? extends T> componentClass,
			PropertyResolver resolver) {
		String key = PREFIX + normalizeComponentName(componentClass) + SUFFIX;
		ConditionMessage.Builder messageBuilder = forCondition(annotationName(),
				componentClass.getName());
		if ("false".equalsIgnoreCase(resolver.getProperty(key))) {
			return ConditionOutcome
					.noMatch(messageBuilder.because("bean is not available"));
		}
		return ConditionOutcome.match();
	}

	protected abstract String normalizeComponentName(Class<? extends T> componentClass);

	protected abstract String annotationName();

}
