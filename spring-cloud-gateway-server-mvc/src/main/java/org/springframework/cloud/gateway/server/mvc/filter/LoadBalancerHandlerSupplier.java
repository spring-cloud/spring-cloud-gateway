/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.filter;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.springframework.cloud.gateway.server.mvc.config.RouteProperties;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerDiscoverer;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerSupplier;

public class LoadBalancerHandlerSupplier implements HandlerSupplier {

	@Override
	public Collection<Method> get() {
		return Arrays.asList(getClass().getMethods());
	}

	public static HandlerDiscoverer.Result lb(RouteProperties routeProperties) {
		return lb(routeProperties.getUri());
	}

	public static HandlerDiscoverer.Result lb(URI uri) {
		// TODO: how to do something other than http
		return new HandlerDiscoverer.Result(HandlerFunctions.http(), Collections.emptyList(),
				Collections.singletonList(LoadBalancerFilterFunctions.lb(uri.getHost())));
	}

}
