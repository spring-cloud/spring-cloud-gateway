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

package org.springframework.cloud.gateway.handler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cloud.gateway.event.RefreshRoutesResultEvent;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.FilterAdapter;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * Cache to avoid sorting all filters on each request.
 *
 * @author ziming liu
 */
public class GatewayFiltersCache implements ApplicationListener<RefreshRoutesResultEvent> {

	private final List<GatewayFilter> globalFilters;

	private final Map<Route, List<GatewayFilter>> cachedGatewayFilters = new ConcurrentHashMap<>();

	public GatewayFiltersCache(List<GlobalFilter> globalFilters) {
		this.globalFilters = FilterAdapter.loadFilters(globalFilters);
	}

	@Override
	public void onApplicationEvent(RefreshRoutesResultEvent event) {
		if (event.getThrowable() != null) {
			return;
		}
		this.cachedGatewayFilters.clear();
	}

	public List<GatewayFilter> cacheGatewayFilters(Route route) {
		if (cachedGatewayFilters.containsKey(route)) {
			return cachedGatewayFilters.get(route);
		}
		List<GatewayFilter> combined = route.getFilters();
		combined.addAll(this.globalFilters);
		AnnotationAwareOrderComparator.sort(combined);
		cachedGatewayFilters.put(route, combined);
		return combined;
	}

}
