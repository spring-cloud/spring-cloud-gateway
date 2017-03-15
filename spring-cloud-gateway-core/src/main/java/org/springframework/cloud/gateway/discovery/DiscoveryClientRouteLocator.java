/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.discovery;

import java.net.URI;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.api.RouteLocator;
import org.springframework.cloud.gateway.filter.factory.RewritePathWebFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRequestPredicateFactory;
import org.springframework.cloud.gateway.model.FilterDefinition;
import org.springframework.cloud.gateway.model.PredicateDefinition;
import org.springframework.cloud.gateway.model.RouteDefinition;

import static org.springframework.cloud.gateway.filter.factory.RewritePathWebFilterFactory.REGEXP_KEY;
import static org.springframework.cloud.gateway.filter.factory.RewritePathWebFilterFactory.REPLACEMENT_KEY;
import static org.springframework.cloud.gateway.handler.predicate.PathRequestPredicateFactory.PATTERN_KEY;
import static org.springframework.cloud.gateway.support.NameUtils.normalizeFilterName;
import static org.springframework.cloud.gateway.support.NameUtils.normalizePredicateName;

import reactor.core.publisher.Flux;

/**
 * TODO: developer configuration, in zuul, this was opt out, should be opt in
 * @author Spencer Gibb
 */
public class DiscoveryClientRouteLocator implements RouteLocator {

	private final DiscoveryClient discoveryClient;
	private final String routeIdPrefix;

	public DiscoveryClientRouteLocator(DiscoveryClient discoveryClient) {
		this.discoveryClient = discoveryClient;
		this.routeIdPrefix = this.discoveryClient.getClass().getSimpleName() + "_";
	}

	@Override
	public Flux<RouteDefinition> getRoutes() {
		return Flux.fromIterable(discoveryClient.getServices())
				.map(serviceId -> {
					RouteDefinition routeDefinition = new RouteDefinition();
					routeDefinition.setId(this.routeIdPrefix + serviceId);
					routeDefinition.setUri(URI.create("lb://" + serviceId));

					// add a predicate that matches the url at /serviceId/**
					PredicateDefinition predicate = new PredicateDefinition();
					predicate.setName(normalizePredicateName(PathRequestPredicateFactory.class));
					predicate.addArg(PATTERN_KEY, "/" + serviceId + "/**");
					routeDefinition.getPredicates().add(predicate);

					//TODO: support for other default predicates

					// add a filter that removes /serviceId by default
					FilterDefinition filter = new FilterDefinition();
					filter.setName(normalizeFilterName(RewritePathWebFilterFactory.class));
					String regex = "/" + serviceId + "/(?<remaining>.*)";
					String replacement = "/${remaining}";
					filter.addArg(REGEXP_KEY, regex);
					filter.addArg(REPLACEMENT_KEY, replacement);
					routeDefinition.getFilters().add(filter);

					//TODO: support for default filters

					return routeDefinition;
				});
	}
}
