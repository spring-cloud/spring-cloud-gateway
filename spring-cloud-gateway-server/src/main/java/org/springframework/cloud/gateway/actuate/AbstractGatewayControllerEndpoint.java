/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.actuate;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.event.RouteDeletedEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.Ordered;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * @author Spencer Gibb
 */
public class AbstractGatewayControllerEndpoint implements ApplicationEventPublisherAware {

	private static final Log log = LogFactory.getLog(GatewayControllerEndpoint.class);

	protected RouteDefinitionLocator routeDefinitionLocator;

	protected List<GlobalFilter> globalFilters;

	// TODO change casing in next major release
	protected List<GatewayFilterFactory> GatewayFilters;

	protected List<RoutePredicateFactory> routePredicates;

	protected RouteDefinitionWriter routeDefinitionWriter;

	protected RouteLocator routeLocator;

	protected ApplicationEventPublisher publisher;

	protected WebEndpointProperties webEndpointProperties;

	private final SimpleMetadataReaderFactory simpleMetadataReaderFactory = new SimpleMetadataReaderFactory();

	public AbstractGatewayControllerEndpoint(RouteDefinitionLocator routeDefinitionLocator,
			List<GlobalFilter> globalFilters, List<GatewayFilterFactory> gatewayFilters,
			List<RoutePredicateFactory> routePredicates, RouteDefinitionWriter routeDefinitionWriter,
			RouteLocator routeLocator, WebEndpointProperties webEndpointProperties) {
		this.routeDefinitionLocator = routeDefinitionLocator;
		this.globalFilters = globalFilters;
		this.GatewayFilters = gatewayFilters;
		this.routePredicates = routePredicates;
		this.routeDefinitionWriter = routeDefinitionWriter;
		this.routeLocator = routeLocator;
		this.webEndpointProperties = webEndpointProperties;
	}

	@GetMapping("/")
	Mono<List<GatewayEndpointInfo>> getEndpoints() {
		List<GatewayEndpointInfo> endpoints = mergeEndpoints(
				getAvailableEndpointsForClass(AbstractGatewayControllerEndpoint.class.getName()),
				getAvailableEndpointsForClass(GatewayControllerEndpoint.class.getName()));

		return Flux.fromIterable(endpoints)
			.map(p -> p)
			.flatMap(path -> this.routeLocator.getRoutes()
				.map(r -> generateHref(r, path))
				.distinct()
				.collectList()
				.flatMapMany(Flux::fromIterable))
			.distinct() // Ensure overall uniqueness
			.collectList();
	}

	private List<GatewayEndpointInfo> mergeEndpoints(List<GatewayEndpointInfo> listA, List<GatewayEndpointInfo> listB) {
		Map<String, List<String>> mergedMap = new HashMap<>();

		Stream.concat(listA.stream(), listB.stream())
			.forEach(e -> mergedMap.computeIfAbsent(e.getHref(), k -> new ArrayList<>())
				.addAll(Arrays.asList(e.getMethods())));

		return mergedMap.entrySet()
			.stream()
			.map(entry -> new GatewayEndpointInfo(entry.getKey(), entry.getValue()))
			.collect(Collectors.toList());
	}

	private List<GatewayEndpointInfo> getAvailableEndpointsForClass(String className) {
		try {
			MetadataReader metadataReader = simpleMetadataReaderFactory.getMetadataReader(className);
			Set<MethodMetadata> annotatedMethods = metadataReader.getAnnotationMetadata()
				.getAnnotatedMethods(RequestMapping.class.getName());

			String gatewayActuatorPath = webEndpointProperties.getBasePath() + "/gateway";
			return annotatedMethods.stream()
				.map(method -> new GatewayEndpointInfo(gatewayActuatorPath
						+ ((String[]) method.getAnnotationAttributes(RequestMapping.class.getName()).get("path"))[0],
						((RequestMethod[]) method.getAnnotationAttributes(RequestMapping.class.getName())
							.get("method"))[0].name()))
				.collect(Collectors.toList());
		}
		catch (IOException exception) {
			log.warn(exception.getMessage());
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
		}
	}

