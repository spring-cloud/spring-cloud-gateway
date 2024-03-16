/*
 * Copyright 2013-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.server.mvc.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;

import static org.springframework.util.StringUtils.tokenizeToStringArray;

/**
 * @author Spencer Gibb
 */
@Validated
public class RouteProperties {

	/**
	 * The Route ID.
	 */
	private String id;

	/**
	 * List of predicates for matching the Route.
	 */
	@NotEmpty
	@Valid
	private List<PredicateProperties> predicates = new ArrayList<>();

	/**
	 * List of filters to be applied to the Route.
	 */
	@Valid
	private List<FilterProperties> filters = new ArrayList<>();

	/**
	 * The destination URI.
	 */
	@NotNull
	private URI uri;

	/**
	 * Metadata associated with the Route.
	 */
	private Map<String, Object> metadata = new HashMap<>();

	/**
	 * The order of the Route, defaults to zero.
	 */
	private int order = 0;

	public RouteProperties() {
	}

	public RouteProperties(String text) {
		int eqIdx = text.indexOf('=');
		if (eqIdx <= 0) {
			throw new ValidationException(
					"Unable to parse RouteDefinition text '" + text + "'" + ", must be of the form name=value");
		}

		setId(text.substring(0, eqIdx));

		String[] args = tokenizeToStringArray(text.substring(eqIdx + 1), ",");

		setUri(URI.create(args[0]));

		for (int i = 1; i < args.length; i++) {
			this.predicates.add(new PredicateProperties(args[i]));
		}
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<PredicateProperties> getPredicates() {
		return predicates;
	}

	public void setPredicates(List<PredicateProperties> predicates) {
		this.predicates = predicates;
	}

	public List<FilterProperties> getFilters() {
		return filters;
	}

	public void setFilters(List<FilterProperties> filters) {
		this.filters = filters;
	}

	public URI getUri() {
		return uri;
	}

	public void setUri(URI uri) {
		this.uri = uri;
	}

	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		RouteProperties that = (RouteProperties) o;
		return this.order == that.order && Objects.equals(this.id, that.id)
				&& Objects.equals(this.predicates, that.predicates) && Objects.equals(this.filters, that.filters)
				&& Objects.equals(this.uri, that.uri) && Objects.equals(this.metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.predicates, this.filters, this.uri, this.metadata, this.order);
	}

	@Override
	public String toString() {
		return "RouteDefinition{" + "id='" + id + '\'' + ", predicates=" + predicates + ", filters=" + filters
				+ ", uri=" + uri + ", order=" + order + ", metadata=" + metadata + '}';
	}

}
