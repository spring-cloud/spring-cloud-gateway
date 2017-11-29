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

import java.time.ZonedDateTime;
import java.util.function.Predicate;

import org.springframework.http.HttpMethod;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;

/**
 * @deprecated inject {@link org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder} bean instead
 * @author Spencer Gibb
 */
@Deprecated
public class RoutePredicates {

	public static Predicate<ServerWebExchange> after(ZonedDateTime datetime) {
		return new AfterRoutePredicateFactory().apply(datetime);
	}

	public static Predicate<ServerWebExchange> before(ZonedDateTime datetime) {
		return new BeforeRoutePredicateFactory().apply(datetime);
	}

	public static Predicate<ServerWebExchange> between(ZonedDateTime datetime1, ZonedDateTime datetime2) {
		return new BetweenRoutePredicateFactory().apply(datetime1, datetime2);
	}

	public static Predicate<ServerWebExchange> cookie(String name, String regex) {
		return new CookieRoutePredicateFactory().apply(name, regex);
	}

	public static Predicate<ServerWebExchange> header(String header, String regex) {
		return new HeaderRoutePredicateFactory().apply(header, regex);
	}

	public static Predicate<ServerWebExchange> host(String pattern) {
		return new HostRoutePredicateFactory().apply(pattern);
	}

	public static Predicate<ServerWebExchange> host(String pattern, PathMatcher pathMatcher) {
		HostRoutePredicateFactory predicateFactory = new HostRoutePredicateFactory();
		predicateFactory.setPathMatcher(pathMatcher);
		return predicateFactory.apply(pattern);
	}

	public static Predicate<ServerWebExchange> method(String method) {
		return new MethodRoutePredicateFactory().apply(method);
	}

	public static Predicate<ServerWebExchange> method(HttpMethod method) {
		return new MethodRoutePredicateFactory().apply(method);
	}

	public static Predicate<ServerWebExchange> path(String pattern) {
		return new PathRoutePredicateFactory().apply(pattern);
	}

	public static Predicate<ServerWebExchange> query(String param, String regex) {
		return new QueryRoutePredicateFactory().apply(param, regex);
	}

	public static Predicate<ServerWebExchange> query(String param) {
		return new QueryRoutePredicateFactory().apply(param, null);
	}

	public static Predicate<ServerWebExchange> remoteAddr(String... addrs) {
		return new RemoteAddrRoutePredicateFactory().apply(addrs);
	}

	public static Predicate<ServerWebExchange> alwaysTrue() {
		return exchange -> true;
	}
}
