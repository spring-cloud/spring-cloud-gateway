package org.springframework.cloud.gateway.actuate;

import java.util.List;

import org.springframework.cloud.gateway.api.RouteReader;
import org.springframework.cloud.gateway.config.Route;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Spencer Gibb
 */
//TODO: move to new Spring Boot 2.0 actuator when ready
//@ConfigurationProperties(prefix = "endpoints.gateway")
@RestController
@RequestMapping("/admin/gateway")
public class GatewayEndpoint {
	private RouteReader routeReader;/*extends AbstractEndpoint<Map<String, Object>> {*/

	public GatewayEndpoint(RouteReader routeReader) {
		//super("gateway");
		this.routeReader = routeReader;
	}

	/*@Override
	public Map<String, Object> invoke() {
		HashMap<String, Object> map = new HashMap<>();

		ArrayList<String> filterNames = new ArrayList<>();
		for (GlobalFilter filter : this.filters) {
			filterNames.add(filter.getClass().getName());
		}
		map.put("filters", filterNames);

		return map;
	}*/

	@GetMapping("/routes")
	public List<Route> routes() {
		return this.routeReader.getRoutes();
	}
}
