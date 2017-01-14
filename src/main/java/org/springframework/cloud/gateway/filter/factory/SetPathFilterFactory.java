package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;
import java.util.Map;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.util.UriTemplate;

import static org.springframework.cloud.gateway.filter.GatewayFilter.getAttribute;
import static org.springframework.cloud.gateway.handler.predicate.UrlPredicateFactory.URL_PREDICATE_VARS_ATTR;

/**
 * @author Spencer Gibb
 */
public class SetPathFilterFactory implements FilterFactory {

	@Override
	@SuppressWarnings("unchecked")
	public GatewayFilter apply(String template, String[] args) {
		UriTemplate uriTemplate = new UriTemplate(template);

		//TODO: caching can happen here
		return (exchange, chain) -> {
			Map<String, String> variables = getAttribute(exchange, URL_PREDICATE_VARS_ATTR, Map.class);
			ServerHttpRequest req = exchange.getRequest();
			URI uri = uriTemplate.expand(variables);
			String newPath = uri.getPath();

			ServerHttpRequest request = req.mutate()
					.path(newPath)
					.build();

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}
