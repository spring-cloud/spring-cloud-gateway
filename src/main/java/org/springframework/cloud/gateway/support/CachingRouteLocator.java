package org.springframework.cloud.gateway.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.cloud.gateway.api.Route;
import org.springframework.cloud.gateway.api.RouteLocator;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Flux;

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
		return this.delegate.getRoutes().collectList().block();
	}

	@EventListener(RefreshRoutesEvent.class)
    /* for testing */ void handleRefresh() {
        refresh();
    }
}
