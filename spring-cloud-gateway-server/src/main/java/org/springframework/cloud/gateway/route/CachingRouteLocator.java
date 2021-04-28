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

package org.springframework.cloud.gateway.route;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesResultEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import reactor.cache.CacheFlux;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Spencer Gibb
 */
public class CachingRouteLocator
		implements Ordered, RouteLocator, ApplicationListener<RefreshRoutesEvent>, ApplicationEventPublisherAware {

	private static final Log log = LogFactory.getLog(CachingRouteLocator.class);

	private static final String CACHE_KEY = "routes";

	private final RouteLocator delegate;

	private final Flux<Route> routes;

	private final Map<String, List> cache = new ConcurrentHashMap<>();

	/**
	 * A copy of routing information that is specifically served for custom routing resolution
	 * key-route definition id, value-route
	 */
	private final AtomicReference<Map<String, Route>> cacheCopy = new AtomicReference<>(new HashMap<>());

	private ApplicationEventPublisher applicationEventPublisher;

	public CachingRouteLocator(RouteLocator delegate) {
		this.delegate = delegate;
		routes = CacheFlux.lookup(cache, CACHE_KEY, Route.class).onCacheMissResume(this::fetch);
	}

	private Flux<Route> fetch() {
		return this.delegate.getRoutes().sort(AnnotationAwareOrderComparator.INSTANCE);
	}

	@Override
	public Flux<Route> getRoutes() {
		return this.routes;
	}

	@Override
	public Flux<Map<String, Route>> getRouteMap() {
		return Flux.just(cacheCopy.get());
	}

	/**
	 * Clears the routes cache.
	 * This method is only used in the test, and the use of production environment will bring performance risks
	 *
	 * @return routes flux
	 */
	public Flux<Route> refresh() {
		this.cache.clear();
		return this.routes;
	}

	@Override
	public void onApplicationEvent(RefreshRoutesEvent event) {
		try {
			fetch().collect(Collectors.toList())
					.doOnSuccess(r -> getRoutes().subscribe(route -> cacheCopy.get().put(route.getId(), route)))
					.subscribe(
							list -> Flux.fromIterable(list).materialize().collect(Collectors.toList()).subscribe(signals -> {
								applicationEventPublisher.publishEvent(new RefreshRoutesResultEvent(this));
								cache.put(CACHE_KEY, signals);
							}, this::handleRefreshError), this::handleRefreshError);
		} catch (Throwable e) {
			handleRefreshError(e);
		}
	}

	private void handleRefreshError(Throwable throwable) {
		if (log.isErrorEnabled()) {
			log.error("Refresh routes error !!!", throwable);
		}
		applicationEventPublisher.publishEvent(new RefreshRoutesResultEvent(this, throwable));
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

}
