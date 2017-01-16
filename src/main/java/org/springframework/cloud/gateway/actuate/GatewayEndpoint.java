package org.springframework.cloud.gateway.actuate;

import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties(prefix = "endpoints.gateway")
public class GatewayEndpoint extends AbstractEndpoint<Map<String, Object>> {
	private List<GlobalFilter> filters;

	public GatewayEndpoint(List<GlobalFilter> filters) {
		super("gateway");
		this.filters = filters;
		AnnotationAwareOrderComparator.sort(this.filters);
	}

	@Override
	public Map<String, Object> invoke() {
		HashMap<String, Object> map = new HashMap<>();

		ArrayList<String> filterNames = new ArrayList<>();
		for (GlobalFilter filter : this.filters) {
			filterNames.add(filter.getClass().getName());
		}
		map.put("filters", filterNames);

		return map;
	}
}
