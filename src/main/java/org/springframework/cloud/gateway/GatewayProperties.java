package org.springframework.cloud.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.gateway")
public class GatewayProperties {


	/**
	 * Map of route names to properties.
	 */
	private Map<String, Route> routes = new LinkedHashMap<>();

	public Map<String, Route> getRoutes() {
		return routes;
	}

	public void setRoutes(Map<String, Route> routes) {
		this.routes = routes;
	}

	public static class Route {
		private String id;
		private String requestPath;
		private URI upstreamUrl;

		public String getRequestPath() {
			return this.requestPath;
		}

		public void setRequestPath(String requestPath) {
			this.requestPath = requestPath;
		}

		public URI getUpstreamUrl() {
			return upstreamUrl;
		}

		public void setUpstreamUrl(URI upstreamUrl) {
			this.upstreamUrl = upstreamUrl;
		}

		@Override
		public String toString() {
			return "Route{" +
					"id='" + id + '\'' +
					", requestPath='" + requestPath + '\'' +
					", upstreamUrl='" + upstreamUrl + '\'' +
					'}';
		}
	}
}
