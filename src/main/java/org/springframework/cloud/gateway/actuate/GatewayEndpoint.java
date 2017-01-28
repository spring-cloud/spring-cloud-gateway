package org.springframework.cloud.gateway.actuate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.cloud.gateway.api.RouteReader;
import org.springframework.cloud.gateway.config.Route;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.route.RouteFilter;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
//TODO: move to new Spring Boot 2.0 actuator when ready
//@ConfigurationProperties(prefix = "endpoints.gateway")
@RestController
@RequestMapping("/admin/gateway")
public class GatewayEndpoint {/*extends AbstractEndpoint<Map<String, Object>> {*/

	private RouteReader routeReader;
	private List<GlobalFilter> globalFilters;
	private List<RouteFilter> routeFilters;
	private FilteringWebHandler filteringWebHandler;

	public GatewayEndpoint(RouteReader routeReader, List<GlobalFilter> globalFilters,
						   List<RouteFilter> routeFilters, FilteringWebHandler filteringWebHandler) {
		//super("gateway");
		this.routeReader = routeReader;
		this.globalFilters = globalFilters;
		this.routeFilters = routeFilters;
		this.filteringWebHandler = filteringWebHandler;
	}

	/*@Override
	public Map<String, Object> invoke() {

	}*/

	@GetMapping("/globalfilters")
	public Map<String, Object> globalfilters() {
		return getNamesToOrders(this.globalFilters);
	}

	@GetMapping("/routefilters")
	public Map<String, Object> routefilers() {
		return getNamesToOrders(this.routeFilters);
	}

	private <T> Map<String, Object> getNamesToOrders(List<T> list) {
		HashMap<String, Object> filters = new HashMap<>();

		for (Object o : list) {
			Integer order = null;
			if (o instanceof Ordered) {
				order = ((Ordered)o).getOrder();
			}
			//filters.put(o.getClass().getName(), order);
			filters.put(o.toString(), order);
		}

		return filters;
	}

	@GetMapping("/routes")
	public Mono<List<Route>> routes() {
		return this.routeReader.getRoutes().collectList();
	}

	@GetMapping("/routes/{id}")
	public Mono<Route> route(@PathVariable String id) {
		return this.routeReader.getRoutes()
				.filter(route -> route.getId().equals(id))
				.singleOrEmpty();
	}

	@GetMapping("/routes/{id}/combinedfilters")
	public Map<String, Object> combinedfilters(@PathVariable String id) {
		Mono<Route> route = this.routeReader.getRoutes()
				.filter(r -> r.getId().equals(id))
				.singleOrEmpty();
		Optional<Route> optional = Optional.ofNullable(route.block()); //TODO: remove block();
		return getNamesToOrders(this.filteringWebHandler.combineFiltersForRoute(optional));
	}
}
