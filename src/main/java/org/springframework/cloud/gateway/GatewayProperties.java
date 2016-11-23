package org.springframework.cloud.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
		private String path;
		private String host;
		private String port;

		public String getPath() {
			return this.path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public String getHost() {
			return this.host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public String getPort() {
			return port;
		}

		public void setPort(String port) {
			this.port = port;
		}

		@Override
		public String toString() {
			return "Route{" +
					"id='" + id + '\'' +
					", path='" + path + '\'' +
					", host='" + host + '\'' +
					", port='" + port + '\'' +
					'}';
		}
	}
}
