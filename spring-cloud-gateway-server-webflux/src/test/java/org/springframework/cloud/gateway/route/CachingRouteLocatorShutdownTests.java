/*
 * Copyright 2013-present the original author or authors.
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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduction tests for gh-2471: shutdown can cause BeanCreationNotAllowedException
 * because RefreshRoutesEvent listeners (such as CachingRouteLocator) keep delegating to
 * RouteLocator beans even after the ApplicationContext has started destroying singletons
 * (e.g. ConsulServiceRegistry.deregister() publishing a RefreshRoutesEvent during
 * destroy). The expected behaviour is that CachingRouteLocator short-circuits the refresh
 * once shutdown has begun, so it does not try to obtain (or recreate) any beans that the
 * BeanFactory will refuse to provide.
 */
public class CachingRouteLocatorShutdownTests {

	@Test
	public void doesNotRefreshAfterContextHasStartedClosing() {
		GenericApplicationContext context = new GenericApplicationContext();
		context.refresh();

		AtomicInteger fetchCount = new AtomicInteger();
		Route route = Route.async().id("r1").uri("http://localhost").order(0).predicate(exchange -> true).build();

		RouteLocator delegate = () -> {
			fetchCount.incrementAndGet();
			return Flux.just(route);
		};

		CachingRouteLocator locator = new CachingRouteLocator(delegate);
		locator.setApplicationEventPublisher(context);
		context.addApplicationListener(locator);

		// Prime the cache so the first fetch counts as the baseline (1).
		locator.getRoutes().collectList().block();
		int baselineFetches = fetchCount.get();
		assertThat(baselineFetches).isEqualTo(1);

		// Simulate the gh-2471 trigger: a bean (Consul service registry, in the
		// real report) publishes a RefreshRoutesEvent from its destroy callback
		// while the BeanFactory is already destroying singletons. At that point
		// the BeanFactory refuses to create or look up new beans and throws
		// BeanCreationNotAllowedException.
		context.getDefaultListableBeanFactory().registerDisposableBean("refreshOnShutdown", new DisposableBean() {
			@Override
			public void destroy() {
				context.publishEvent(new RefreshRoutesEvent(this));
			}
		});

		context.close();

		// With shutdown gating in place, the destroy-phase RefreshRoutesEvent
		// must be ignored. Today this assertion fails because the listener
		// keeps invoking the delegate even after destroy has begun.
		assertThat(fetchCount.get())
			.as("CachingRouteLocator must not delegate to RouteLocator beans during context destruction (gh-2471)")
			.isEqualTo(baselineFetches);
	}

}
