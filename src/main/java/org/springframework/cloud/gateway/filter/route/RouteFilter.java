package org.springframework.cloud.gateway.filter.route;

import org.springframework.util.Assert;
import org.springframework.web.server.WebFilter;

/**
 * @author Spencer Gibb
 */
public interface RouteFilter {

	WebFilter apply(String... args);

	default void validate(int requiredSize, String... args) {
		Assert.isTrue(args != null && args.length == requiredSize,
				"args must have "+ requiredSize +" entry(s)");
	}
}
