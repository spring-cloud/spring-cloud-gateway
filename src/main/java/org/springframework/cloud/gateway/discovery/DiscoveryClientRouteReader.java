package org.springframework.cloud.gateway.discovery;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.api.RouteReader;
import org.springframework.cloud.gateway.config.FilterDefinition;
import org.springframework.cloud.gateway.config.PredicateDefinition;
import org.springframework.cloud.gateway.config.Route;

import reactor.core.publisher.Flux;

import java.net.URI;

/**
 * @author Spencer Gibb
 */
public class DiscoveryClientRouteReader implements RouteReader {

	private final DiscoveryClient discoveryClient;

	public DiscoveryClientRouteReader(DiscoveryClient discoveryClient) {
		this.discoveryClient = discoveryClient;
	}

	@Override
	public Flux<Route> getRoutes() {
		return Flux.fromIterable(discoveryClient.getServices())
				.map(serviceId -> {
					Route route = new Route();
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
					filter.setValue("/" + serviceId + "/(?<remaining>.*)");
					filter.setArgs(new String[]{ "/${remaining}" });
					route.getFilters().add(filter);

					//TODO: support for default filters

					return route;
				});
	}
}
