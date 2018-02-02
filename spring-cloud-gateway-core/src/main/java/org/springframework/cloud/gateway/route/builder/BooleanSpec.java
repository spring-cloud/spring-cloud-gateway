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
 */

package org.springframework.cloud.gateway.route.builder;

import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpStatus;
import org.springframework.tuple.Tuple;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import com.netflix.hystrix.HystrixObservableCommand;

import static org.springframework.cloud.gateway.route.builder.BooleanSpec.Operator.AND;
import static org.springframework.cloud.gateway.route.builder.BooleanSpec.Operator.NEGATE;
import static org.springframework.cloud.gateway.route.builder.BooleanSpec.Operator.OR;

public class BooleanSpec extends GatewayFilterSpec { //TODO after next release, extend UriSpec

	enum Operator { AND, OR, NEGATE }

	final Predicate<ServerWebExchange> predicate;

	public BooleanSpec(Route.Builder routeBuilder, RouteLocatorBuilder.Builder builder) {
		super(routeBuilder, builder);
		// save current predicate useful in kotlin dsl
		predicate = routeBuilder.getPredicate();
	}

	public BooleanOpSpec and() {
		return new BooleanOpSpec(routeBuilder, builder, AND);
	}

	public BooleanOpSpec or() {
		return new BooleanOpSpec(routeBuilder, builder, OR);
	}

	public BooleanOpSpec negate() {
		return new BooleanOpSpec(routeBuilder, builder, NEGATE);
	}

	public UriSpec filters(Function<GatewayFilterSpec, UriSpec> fn) {
		return fn.apply(new GatewayFilterSpec(routeBuilder, builder));
	}

	public static class BooleanOpSpec extends PredicateSpec {

		private Operator operator;

		BooleanOpSpec(Route.Builder routeBuilder, RouteLocatorBuilder.Builder builder, Operator operator) {
			super(routeBuilder, builder);
			Assert.notNull(operator, "operator may not be null");
			this.operator = operator;
		}

		@Override
		public BooleanSpec predicate(Predicate<ServerWebExchange> predicate) {
			switch (this.operator) {
				case AND:
					this.routeBuilder.and(predicate);
					break;
				case OR:
					this.routeBuilder.or(predicate);
					break;
				case NEGATE:
					this.routeBuilder.negate();
			}
			return createBooleanSpec();
		}
	}

	//TODO: after next release remove all deprecated methods
	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec gatewayFilters(List<GatewayFilter> gatewayFilters) {
		return super.gatewayFilters(gatewayFilters);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec add(GatewayFilter gatewayFilter) {
		return super.add(gatewayFilter);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec filter(GatewayFilter gatewayFilter) {
		return super.filter(gatewayFilter);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec filter(GatewayFilter gatewayFilter, int order) {
		return super.filter(gatewayFilter, order);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec addAll(Collection<GatewayFilter> gatewayFilters) {
		return super.addAll(gatewayFilters);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec addRequestHeader(String headerName, String headerValue) {
		return super.addRequestHeader(headerName, headerValue);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec addRequestParameter(String param, String value) {
		return super.addRequestParameter(param, value);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec addResponseHeader(String headerName, String headerValue) {
		return super.addResponseHeader(headerName, headerValue);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec hystrix(String commandName) {
		return super.hystrix(commandName);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec hystrix(HystrixObservableCommand.Setter setter) {
		return super.hystrix(setter);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec hystrix(String commandName, URI fallbackUri) {
		return super.hystrix(commandName, fallbackUri);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec hystrix(HystrixObservableCommand.Setter setter, URI fallbackUri) {
		return super.hystrix(setter, fallbackUri);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec prefixPath(String prefix) {
		return super.prefixPath(prefix);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec preserveHostHeader() {
		return super.preserveHostHeader();
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec redirect(int status, URI url) {
		return super.redirect(status, url);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec redirect(int status, String url) {
		return super.redirect(status, url);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec redirect(String status, URI url) {
		return super.redirect(status, url);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec redirect(String status, String url) {
		return super.redirect(status, url);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec redirect(HttpStatus status, URL url) {
		return super.redirect(status, url);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec removeNonProxyHeaders() {
		return super.removeNonProxyHeaders();
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec removeNonProxyHeaders(String... headersToRemove) {
		return super.removeNonProxyHeaders(headersToRemove);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec removeRequestHeader(String headerName) {
		return super.removeRequestHeader(headerName);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec removeResponseHeader(String headerName) {
		return super.removeResponseHeader(headerName);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec requestRateLimiter(Tuple args) {
		return super.requestRateLimiter(args);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec rewritePath(String regex, String replacement) {
		return super.rewritePath(regex, replacement);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec secureHeaders() {
		return super.secureHeaders();
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec setPath(String template) {
		return super.setPath(template);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec setRequestHeader(String headerName, String headerValue) {
		return super.setRequestHeader(headerName, headerValue);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec setResponseHeader(String headerName, String headerValue) {
		return super.setResponseHeader(headerName, headerValue);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec setStatus(int status) {
		return super.setStatus(status);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec setStatus(String status) {
		return super.setStatus(status);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec setStatus(HttpStatus status) {
		return super.setStatus(status);
	}

	@Override
	@Deprecated /** @deprecated use {@link #filters(Function)} */
	public GatewayFilterSpec saveSession() {
		return super.saveSession();
	}
}
