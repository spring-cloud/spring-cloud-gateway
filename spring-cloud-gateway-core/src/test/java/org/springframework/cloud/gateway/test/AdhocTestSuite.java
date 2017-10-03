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

package org.springframework.cloud.gateway.test;

import org.junit.ClassRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.Statement;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderWebFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.AddRequestParameterWebFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.HystrixWebFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.RedirectToWebFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.RemoveNonProxyHeadersWebFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderWebFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.RewritePathWebFilterFactoryIntegrationTests;
import org.springframework.cloud.gateway.filter.factory.RewritePathWebFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersWebFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.SetPathWebFilterFactoryIntegrationTests;
import org.springframework.cloud.gateway.filter.factory.SetPathWebFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.SetResponseWebFilterFactoryTests;
import org.springframework.cloud.gateway.filter.factory.SetStatusWebFilterFactoryTests;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiterTests;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactoryTests;
import org.springframework.cloud.gateway.test.websocket.WebSocketIntegrationTests;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeThat;

/**
 * To run this suite in an IDE, set env var GATEWAY_ADHOC_ENABLED=true in test runner.
 * @author Spencer Gibb
 */
@RunWith(Suite.class)
@SuiteClasses({GatewayIntegrationTests.class,
		FormIntegrationTests.class,
		PostTests.class,
		RedisRateLimiterTests.class,
		WebSocketIntegrationTests.class,
		// route filter tests
		AddRequestHeaderWebFilterFactoryTests.class,
		AddRequestParameterWebFilterFactoryTests.class,
		HystrixWebFilterFactoryTests.class,
		RedirectToWebFilterFactoryTests.class,
		RemoveNonProxyHeadersWebFilterFactoryTests.class,
		RemoveRequestHeaderWebFilterFactoryTests.class,
		RewritePathWebFilterFactoryIntegrationTests.class,
		SecureHeadersWebFilterFactoryTests.class,
		SetPathWebFilterFactoryIntegrationTests.class,
		SetPathWebFilterFactoryTests.class,
		SetResponseWebFilterFactoryTests.class,
		SetStatusWebFilterFactoryTests.class,
		RewritePathWebFilterFactoryTests.class,
		// RoutePredicateFactory tests
		AfterRoutePredicateFactoryTests.class,
		BeforeRoutePredicateFactoryTests.class,
		BetweenRoutePredicateFactoryTests.class,
		HostRoutePredicateFactoryTests.class,
		MethodRoutePredicateFactoryTests.class,
		PathRoutePredicateFactoryTests.class,
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
