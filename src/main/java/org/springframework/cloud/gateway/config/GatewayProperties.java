package org.springframework.cloud.gateway.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.gateway")
public class GatewayProperties {

	/**
	 * Map of route names to properties.
	 */
	@NotNull
	@Valid
	private List<Route> routes = new ArrayList<>();

	public List<Route> getRoutes() {
		return routes;
	}

	public void setRoutes(List<Route> routes) {
		this.routes = routes;
	}

	public static class Route {
		@NotEmpty
		private String id;

		@NotEmpty
		@Valid
		private List<Predicate> predicates = new ArrayList<>();

		@NotNull
		private URI downstreamUrl;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public List<Predicate> getPredicates() {
			return predicates;
		}

		public void setPredicates(List<Predicate> predicates) {
			this.predicates = predicates;
		}

		public URI getDownstreamUrl() {
			return downstreamUrl;
		}

		public void setDownstreamUrl(URI downstreamUrl) {
			this.downstreamUrl = downstreamUrl;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Route route = (Route) o;
			return Objects.equals(id, route.id) &&
					Objects.equals(predicates, route.predicates) &&
					Objects.equals(downstreamUrl, route.downstreamUrl);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, predicates, downstreamUrl);
		}

		@Override
		public String toString() {
			return "Route{" +
					"id='" + id + '\'' +
					", predicates=" + predicates +
					", downstreamUrl=" + downstreamUrl +
					'}';
		}
	}

	public static class Predicate {
		@NotNull
		private String name;
		@NotNull
		private String value;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Predicate predicate = (Predicate) o;
			return Objects.equals(name, predicate.name) &&
					Objects.equals(value, predicate.value);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, value);
		}

		@Override
		public String toString() {
			return "Predicate{" +
					"name='" + name + '\'' +
					", value='" + value + '\'' +
					'}';
		}
	}
}
