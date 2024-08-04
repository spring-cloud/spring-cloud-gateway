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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.cache.CacheFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesResultEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

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

	private ApplicationEventPublisher applicationEventPublisher;

	public CachingRouteLocator(RouteLocator delegate) {
		this.delegate = delegate;
		routes = CacheFlux.lookup(cache, CACHE_KEY, Route.class).onCacheMissResume(this::fetch);
	}

	private Flux<Route> fetch() {
		return this.delegate.getRoutes().sort(AnnotationAwareOrderComparator.INSTANCE);
	}

	private Flux<Route> fetch(Map<String, Object> metadata) {
		return this.delegate.getRoutesByMetadata(metadata).sort(AnnotationAwareOrderComparator.INSTANCE);
	}

	@Override
	public Flux<Route> getRoutes() {
		return this.routes;
	}

	/**
	 * Clears the routes cache.
	 * @return routes flux
	 */
	public Flux<Route> refresh() {
		this.cache.remove(CACHE_KEY);
		return this.routes;
	}

	@Override
	public void onApplicationEvent(RefreshRoutesEvent event) {
		try {
			if (this.cache.containsKey(CACHE_KEY) && event.isScoped()) {
				final Mono<List<Route>> scopedRoutes = fetch(event.getMetadata()).collect(Collectors.toList())
					.onErrorResume(s -> Mono.just(List.of()));

				scopedRoutes.subscribe(scopedRoutesList -> {
					updateCache(Flux.concat(Flux.fromIterable(scopedRoutesList), getNonScopedRoutes(event))
						.sort(AnnotationAwareOrderComparator.INSTANCE));
				}, this::handleRefreshError);
			}
			else {
				final Mono<List<Route>> allRoutes = fetch().collect(Collectors.toList());
				allRoutes.subscribe(list -> updateCache(Flux.fromIterable(list)), this::handleRefreshError);
			}
		}
		catch (Throwable e) {
			handleRefreshError(e);
		}
	}

	private synchronized void updateCache(Flux<Route> routes) {
		routes.materialize()
			.collect(Collectors.toList())
			.subscribe(this::publishRefreshEvent, this::handleRefreshError);
	}

	private void publishRefreshEvent(List<Signal<Route>> signals) {
		cache.put(CACHE_KEY, signals);
		applicationEventPublisher.publishEvent(new RefreshRoutesResultEvent(this));
	}

	private Flux<Route> getNonScopedRoutes(RefreshRoutesEvent scopedEvent) {
		return this.getRoutes()
			.filter(route -> !RouteLocator.matchMetadata(route.getMetadata(), scopedEvent.getMetadata()));
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
