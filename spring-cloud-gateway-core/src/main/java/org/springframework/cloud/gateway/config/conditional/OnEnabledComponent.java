package org.springframework.cloud.gateway.config.conditional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;

public abstract class OnEnabledComponent<T> extends SpringBootCondition implements ConfigurationCondition {

	private static final String PREFIX = "spring.cloud.gateway.";
	private static final String SUFFIX = ".enabled";

	@Override
	public ConfigurationPhase getConfigurationPhase() {
		return ConfigurationPhase.REGISTER_BEAN;
	}

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		List<ConditionMessage> noMatch = new ArrayList<>();
		List<ConditionMessage> match = new ArrayList<>();

		getCandidates(metadata).forEach(it -> {
			ConditionOutcome outcome = determineOutcome(it, context.getEnvironment());
			(outcome.isMatch() ? match : noMatch).add(outcome.getConditionMessage());
		});

		if (!noMatch.isEmpty()) {
			return ConditionOutcome.noMatch(ConditionMessage.of(noMatch));
		}
		return ConditionOutcome.match(ConditionMessage.of(match));
	}

	private List<Class<? extends T>> getCandidates(AnnotatedTypeMetadata metadata) {
		List<Object> candidates = metadata.getAllAnnotationAttributes(ConditionalOnEnabledFilter.class.getName())
				.get("value");
		if (candidates == null) {
			return Collections.emptyList();
		}
		return candidates.stream()
				.flatMap(it -> {
					Class<? extends T>[] array = (Class<? extends T>[]) it;
					return Arrays.stream(array);
				})
				.collect(Collectors.toList());
	}

	private ConditionOutcome determineOutcome(Class<? extends T> componentClass, PropertyResolver resolver) {
		String key = PREFIX + normalizeComponentName(componentClass) + SUFFIX;
		if ("false".equalsIgnoreCase(resolver.getProperty(key))) {
			return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnEnabledFilter.class, componentClass.getName())
					.because("bean is not available"));
		}
		return ConditionOutcome
				.match(ConditionMessage.forCondition(ConditionalOnProperty.class, componentClass.getName()).because("matched"));
	}

	protected abstract String normalizeComponentName(Class<? extends T> filterClass);

}
