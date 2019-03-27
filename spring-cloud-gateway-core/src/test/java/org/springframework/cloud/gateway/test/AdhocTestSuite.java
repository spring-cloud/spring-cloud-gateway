/*
 * Copyright 2013-2017 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.test;

import org.junit.ClassRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.Statement;

import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.AddRequestParameterGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.HystrixGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.PreserveHostHeaderGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.RedirectToGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactoryIntegrationTests;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.RewriteResponseHeaderGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactoryIntegrationTests;
import org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.SetResponseHeaderGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.SetStatusGatewayFilterFactoryTests;
import org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilterTests;
import org.springframework.cloud.gateway.filter.headers.RemoveHopByHopHeadersFilterTests;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilterTests;
import org.springframework.cloud.gateway.filter.ratelimit.PrincipalNameKeyResolverIntegrationTests;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiterTests;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactoryTests;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocatorTests;
import org.springframework.cloud.gateway.test.websocket.WebSocketIntegrationTests;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeThat;

/**
 * To run this suite in an IDE, set env var GATEWAY_ADHOC_ENABLED=true in test runner.
 * @author Spencer Gibb
 */
@RunWith(Suite.class)
@SuiteClasses({
		WebfluxNotIncludedTests.class,
		GatewayIntegrationTests.class,
		FormIntegrationTests.class,
		HttpStatusTests.class,
		PostTests.class,
		ForwardTests.class,
		WebSocketIntegrationTests.class,
		ForwardedHeadersFilterTests.class,
		RemoveHopByHopHeadersFilterTests.class,
		XForwardedHeadersFilterTests.class,
		// FilterFactory Tests
		RemoveResponseHeaderGatewayFilterFactoryTests.class,
		HystrixGatewayFilterFactoryTests.class,
		RewritePathGatewayFilterFactoryIntegrationTests.class,
		RemoveRequestHeaderGatewayFilterFactoryTests.class,
		SetPathGatewayFilterFactoryTests.class,
		RewritePathGatewayFilterFactoryTests.class,
		SetStatusGatewayFilterFactoryTests.class,
		RedirectToGatewayFilterFactoryTests.class,
		AddRequestHeaderGatewayFilterFactoryTests.class,
		SecureHeadersGatewayFilterFactoryTests.class,
		RequestRateLimiterGatewayFilterFactoryTests.class,
		SetPathGatewayFilterFactoryIntegrationTests.class,
		AddRequestParameterGatewayFilterFactoryTests.class,
		SetResponseHeaderGatewayFilterFactoryTests.class,
		DedupeResponseHeaderGatewayFilterFactoryTests.class,
		RewriteResponseHeaderGatewayFilterFactoryTests.class,
		PrincipalNameKeyResolverIntegrationTests.class,
		RedisRateLimiterTests.class,
		RouteDefinitionRouteLocatorTests.class,
		PreserveHostHeaderGatewayFilterFactoryTests.class,
		// PredicateFactory Tests
		MethodRoutePredicateFactoryTests.class,
		HostRoutePredicateFactoryTests.class,
		AfterRoutePredicateFactoryTests.class,
		PathRoutePredicateFactoryTests.class,
		BetweenRoutePredicateFactoryTests.class,
		BeforeRoutePredicateFactoryTests.class,

})
public class AdhocTestSuite {

	@ClassRule
	public static AdhocEnabled adhocEnabled = new AdhocEnabled();

	static class AdhocEnabled implements TestRule {

		@Override
		public Statement apply(Statement base, Description description) {
			assumeThat("Adhoc Tests ignored",
					System.getenv("GATEWAY_ADHOC_ENABLED"),
					is(equalTo("true")));

			return base;
		}
	}
}
