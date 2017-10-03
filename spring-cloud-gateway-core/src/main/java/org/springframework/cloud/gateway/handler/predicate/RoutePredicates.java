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

package org.springframework.cloud.gateway.handler.predicate;

import org.springframework.web.server.ServerWebExchange;

import java.util.function.Predicate;

import static org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory.METHOD_KEY;
import static org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory.PATTERN_KEY;
import static org.springframework.tuple.TupleBuilder.tuple;

/**
 * @author Spencer Gibb
 */
public class RoutePredicates {

	//TODO: add support for AfterRoutePredicateFactory

	//TODO: add support for BeforeRoutePredicateFactory

	//TODO: add support for BetweenRoutePredicateFactory

	//TODO: add support for CookieRoutePredicateFactory

	//TODO: add support for RoutePredicates

	//TODO: add support for HeaderRoutePredicateFactory

	public static Predicate<ServerWebExchange> host(String pattern) {
		return new HostRoutePredicateFactory().apply(tuple().of(PATTERN_KEY, pattern));
	}

	public static Predicate<ServerWebExchange> method(String method) {
		return new MethodRoutePredicateFactory().apply(tuple().of(METHOD_KEY, method));
	}

	public static Predicate<ServerWebExchange> path(String pattern) {
		return new PathRoutePredicateFactory().apply(tuple().of(PATTERN_KEY, pattern));
	}

	//TODO: add support for PredicateDefinition

	//TODO: add support for QueryRoutePredicateFactory

	//TODO: add support for RemoteAddrRoutePredicateFactory

	public static Predicate<ServerWebExchange> alwaysTrue() {
		return exchange -> true;
	}
}
