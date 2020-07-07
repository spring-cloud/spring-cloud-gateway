package org.springframework.cloud.gateway.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.context.annotation.Conditional;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Documented
@Conditional(OnEnabledFilter.class)
public @interface ConditionalOnEnabledFilter {

	/**
	 * The classes components to check for.
	 * @return the classes that must be present
	 */
	Class<? extends GatewayFilterFactory<?>>[] value() default {};
}
