package org.springframework.cloud.gateway.config;

import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Spencer Gibb
 */
public class Route {
	@NotEmpty
	private String id;

	@NotEmpty
	@Valid
	private List<PredicateDefinition> predicates = new ArrayList<>();

	@NotNull
	private URI downstreamUrl;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<PredicateDefinition> getPredicates() {
		return predicates;
	}

	public void setPredicates(List<PredicateDefinition> predicates) {
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
