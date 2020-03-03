/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.route;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.toAsyncPredicate;

/**
 * @author Spencer Gibb
 */
public class Route implements Ordered {

	private final String id;

	private final URI uri;

	private final int order;

	private final AsyncPredicate<ServerWebExchange> predicate;

	private final List<GatewayFilter> gatewayFilters;

	private final Map<String, Object> metadata;

	private Route(String id, URI uri, int order,
			AsyncPredicate<ServerWebExchange> predicate,
			List<GatewayFilter> gatewayFilters, Map<String, Object> metadata) {
		this.id = id;
		this.uri = uri;
		this.order = order;
		this.predicate = predicate;
		this.gatewayFilters = gatewayFilters;
		this.metadata = metadata;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(RouteDefinition routeDefinition) {
		// @formatter:off
		return new Builder().id(routeDefinition.getId())
				.uri(routeDefinition.getUri())
				.order(routeDefinition.getOrder())
				.metadata(routeDefinition.getMetadata());
		// @formatter:on
	}

	public static AsyncBuilder async() {
		return new AsyncBuilder();
	}

	public static AsyncBuilder async(RouteDefinition routeDefinition) {
		// @formatter:off
		return new AsyncBuilder().id(routeDefinition.getId())
				.uri(routeDefinition.getUri())
				.order(routeDefinition.getOrder())
				.metadata(routeDefinition.getMetadata());
		// @formatter:on
	}

	public String getId() {
		return this.id;
	}

	public URI getUri() {
		return this.uri;
	}

	public int getOrder() {
		return order;
	}

	public AsyncPredicate<ServerWebExchange> getPredicate() {
		return this.predicate;
	}

	public List<GatewayFilter> getFilters() {
		return Collections.unmodifiableList(this.gatewayFilters);
	}

	public Map<String, Object> getMetadata() {
		return Collections.unmodifiableMap(metadata);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Route route = (Route) o;
		return this.order == route.order && Objects.equals(this.id, route.id)
				&& Objects.equals(this.uri, route.uri)
				&& Objects.equals(this.predicate, route.predicate)
				&& Objects.equals(this.gatewayFilters, route.gatewayFilters)
				&& Objects.equals(this.metadata, route.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.uri, this.order, this.predicate,
				this.gatewayFilters, this.metadata);
	}

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("Route{");
		sb.append("id='").append(id).append('\'');
		sb.append(", uri=").append(uri);
		sb.append(", order=").append(order);
		sb.append(", predicate=").append(predicate);
		sb.append(", gatewayFilters=").append(gatewayFilters);
		sb.append(", metadata=").append(metadata);
		sb.append('}');
		return sb.toString();
	}

	public abstract static class AbstractBuilder<B extends AbstractBuilder<B>> {

		protected String id;

		protected URI uri;

		protected int order = 0;

		protected List<GatewayFilter> gatewayFilters = new ArrayList<>();

		protected Map<String, Object> metadata = new HashMap<>();

		protected AbstractBuilder() {
		}

		protected abstract B getThis();

		public B id(String id) {
			this.id = id;
			return getThis();
		}

		public String getId() {
			return id;
		}

		public B order(int order) {
			this.order = order;
			return getThis();
		}

		public B uri(String uri) {
			return uri(URI.create(uri));
		}

		public B uri(URI uri) {
			this.uri = uri;
			String scheme = this.uri.getScheme();
			Assert.hasText(scheme, "The parameter [" + this.uri
					+ "] format is incorrect, scheme can not be empty");
			if (this.uri.getPort() < 0 && scheme.startsWith("http")) {
				// default known http ports
				int port = this.uri.getScheme().equals("https") ? 443 : 80;
				this.uri = UriComponentsBuilder.fromUri(this.uri).port(port).build(false)
						.toUri();
			}
			return getThis();
		}

		public B replaceMetadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return getThis();
		}

		public B metadata(Map<String, Object> metadata) {
			this.metadata.putAll(metadata);
			return getThis();
		}

		public B metadata(String key, Object value) {
			this.metadata.put(key, value);
			return getThis();
		}

		public abstract AsyncPredicate<ServerWebExchange> getPredicate();

		public B replaceFilters(List<GatewayFilter> gatewayFilters) {
			this.gatewayFilters = gatewayFilters;
			return getThis();
		}

		public B filter(GatewayFilter gatewayFilter) {
			this.gatewayFilters.add(gatewayFilter);
			return getThis();
		}

		public B filters(Collection<GatewayFilter> gatewayFilters) {
			this.gatewayFilters.addAll(gatewayFilters);
			return getThis();
		}

		public B filters(GatewayFilter... gatewayFilters) {
			return filters(Arrays.asList(gatewayFilters));
		}

		public Route build() {
			Assert.notNull(this.id, "id can not be null");
			Assert.notNull(this.uri, "uri can not be null");
			AsyncPredicate<ServerWebExchange> predicate = getPredicate();
			Assert.notNull(predicate, "predicate can not be null");

			return new Route(this.id, this.uri, this.order, predicate,
					this.gatewayFilters, this.metadata);
		}

	}

	public static class AsyncBuilder extends AbstractBuilder<AsyncBuilder> {

		protected AsyncPredicate<ServerWebExchange> predicate;

		@Override
		protected AsyncBuilder getThis() {
			return this;
		}

		@Override
		public AsyncPredicate<ServerWebExchange> getPredicate() {
			return this.predicate;
		}

		public AsyncBuilder predicate(Predicate<ServerWebExchange> predicate) {
			return asyncPredicate(toAsyncPredicate(predicate));
		}

		public AsyncBuilder asyncPredicate(AsyncPredicate<ServerWebExchange> predicate) {
			this.predicate = predicate;
			return this;
		}

		public AsyncBuilder and(AsyncPredicate<ServerWebExchange> predicate) {
			Assert.notNull(this.predicate, "can not call and() on null predicate");
			this.predicate = this.predicate.and(predicate);
			return this;
		}

		public AsyncBuilder or(AsyncPredicate<ServerWebExchange> predicate) {
			Assert.notNull(this.predicate, "can not call or() on null predicate");
			this.predicate = this.predicate.or(predicate);
			return this;
		}

		public AsyncBuilder negate() {
			Assert.notNull(this.predicate, "can not call negate() on null predicate");
			this.predicate = this.predicate.negate();
			return this;
		}

	}

	public static class Builder extends AbstractBuilder<Builder> {

		protected Predicate<ServerWebExchange> predicate;

		@Override
		protected Builder getThis() {
			return this;
		}

		@Override
		public AsyncPredicate<ServerWebExchange> getPredicate() {
			return ServerWebExchangeUtils.toAsyncPredicate(this.predicate);
		}

		public Builder and(Predicate<ServerWebExchange> predicate) {
			Assert.notNull(this.predicate, "can not call and() on null predicate");
			this.predicate = this.predicate.and(predicate);
			return this;
		}

		public Builder or(Predicate<ServerWebExchange> predicate) {
			Assert.notNull(this.predicate, "can not call or() on null predicate");
			this.predicate = this.predicate.or(predicate);
			return this;
		}

		public Builder negate() {
			Assert.notNull(this.predicate, "can not call negate() on null predicate");
			this.predicate = this.predicate.negate();
			return this;
		}

	}

}
