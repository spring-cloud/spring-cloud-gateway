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

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.GatewayMetricsFilter;
import org.springframework.context.ApplicationListener;

/**
 * A metric to track the number of routes definitions on the gateway.
 *
 * @author Fredrich Ombico
 */
public class RouteDefinitionMetrics implements ApplicationListener<RefreshRoutesEvent> {

	private static final Log log = LogFactory.getLog(GatewayMetricsFilter.class);

	private final RouteDefinitionLocator routeLocator;

	private final AtomicInteger routeDefinitionCount;

	private final String metricsPrefix;

	public RouteDefinitionMetrics(MeterRegistry meterRegistry, RouteDefinitionLocator routeLocator,
			String metricsPrefix) {
		this.routeLocator = routeLocator;

		if (metricsPrefix.endsWith(".")) {
			this.metricsPrefix = metricsPrefix.substring(0, metricsPrefix.length() - 1);
		}
		else {
			this.metricsPrefix = metricsPrefix;
		}
		routeDefinitionCount = meterRegistry.gauge(this.metricsPrefix + ".routes.count", new AtomicInteger(0));
	}

	public String getMetricsPrefix() {
		return metricsPrefix;
	}

	@Override
	public void onApplicationEvent(RefreshRoutesEvent event) {
		routeLocator.getRouteDefinitions().count().subscribe(count -> {
			routeDefinitionCount.set(count.intValue());
			if (log.isDebugEnabled()) {
				log.debug("New routes count: " + routeDefinitionCount);
			}
		});
	}

}
