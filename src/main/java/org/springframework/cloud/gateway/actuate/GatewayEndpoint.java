package org.springframework.cloud.gateway.actuate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.gateway.api.RouteReader;
import org.springframework.cloud.gateway.config.Route;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

	public GatewayEndpoint(RouteReader routeReader, List<GlobalFilter> globalFilters) {
		//super("gateway");
		this.routeReader = routeReader;
		this.globalFilters = globalFilters;
	}

	/*@Override
	public Map<String, Object> invoke() {

	}*/

	@GetMapping("/globalfilters")
	public Map<String, Object> globalfilters() {
		HashMap<String, Object> filters = new HashMap<>();

		for (GlobalFilter filter : this.globalFilters) {
			Integer order = null;
			if (filter instanceof Ordered) {
				order = ((Ordered)filter).getOrder();
			}
			filters.put(filter.getClass().getName(), order);
		}

		return filters;
	}

	@GetMapping("/routes")
	public List<Route> routes() {
		return this.routeReader.getRoutes();
	}

	@GetMapping("/routes/{id}")
	public Route routes(@PathVariable String id) {
		return this.routeReader.getRoutes().stream()
				.filter(route -> route.getId().equals(id))
				.findFirst().get();
	}

	//TODO: add combined routes for a filter
}
