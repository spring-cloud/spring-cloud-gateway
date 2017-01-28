package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public interface RoutePredicate {

	Predicate<ServerWebExchange> apply(String... args);

	default void validate(int minimumSize, String... args) {
		Assert.isTrue(args != null && args.length >= minimumSize,
				"args must have at least "+ minimumSize +" entry(s)");
	}
}
