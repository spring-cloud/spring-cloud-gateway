package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.util.Assert;

/**
 * @author Spencer Gibb
 */
public interface FilterFactory {

	default String getName() {
		return getClass().getSimpleName().replace(FilterFactory.class.getSimpleName(), "");
	}

	GatewayFilter apply(String value, String[] args);

	default void validate(String[] args, int requiredSize) {
		Assert.isTrue(args != null && args.length == requiredSize,
				"args must have "+ requiredSize +" entry(s)");
	}
}