	private GatewayEndpointInfo generateHref(Route r, GatewayEndpointInfo path) {
		return new GatewayEndpointInfo(path.getHref().replace("{id}", r.getId()), Arrays.asList(path.getMethods()));
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	// TODO: Add uncommited or new but not active routes endpoint

	@PostMapping("/refresh")
	public Mono<Void> refresh(@RequestParam(value = "metadata", required = false) List<String> byMetadata) {
		publishRefreshEvent(byMetadata);
		return Mono.empty();
	}

	private void publishRefreshEvent(List<String> byMetadata) {
		RefreshRoutesEvent event;
		if (!CollectionUtils.isEmpty(byMetadata)) {
			event = new RefreshRoutesEvent(this, convertToMap(byMetadata));
		}
		else {
			event = new RefreshRoutesEvent(this);
		}

		this.publisher.publishEvent(event);
	}

	private Map<String, Object> convertToMap(List<String> byMetadata) {
		return byMetadata.stream()
			.map(keyValueStr -> keyValueStr.split(":"))
			.collect(Collectors.toMap(kv -> kv[0], kv -> kv.length > 1 ? kv[1] : null));
	}

	@GetMapping("/globalfilters")
	public Mono<HashMap<String, Object>> globalfilters() {
		return getNamesToOrders(this.globalFilters);
	}

	@GetMapping("/routefilters")
	public Mono<HashMap<String, Object>> routefilers() {
		return getNamesToOrders(this.GatewayFilters);
	}

	@GetMapping("/routepredicates")
	public Mono<HashMap<String, Object>> routepredicates() {
		return getNamesToOrders(this.routePredicates);
	}

	private <T> Mono<HashMap<String, Object>> getNamesToOrders(List<T> list) {
		return Flux.fromIterable(list).reduce(new HashMap<>(), this::putItem);
	}

	private HashMap<String, Object> putItem(HashMap<String, Object> map, Object o) {
		Integer order = null;
		if (o instanceof Ordered) {
			order = ((Ordered) o).getOrder();
		}
		// filters.put(o.getClass().getName(), order);
		map.put(o.toString(), order);
		return map;
	}

	/*
	 * http POST :8080/admin/gateway/routes/apiaddreqhead uri=http://httpbin.org:80
	 * predicates:='["Host=**.apiaddrequestheader.org", "Path=/headers"]'
	 * filters:='["AddRequestHeader=X-Request-ApiFoo, ApiBar"]'
	 */
	@PostMapping("/routes/{id}")
	@SuppressWarnings("unchecked")
	public Mono<ResponseEntity<Object>> save(@PathVariable String id, @RequestBody RouteDefinition route) {

		return Mono.just(route)
			.doOnNext(this::validateRouteDefinition)
			.flatMap(routeDefinition -> this.routeDefinitionWriter.save(Mono.just(routeDefinition).map(r -> {
				r.setId(id);
				log.debug("Saving route: " + route);
				return r;
			})).then(Mono.defer(() -> Mono.just(ResponseEntity.created(URI.create("/routes/" + id)).build()))))
			.switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.badRequest().build())));
	}

	@PostMapping("/routes")
	@SuppressWarnings("unchecked")
	public Mono<ResponseEntity<Object>> save(@RequestBody List<RouteDefinition> routes) {
		routes.stream().forEach(routeDef -> {
			validateRouteDefinition(routeDef);
			validateRouteId(routeDef);
		});

		return Flux.fromIterable(routes)
			.flatMap(routeDefinition -> this.routeDefinitionWriter.save(Mono.just(routeDefinition).map(r -> {
				log.debug("Saving route: " + routeDefinition);
				return r;
			})))
			.then(Mono.defer(() -> Mono.just(ResponseEntity.ok().build())))
			.switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.badRequest().build())));
	}

	private void validateRouteId(RouteDefinition routeDefinition) {
		if (routeDefinition.getId() == null) {
			handleError("Saving multiple routes require specifying the ID for every route");
		}
	}

	private void validateRouteDefinition(RouteDefinition routeDefinition) {
		Set<String> unavailableFilterDefinitions = routeDefinition.getFilters()
			.stream()
			.filter(rd -> !isAvailable(rd))
			.map(FilterDefinition::getName)
			.collect(Collectors.toSet());

		Set<String> unavailablePredicatesDefinitions = routeDefinition.getPredicates()
			.stream()
			.filter(rd -> !isAvailable(rd))
			.map(PredicateDefinition::getName)
			.collect(Collectors.toSet());
		if (!unavailableFilterDefinitions.isEmpty()) {
			handleUnavailableDefinition(FilterDefinition.class.getSimpleName(), unavailableFilterDefinitions);
		}
		else if (!unavailablePredicatesDefinitions.isEmpty()) {
			handleUnavailableDefinition(PredicateDefinition.class.getSimpleName(), unavailablePredicatesDefinitions);
		}

		validateRouteUri(routeDefinition.getUri());
	}

	private void validateRouteUri(URI uri) {
		if (uri == null) {
			handleError("The URI can not be empty");
		}

		if (!StringUtils.hasText(uri.getScheme())) {
			handleError(String.format("The URI format [%s] is incorrect, scheme can not be empty", uri));
		}
	}

	private void handleUnavailableDefinition(String simpleName, Set<String> unavailableDefinitions) {
		final String errorMessage = String.format("Invalid %s: %s", simpleName, unavailableDefinitions);
		log.warn(errorMessage);
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
	}

	private void handleError(String errorMessage) {
		log.warn(errorMessage);
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
	}

	private boolean isAvailable(FilterDefinition filterDefinition) {
		return GatewayFilters.stream()
			.anyMatch(gatewayFilterFactory -> filterDefinition.getName().equals(gatewayFilterFactory.name()));
	}

	private boolean isAvailable(PredicateDefinition predicateDefinition) {
		return routePredicates.stream()
			.anyMatch(routePredicate -> predicateDefinition.getName().equals(routePredicate.name()));
	}

	@DeleteMapping("/routes/{id}")
	public Mono<ResponseEntity<Object>> delete(@PathVariable String id) {
		return this.routeDefinitionWriter.delete(Mono.just(id)).then(Mono.defer(() -> {
			publisher.publishEvent(new RouteDeletedEvent(this, id));
			return Mono.just(ResponseEntity.ok().build());
		})).onErrorResume(t -> t instanceof NotFoundException, t -> Mono.just(ResponseEntity.notFound().build()));
	}

	@GetMapping("/routes/{id}/combinedfilters")
	public Mono<HashMap<String, Object>> combinedfilters(@PathVariable String id) {
		// TODO: missing global filters
		return this.routeLocator.getRoutes()
			.filter(route -> route.getId().equals(id))
			.reduce(new HashMap<>(), this::putItem);
	}

}
