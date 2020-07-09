package org.springframework.cloud.gateway.config.conditional;

import com.google.common.base.CaseFormat;

import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.NameUtils;

public class OnEnabledPredicate extends OnEnabledComponent<RoutePredicateFactory<?>> {

	@Override
	protected String normalizeComponentName(Class<? extends RoutePredicateFactory<?>> predicateClass) {
		String filterName = NameUtils.normalizeRoutePredicateName(predicateClass);
		return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, filterName);
	}

	@Override
	protected String annotationName() {
		return ConditionalOnEnabledPredicate.class.getName();
	}

}
