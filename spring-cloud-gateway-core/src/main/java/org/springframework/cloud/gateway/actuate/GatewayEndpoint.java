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

package org.springframework.cloud.gateway.actuate;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.model.Route;
import org.springframework.cloud.gateway.model.RouteLocator;
import org.springframework.cloud.gateway.model.RouteWriter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.route.RouteFilter;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
//TODO: move to new Spring Boot 2.0 actuator when ready
//@ConfigurationProperties(prefix = "endpoints.gateway")
@RestController
@RequestMapping("/admin/gateway")
public class GatewayEndpoint implements ApplicationEventPublisherAware {/*extends AbstractEndpoint<Map<String, Object>> {*/

	private static final Log log = LogFactory.getLog(GatewayEndpoint.class);

	private RouteLocator routeLocator;
	private List<GlobalFilter> globalFilters;
	private List<RouteFilter> routeFilters;
	private FilteringWebHandler filteringWebHandler;
	private RouteWriter routeWriter;
	private ApplicationEventPublisher publisher;

	public GatewayEndpoint(RouteLocator routeLocator, List<GlobalFilter> globalFilters,
						   List<RouteFilter> routeFilters, FilteringWebHandler filteringWebHandler,
						   RouteWriter routeWriter) {
		//super("gateway");
		this.routeLocator = routeLocator;
		this.globalFilters = globalFilters;
		this.routeFilters = routeFilters;
		this.filteringWebHandler = filteringWebHandler;
		this.routeWriter = routeWriter;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	/*@Override
	public Map<String, Object> invoke() {
	}*/

	//TODO: this should really be a listener that responds to a RefreshEvent
	@PostMapping("/refresh")
	public Mono<Void> refresh() {
	    this.publisher.publishEvent(new RefreshRoutesEvent(this));
		return Mono.empty();
	}

	@GetMapping("/globalfilters")
	public Map<String, Object> globalfilters() {
		return getNamesToOrders(this.globalFilters);
	}

	@GetMapping("/routefilters")
	public Map<String, Object> routefilers() {
		return getNamesToOrders(this.routeFilters);
	}

	private <T> Map<String, Object> getNamesToOrders(List<T> list) {
		HashMap<String, Object> filters = new HashMap<>();

		for (Object o : list) {
			Integer order = null;
			if (o instanceof Ordered) {
				order = ((Ordered)o).getOrder();
			}
			//filters.put(o.getClass().getName(), order);
			filters.put(o.toString(), order);
		}

		return filters;
	}

	@GetMapping("/routes")
	public Mono<List<Route>> routes() {
		return this.routeLocator.getRoutes().collectList();
	}

/*
http POST :8080/admin/gateway/routes/apiaddreqhead uri=http://httpbin.org:80 predicates:='["Host=**.apiaddrequestheader.org", "Path=/headers"]' filters:='["AddRequestHeader=X-Request-ApiFoo, ApiBar"]'
*/
	@PostMapping("/routes/{id}")
	public Mono<ResponseEntity<Void>> save(@PathVariable String id, @RequestBody Mono<Route> route) {
		return this.routeWriter.save(route.map(r ->  {
			r.setId(id);
			log.debug("Saving route: " + route);
			return r;
		})).then(() ->
			Mono.just(ResponseEntity.created(URI.create("/routes/"+id)).build())
		);
	}

	@DeleteMapping("/routes/{id}")
	public Mono<ResponseEntity<Object>> delete(@PathVariable String id) {
		return this.routeWriter .delete(Mono.just(id))
				.then(() -> Mono.just(ResponseEntity.ok().build()))
				.otherwise(t -> t instanceof NotFoundException, t -> Mono.just(ResponseEntity.notFound().build()));
	}

	@GetMapping("/routes/{id}")
	public Mono<ResponseEntity<Route>> route(@PathVariable String id) {
		return this.routeLocator.getRoutes()
				.filter(route -> route.getId().equals(id))
				.singleOrEmpty()
				.map(route -> ResponseEntity.ok(route))
				.otherwiseIfEmpty(Mono.just(ResponseEntity.notFound().build()));
	}

	@GetMapping("/routes/{id}/combinedfilters")
	public Map<String, Object> combinedfilters(@PathVariable String id) {
		Mono<Route> route = this.routeLocator.getRoutes()
				.filter(r -> r.getId().equals(id))
				.singleOrEmpty();
		Optional<Route> optional = Optional.ofNullable(route.block()); //TODO: remove block();
		return getNamesToOrders(this.filteringWebHandler.combineFiltersForRoute(optional));
	}
}
