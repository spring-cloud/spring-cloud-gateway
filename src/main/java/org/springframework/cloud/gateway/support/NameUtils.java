package org.springframework.cloud.gateway.support;

import org.springframework.cloud.gateway.filter.route.RouteFilter;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicate;

/**
 * @author Spencer Gibb
 */
public class NameUtils {

	public static String normalizePredicateName(Class<? extends RoutePredicate> clazz) {
		return clazz.getSimpleName().replace(RoutePredicate.class.getSimpleName(), "");
	}

	public static String normalizeFilterName(Class<? extends RouteFilter> clazz) {
		return clazz.getSimpleName().replace(RouteFilter.class.getSimpleName(), "");
	}
}
