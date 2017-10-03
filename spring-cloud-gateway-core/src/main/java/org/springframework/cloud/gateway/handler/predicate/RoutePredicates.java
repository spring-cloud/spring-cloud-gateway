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
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactory.DATETIME1_KEY;
import static org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactory.DATETIME2_KEY;
import static org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory.METHOD_KEY;
import static org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory.PATTERN_KEY;
import static org.springframework.tuple.TupleBuilder.tuple;

/**
 * @author Spencer Gibb
 */
public class RoutePredicates {

	public static Predicate<ServerWebExchange> after(ZonedDateTime datetime) {
		return new AfterRoutePredicateFactory().apply(tuple().of(AfterRoutePredicateFactory.DATETIME_KEY, datetime));
	}

	public static Predicate<ServerWebExchange> before(ZonedDateTime datetime) {
		return new BeforeRoutePredicateFactory().apply(tuple().of(BeforeRoutePredicateFactory.DATETIME_KEY, datetime));
	}

	public static Predicate<ServerWebExchange> between(ZonedDateTime datetime1, ZonedDateTime datetime2) {
		return new BetweenRoutePredicateFactory().apply(tuple()
				.of(DATETIME1_KEY, datetime1, DATETIME2_KEY, datetime2));
	}

	public static Predicate<ServerWebExchange> cookie(String name, String regex) {
		return new CookieRoutePredicateFactory().apply(tuple()
				.of(CookieRoutePredicateFactory.NAME_KEY, name, CookieRoutePredicateFactory.REGEXP_KEY, regex));
	}

	public static Predicate<ServerWebExchange> header(String header, String regex) {
		return new HeaderRoutePredicateFactory().apply(tuple()
				.of(HeaderRoutePredicateFactory.HEADER_KEY, header, HeaderRoutePredicateFactory.REGEXP_KEY, regex));
	}

	public static Predicate<ServerWebExchange> host(String pattern) {
		return new HostRoutePredicateFactory().apply(tuple().of(PATTERN_KEY, pattern));
	}

	public static Predicate<ServerWebExchange> method(String method) {
		return new MethodRoutePredicateFactory().apply(tuple().of(METHOD_KEY, method));
	}

	public static Predicate<ServerWebExchange> path(String pattern) {
		return new PathRoutePredicateFactory().apply(tuple().of(PATTERN_KEY, pattern));
	}

	public static Predicate<ServerWebExchange> query(String param, String regex) {
		return new QueryRoutePredicateFactory().apply(tuple().
				of(QueryRoutePredicateFactory.PARAM_KEY, param, QueryRoutePredicateFactory.REGEXP_KEY, regex));
	}

	public static Predicate<ServerWebExchange> remoteAddr(String... addrs) {
		List<String> names = IntStream.range(0, addrs.length).mapToObj(i -> "addr" + i).collect(Collectors.toList());
		return new RemoteAddrRoutePredicateFactory().apply(tuple().ofNamesAndValues(names, Arrays.asList(addrs)));
	}

	public static Predicate<ServerWebExchange> alwaysTrue() {
		return exchange -> true;
	}
}
