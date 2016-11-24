package org.springframework.cloud.gateway.config;

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
		private String requestHost;
		private URI downstreamUrl;

		public String getRequestPath() {
			return this.requestPath;
		}

		public void setRequestPath(String requestPath) {
			this.requestPath = requestPath;
		}

		public String getRequestHost() {
			return requestHost;
		}

		public void setRequestHost(String requestHost) {
			this.requestHost = requestHost;
		}

		public URI getDownstreamUrl() {
			return downstreamUrl;
		}

		public void setDownstreamUrl(URI downstreamUrl) {
			this.downstreamUrl = downstreamUrl;
		}

		@Override
		public String toString() {
			return "Route{" +
					"id='" + id + '\'' +
					", requestPath='" + requestPath + '\'' +
					", requestHost='" + requestHost + '\'' +
					", downstreamUrl='" + downstreamUrl + '\'' +
					'}';
		}
	}
}
