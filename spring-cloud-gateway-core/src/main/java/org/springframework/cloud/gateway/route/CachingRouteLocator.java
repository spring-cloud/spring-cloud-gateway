/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.gateway.route;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Flux;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * @author Spencer Gibb
 */
public class CachingRouteLocator implements RouteLocator {

	private final RouteLocator delegate;
	private final AtomicReference<List<Route>> cachedRoutes = new AtomicReference<>();

	public CachingRouteLocator(RouteLocator delegate) {
		this.delegate = delegate;
		this.cachedRoutes.compareAndSet(null, collectRoutes());
	}

	@Override
	public Flux<Route> getRoutes() {
		return Flux.fromIterable(this.cachedRoutes.get());
	}

	/**
	 * Sets the new routes
	 * @return old routes
	 */
	public Flux<Route> refresh() {
		return Flux.fromIterable(this.cachedRoutes.getAndUpdate(
				routes -> CachingRouteLocator.this.collectRoutes()));
	}

	private List<Route> collectRoutes() {
		List<Route> routes = this.delegate.getRoutes().collectList().block();
		AnnotationAwareOrderComparator.sort(routes);
		return routes;
	}

	@EventListener(RefreshRoutesEvent.class)
    /* for testing */ void handleRefresh() {
        refresh();
    }
}
