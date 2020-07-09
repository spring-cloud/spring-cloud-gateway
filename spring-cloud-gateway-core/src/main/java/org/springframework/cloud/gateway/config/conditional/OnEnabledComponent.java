package org.springframework.cloud.gateway.config.conditional;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.springframework.boot.autoconfigure.condition.ConditionMessage.forCondition;

public abstract class OnEnabledComponent<T> extends SpringBootCondition implements ConfigurationCondition {

	private static final String PREFIX = "spring.cloud.gateway.";
	private static final String SUFFIX = ".enabled";

	@Override
	public ConfigurationPhase getConfigurationPhase() {
		return ConfigurationPhase.REGISTER_BEAN;
	}

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		Class<? extends T> candidate = (Class<? extends T>) metadata.getAnnotationAttributes(annotationName())
				.get("value");
		return determineOutcome(candidate, context.getEnvironment());
	}

	private ConditionOutcome determineOutcome(Class<? extends T> componentClass, PropertyResolver resolver) {
		String key = PREFIX + normalizeComponentName(componentClass) + SUFFIX;
		ConditionMessage.Builder messageBuilder = forCondition(annotationName(), componentClass.getName());
		if ("false".equalsIgnoreCase(resolver.getProperty(key))) {
			return ConditionOutcome.noMatch(messageBuilder.because("bean is not available"));
		}
		return ConditionOutcome.match();
	}

	protected abstract String normalizeComponentName(Class<? extends T> componentClass);

	protected abstract String annotationName();

}
