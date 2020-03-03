/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.test;

import org.junit.ClassRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.Statement;

import org.springframework.cloud.gateway.filter.GatewayMetricsFilterTests;
import org.springframework.cloud.gateway.filter.factory.RewriteLocationResponseHeaderGatewayFilterFactoryTests;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeThat;

/**
 * To run this suite in an IDE, set env var GATEWAY_ADHOC_ENABLED=true in test runner.
 *
 * @author Spencer Gibb
 */
@RunWith(Suite.class)
@SuiteClasses({
		org.springframework.cloud.gateway.handler.RoutePredicateHandlerMappingIntegrationTests.class,
		org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactoryTests.class,
		org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactoryIntegrationTests.class,
		org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactoryTests.class,
		org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactoryIntegrationTests.class,
		org.springframework.cloud.gateway.handler.predicate.CookieRoutePredicateFactoryTests.class,
		org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactoryTests.class,
		org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactoryTests.class,
		org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicateFactoryTests.class,
		org.springframework.cloud.gateway.handler.predicate.WeightRoutePredicateFactoryIntegrationTests.class,
		org.springframework.cloud.gateway.handler.predicate.HeaderRoutePredicateFactoryTests.class,
		org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactoryTests.class,
		org.springframework.cloud.gateway.handler.predicate.RemoteAddrRoutePredicateFactoryTests.class,
		org.springframework.cloud.gateway.handler.predicate.ReadBodyPredicateFactoryTest.class,
		org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactoryTests.class,
		org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactoryTest.class,
		org.springframework.cloud.gateway.handler.RoutePredicateHandlerMappingTests.class,
		org.springframework.cloud.gateway.filter.factory.RewriteResponseHeaderGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.RedirectToGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.AddRequestParameterGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.PrefixPathGatewayFilterFactoryTest.class,
		org.springframework.cloud.gateway.filter.factory.RewriteResponseHeaderGatewayFilterFactoryUnitTests.class,
		RewriteLocationResponseHeaderGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.RequestHeaderToRequestUriGatewayFilterFactoryIntegrationTests.class,
		org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactoryIntegrationTests.class,
		org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactoryIntegrationTests.class,
		org.springframework.cloud.gateway.filter.factory.SetRequestHeaderGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.RequestHeaderToRequestUriGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.RequestSizeGatewayFilterFactoryTest.class,
		org.springframework.cloud.gateway.filter.factory.SetStatusGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.SaveSessionGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactoryIntegrationTests.class,
		org.springframework.cloud.gateway.filter.factory.PreserveHostHeaderGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactoryIntegrationTests.class,
		org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.SetResponseHeaderGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactoryTests.class,
		org.springframework.cloud.gateway.filter.WeightCalculatorWebFilterTests.class,
		org.springframework.cloud.gateway.filter.RouteToRequestUrlFilterTests.class,
		org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilterTests.class,
		org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilterTests.class,
		org.springframework.cloud.gateway.filter.headers.HttpStatusInResponseHeadersFilterTests.class,
		org.springframework.cloud.gateway.filter.headers.HttpHeadersFilterMixedTypeTests.class,
		org.springframework.cloud.gateway.filter.headers.HttpHeadersFilterTests.class,
		org.springframework.cloud.gateway.filter.headers.NonStandardHeadersInResponseTests.class,
		org.springframework.cloud.gateway.filter.headers.RemoveHopByHopHeadersFilterTests.class,
		org.springframework.cloud.gateway.filter.WebsocketRoutingFilterTests.class,
		org.springframework.cloud.gateway.filter.ratelimit.PrincipalNameKeyResolverIntegrationTests.class,
		org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiterConfigTests.class,
		org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiterTests.class,
		org.springframework.cloud.gateway.filter.NettyRoutingFilterIntegrationTests.class,
		GatewayMetricsFilterTests.class,
		org.springframework.cloud.gateway.filter.ForwardRoutingFilterTests.class,
		org.springframework.cloud.gateway.route.RouteDefinitionRouteLocatorTests.class,
		org.springframework.cloud.gateway.route.RouteTests.class,
		org.springframework.cloud.gateway.route.CachingRouteLocatorTests.class,
		org.springframework.cloud.gateway.route.RouteRefreshListenerTests.class,
		org.springframework.cloud.gateway.route.builder.RouteDslTests.class,
		org.springframework.cloud.gateway.route.builder.RouteBuilderTests.class,
		org.springframework.cloud.gateway.route.builder.GatewayFilterSpecTests.class,
		org.springframework.cloud.gateway.route.CachingRouteDefinitionLocatorTests.class,
		org.springframework.cloud.gateway.actuate.GatewayControllerEndpointTests.class,
		org.springframework.cloud.gateway.config.GatewayAutoConfigurationTests.class,
		org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocatorTests.class,
		org.springframework.cloud.gateway.discovery.ReactiveGatewayDiscoveryClientAutoConfigurationTests.class,
		org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocatorIntegrationTests.class,
		org.springframework.cloud.gateway.support.ShortcutConfigurableTests.class,
		org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolverTest.class,
		org.springframework.cloud.gateway.cors.CorsTests.class,
		org.springframework.cloud.gateway.test.FormIntegrationTests.class,
		org.springframework.cloud.gateway.test.ForwardTests.class,
		org.springframework.cloud.gateway.test.PostTests.class,
		org.springframework.cloud.gateway.test.ssl.SingleCertSSLTests.class,
		org.springframework.cloud.gateway.test.ssl.MultiCertSSLTests.class,
		org.springframework.cloud.gateway.test.ssl.SSLHandshakeTimeoutTests.class,
		org.springframework.cloud.gateway.test.websocket.WebSocketIntegrationTests.class,
		org.springframework.cloud.gateway.test.WebfluxNotIncludedTests.class,
		org.springframework.cloud.gateway.test.HttpStatusTests.class,
		org.springframework.cloud.gateway.test.GatewayIntegrationTests.class,
		org.springframework.cloud.gateway.test.sse.SseIntegrationTests.class })
public class AdhocTestSuite {

	@ClassRule
	public static AdhocEnabled adhocEnabled = new AdhocEnabled();

	static class AdhocEnabled implements TestRule {

		@Override
		public Statement apply(Statement base, Description description) {
			assumeThat("Adhoc Tests ignored", System.getenv("GATEWAY_ADHOC_ENABLED"),
					is(equalTo("true")));

			return base;
		}

	}

}
