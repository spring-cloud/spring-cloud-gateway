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
 */

package org.springframework.cloud.gateway.route.builder;

import java.time.ZonedDateTime;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CookieRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HeaderRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RemoteAddrRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.WeightRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ServerWebExchange;

public class PredicateSpec extends UriSpec {

	PredicateSpec(Route.Builder routeBuilder, RouteLocatorBuilder.Builder builder) {
		super(routeBuilder, builder);
	}

	public PredicateSpec order(int order) {
		this.routeBuilder.order(order);
		return this;
	}

	public BooleanSpec predicate(Predicate<ServerWebExchange> predicate) {
		this.routeBuilder.predicate(predicate);
		return new BooleanSpec(this.routeBuilder, this.builder);
	}

	protected GatewayFilterSpec createGatewayFilterSpec() {
		return new GatewayFilterSpec(this.routeBuilder, this.builder);
	}

	public BooleanSpec after(ZonedDateTime datetime) {
		return predicate(getBean(AfterRoutePredicateFactory.class)
				.apply(c-> c.setDatetime(datetime.toString())));
	}

	public BooleanSpec before(ZonedDateTime datetime) {
		return predicate(getBean(BeforeRoutePredicateFactory.class).apply(c -> c.setDatetime(datetime.toString())));
	}

	public BooleanSpec between(ZonedDateTime datetime1, ZonedDateTime datetime2) {
		return predicate(getBean(BetweenRoutePredicateFactory.class)
				.apply(c -> c.setDatetime1(datetime1.toString()).setDatetime2(datetime2.toString())));
	}

	public BooleanSpec cookie(String name, String regex) {
		return predicate(getBean(CookieRoutePredicateFactory.class)
				.apply(c -> c.setName(name).setRegexp(regex)));
	}

	public BooleanSpec header(String header) {
		return predicate(getBean(HeaderRoutePredicateFactory.class)
				.apply(c -> c.setHeader(header))); //TODO: default regexp
	}

	public BooleanSpec header(String header, String regex) {
		return predicate(getBean(HeaderRoutePredicateFactory.class)
				.apply(c -> c.setHeader(header).setRegexp(regex)));
	}

	public BooleanSpec host(String pattern) {
		return predicate(getBean(HostRoutePredicateFactory.class)
				.apply(c-> c.setPattern(pattern)));
	}

	public BooleanSpec method(String method) {
		return predicate(getBean(MethodRoutePredicateFactory.class)
				.apply(c -> c.setMethod(HttpMethod.resolve(method))));
	}

	public BooleanSpec method(HttpMethod method) {
		return predicate(getBean(MethodRoutePredicateFactory.class)
				.apply(c -> c.setMethod(method)));
	}

	public BooleanSpec path(String pattern) {
		return predicate(getBean(PathRoutePredicateFactory.class)
				.apply(c -> c.setPattern(pattern)));
	}

	public BooleanSpec query(String param, String regex) {
		return predicate(getBean(QueryRoutePredicateFactory.class)
				.apply(c -> c.setParam(param).setRegexp(regex)));
	}

	public BooleanSpec query(String param) {
		return predicate(getBean(QueryRoutePredicateFactory.class)
				.apply(c -> c.setParam(param)));
	}

	public BooleanSpec remoteAddr(String... addrs) {
		return remoteAddr(null, addrs);
	}

	public BooleanSpec remoteAddr(RemoteAddressResolver resolver, String... addrs) {
		return predicate(getBean(RemoteAddrRoutePredicateFactory.class).apply(c -> {
			c.setSources(addrs);
			if (resolver != null) {
				c.setRemoteAddressResolver(resolver);
			}
		}));
	}

	public BooleanSpec weight(String group, int weight) {
		return predicate(getBean(WeightRoutePredicateFactory.class)
				.apply(c -> c.setGroup(group)
						.setRouteId(routeBuilder.getId())
						.setWeight(weight)));
	}

	public BooleanSpec alwaysTrue() {
		return predicate(exchange -> true);
	}
}
