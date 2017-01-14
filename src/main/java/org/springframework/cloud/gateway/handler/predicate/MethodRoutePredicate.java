package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.springframework.http.HttpMethod;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class MethodRoutePredicate implements RoutePredicate {

	@Override
	public Predicate<ServerWebExchange> apply(String method, String[] args) {
		//TODO: caching can happen here
		return exchange -> {
			HttpMethod requestMethod = exchange.getRequest().getMethod();
			return requestMethod.matches(method);
		};
	}
}
