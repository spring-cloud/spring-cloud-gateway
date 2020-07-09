package org.springframework.cloud.gateway.config.conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.context.annotation.Conditional;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Documented
@Conditional(OnEnabledPredicate.class)
public @interface ConditionalOnEnabledPredicate {

	/**
	 * The class components to check for.
	 * @return the class that must be enabled
	 */
	Class<? extends RoutePredicateFactory<?>> value();
}
