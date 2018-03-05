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
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REGEXP_KEY;
import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REPLACEMENT_KEY;
import static org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory.PATTERN_KEY;
import static org.springframework.cloud.gateway.support.NameUtils.normalizeFilterFactoryName;
import static org.springframework.cloud.gateway.support.NameUtils.normalizeRoutePredicateName;

import reactor.core.publisher.Flux;

/**
 * TODO: developer configuration, in zuul, this was opt out, should be opt in
 * TODO: change to RouteLocator? use java dsl
 * @author Spencer Gibb
 */
public class DiscoveryClientRouteDefinitionLocator implements RouteDefinitionLocator {

	private final DiscoveryClient discoveryClient;
	private final String routeIdPrefix;

	public DiscoveryClientRouteDefinitionLocator(DiscoveryClient discoveryClient) {
		this.discoveryClient = discoveryClient;
		this.routeIdPrefix = this.discoveryClient.getClass().getSimpleName() + "_";
	}

	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return Flux.fromIterable(discoveryClient.getServices())
				.map(serviceId -> {
					RouteDefinition routeDefinition = new RouteDefinition();
					routeDefinition.setId(this.routeIdPrefix + serviceId);
					routeDefinition.setUri(URI.create("lb://" + serviceId));

					// add a predicate that matches the url at /serviceId
					/*PredicateDefinition barePredicate = new PredicateDefinition();
					barePredicate.setName(normalizePredicateName(PathRoutePredicate.class));
					barePredicate.addArg(PATTERN_KEY, "/" + serviceId);
					routeDefinition.getPredicates().add(barePredicate);*/

					// add a predicate that matches the url at /serviceId/**
					PredicateDefinition subPredicate = new PredicateDefinition();
					subPredicate.setName(normalizeRoutePredicateName(PathRoutePredicateFactory.class));
					subPredicate.addArg(PATTERN_KEY, "/" + serviceId + "/**");
					routeDefinition.getPredicates().add(subPredicate);

					//TODO: support for other default predicates

					// add a filter that removes /serviceId by default
					FilterDefinition filter = new FilterDefinition();
					filter.setName(normalizeFilterFactoryName(RewritePathGatewayFilterFactory.class));
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
