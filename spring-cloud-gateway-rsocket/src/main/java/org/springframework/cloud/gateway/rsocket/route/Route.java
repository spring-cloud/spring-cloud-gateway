/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.cloud.gateway.rsocket.server.GatewayExchange;
import org.springframework.cloud.gateway.rsocket.server.GatewayFilter;
import org.springframework.cloud.gateway.rsocket.support.AsyncPredicate;
import org.springframework.cloud.gateway.rsocket.support.Metadata;
import org.springframework.core.Ordered;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.Assert;

/**
 * @author Spencer Gibb
 */
public class Route implements Ordered {

	private final String id;

	private final Metadata targetMetadata;

	private final int order;

	private final AsyncPredicate<GatewayExchange> predicate;

	private final List<GatewayFilter> gatewayFilters;

	public static Builder builder() {
		return new Builder();
	}

	private Route(String id, Metadata targetMetadata, int order,
			AsyncPredicate<GatewayExchange> predicate,
			List<GatewayFilter> gatewayFilters) {
		this.id = id;
		this.targetMetadata = targetMetadata;
		this.order = order;
		this.predicate = predicate;
		this.gatewayFilters = gatewayFilters;
	}

	public String getId() {
		return this.id;
	}

	public Metadata getTargetMetadata() {
		return this.targetMetadata;
	}

	public int getOrder() {
		return order;
	}

	public AsyncPredicate<GatewayExchange> getPredicate() {
		return this.predicate;
	}

	public List<GatewayFilter> getFilters() {
		return Collections.unmodifiableList(this.gatewayFilters);
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
		return Objects.equals(id, route.id)
				&& Objects.equals(targetMetadata, route.targetMetadata)
				&& Objects.equals(order, route.order)
				&& Objects.equals(predicate, route.predicate)
				&& Objects.equals(gatewayFilters, route.gatewayFilters);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, targetMetadata, predicate, gatewayFilters);
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("id", id)
				.append("targetMetadata", targetMetadata).append("order", order)
				.append("predicate", predicate).append("gatewayFilters", gatewayFilters)
				.toString();
	}

	public static class Builder {

		protected String id;

		protected Metadata routingMetadata;

		protected int order = 0;

		protected AsyncPredicate<GatewayExchange> predicate;

		protected List<GatewayFilter> gatewayFilters = new ArrayList<>();

		protected Builder() {
		}

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public String getId() {
			return id;
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public AsyncPredicate<GatewayExchange> getPredicate() {
			return this.predicate;
		}

		public Builder routingMetadata(Metadata routingMetadata) {
			this.routingMetadata = routingMetadata;
			return this;
		}

		public Builder setFilters(List<GatewayFilter> gatewayFilters) {
			this.gatewayFilters = gatewayFilters;
			return this;
		}

		public Builder filter(GatewayFilter gatewayFilter) {
			this.gatewayFilters.add(gatewayFilter);
			return this;
		}

		public Builder filters(Collection<GatewayFilter> gatewayFilters) {
			this.gatewayFilters.addAll(gatewayFilters);
			return this;
		}

		public Builder filters(GatewayFilter... gatewayFilters) {
			return filters(Arrays.asList(gatewayFilters));
		}

		public Builder predicate(AsyncPredicate<GatewayExchange> predicate) {
			this.predicate = predicate;
			return this;
		}

		public Builder and(AsyncPredicate<GatewayExchange> predicate) {
			Assert.notNull(this.predicate, "can not call and() on null predicate");
			this.predicate = this.predicate.and(predicate);
			return this;
		}

		public Builder or(AsyncPredicate<GatewayExchange> predicate) {
			Assert.notNull(this.predicate, "can not call or() on null predicate");
			this.predicate = this.predicate.or(predicate);
			return this;
		}

		public Builder negate() {
			Assert.notNull(this.predicate, "can not call negate() on null predicate");
			this.predicate = this.predicate.negate();
			return this;
		}

		public Route build() {
			Assert.notNull(this.id, "id can not be null");
			Assert.notNull(this.routingMetadata, "targetMetadata can not be null");
			Assert.notNull(this.predicate, "predicate can not be null");

			return new Route(this.id, this.routingMetadata, this.order, predicate,
					this.gatewayFilters);
		}

	}

}
