package org.springframework.cloud.gateway.api;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.cloud.gateway.config.Route;

import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 */
public class CachingRouteReader implements RouteReader {

	private final RouteReader delegate;
	private final AtomicReference<List<Route>> cachedRoutes = new AtomicReference<>();

	public CachingRouteReader(RouteReader delegate) {
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
				routes -> CachingRouteReader.this.collectRoutes()));
	}

	private List<Route> collectRoutes() {
		return this.delegate.getRoutes().collectList().block();
	}
}
