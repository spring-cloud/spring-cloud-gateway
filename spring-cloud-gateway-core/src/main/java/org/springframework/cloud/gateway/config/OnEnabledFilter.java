package org.springframework.cloud.gateway.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.CaseFormat;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OnEnabledFilter extends SpringBootCondition implements ConfigurationCondition {

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

	private List<Class<? extends GatewayFilterFactory<?>>> getCandidates(AnnotatedTypeMetadata metadata) {
		List<Object> candidates = metadata.getAllAnnotationAttributes(ConditionalOnEnabledFilter.class.getName())
				.get("value");
		if (candidates == null) {
			return Collections.emptyList();
		}
		return candidates.stream()
				.flatMap(it -> {
					Class<? extends GatewayFilterFactory<?>>[] array = (Class<? extends GatewayFilterFactory<?>>[]) it;
					return Arrays.stream(array);
				})
				.collect(Collectors.toList());
	}

	private ConditionOutcome determineOutcome(Class<? extends GatewayFilterFactory<?>> filterClass, PropertyResolver resolver) {
		String filterName = NameUtils.normalizeFilterFactoryName(filterClass);
		String classAsPropertyFormat = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, filterName);
		String key = PREFIX + classAsPropertyFormat + SUFFIX;
		if ("false".equalsIgnoreCase(resolver.getProperty(key))) {
			return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnEnabledFilter.class, filterClass.getName())
					.because("bean is not available"));
		}
		return ConditionOutcome
				.match(ConditionMessage.forCondition(ConditionalOnProperty.class, filterClass.getName()).because("matched"));
	}

}
