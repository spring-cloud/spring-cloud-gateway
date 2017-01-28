package org.springframework.cloud.gateway.discovery;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.api.RouteReader;
import org.springframework.cloud.gateway.config.FilterDefinition;
import org.springframework.cloud.gateway.config.PredicateDefinition;
import org.springframework.cloud.gateway.config.Route;

import reactor.core.publisher.Flux;

import java.net.URI;

/**
 * TODO: developer configuration, in zuul, this was opt out, should be opt in
 * @author Spencer Gibb
 */
public class DiscoveryClientRouteReader implements RouteReader {

	private final DiscoveryClient discoveryClient;
	private final String routeIdPrefix;

	public DiscoveryClientRouteReader(DiscoveryClient discoveryClient) {
		this.discoveryClient = discoveryClient;
		this.routeIdPrefix = this.discoveryClient.getClass().getSimpleName() + "_";
	}

	@Override
	public Flux<Route> getRoutes() {
		return Flux.fromIterable(discoveryClient.getServices())
				.map(serviceId -> {
					Route route = new Route();
					route.setId(this.routeIdPrefix + serviceId);
					route.setUri(URI.create("lb://" + serviceId));

					// add a predicate that matches the url at /serviceId/**
					PredicateDefinition predicate = new PredicateDefinition();
					predicate.setName("Url");
					predicate.setValue("/" + serviceId + "/**");
					route.getPredicates().add(predicate);

					//TODO: support for other default predicates

					// add a filter that removes /serviceId by default
					FilterDefinition filter = new FilterDefinition();
					filter.setName("RewritePath");
					String regex = "/" + serviceId + "/(?<remaining>.*)";
					String replacement = "/${remaining}";
					filter.setArgs(regex, replacement);
					route.getFilters().add(filter);

					//TODO: support for default filters

					return route;
				});
	}
}
