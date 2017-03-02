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

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.springframework.cloud.gateway.filter.route.AddRequestHeaderRouteFilterIntegrationTests;
import org.springframework.cloud.gateway.filter.route.AddRequestParameterRouteFilterIntegrationTests;
import org.springframework.cloud.gateway.filter.route.HystrixRouteFilterIntegrationTests;
import org.springframework.cloud.gateway.filter.route.RedirectToRouteFilterIntegrationTests;
import org.springframework.cloud.gateway.filter.route.RemoveNonProxyHeadersRouteFilterIntegrationTests;
import org.springframework.cloud.gateway.filter.route.RemoveRequestHeaderRouteFilterIntegrationTests;
import org.springframework.cloud.gateway.filter.route.RewritePathRouteFilterTests;
import org.springframework.cloud.gateway.filter.route.SecureHeadersRouteFilterIntegrationTests;
import org.springframework.cloud.gateway.filter.route.SetPathRouteFilterIntegrationTests;
import org.springframework.cloud.gateway.filter.route.SetPathRouteFilterTests;
import org.springframework.cloud.gateway.filter.route.SetResponseRouteFilterIntegrationTests;
import org.springframework.cloud.gateway.filter.route.SetStatusRouteFilterIntegrationTests;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateTests;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateTests;
import org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateTests;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateIntegrationTests;
import org.springframework.cloud.gateway.handler.predicate.UrlRoutePredicateIntegrationTests;

/**
 * @author Spencer Gibb
 */
@Ignore
@RunWith(Suite.class)
@SuiteClasses({GatewayIntegrationTests.class,
		FormIntegrationTests.class,
		// route filter tests
		AddRequestHeaderRouteFilterIntegrationTests.class,
		AddRequestParameterRouteFilterIntegrationTests.class,
		HystrixRouteFilterIntegrationTests.class,
		RedirectToRouteFilterIntegrationTests.class,
		RemoveNonProxyHeadersRouteFilterIntegrationTests.class,
		RemoveRequestHeaderRouteFilterIntegrationTests.class,
		SecureHeadersRouteFilterIntegrationTests.class,
		SetPathRouteFilterIntegrationTests.class,
		SetPathRouteFilterTests.class,
		SetResponseRouteFilterIntegrationTests.class,
		SetStatusRouteFilterIntegrationTests.class,
		RewritePathRouteFilterTests.class,
		// route predicate tests
		AfterRoutePredicateTests.class,
		BeforeRoutePredicateTests.class,
		BetweenRoutePredicateTests.class,
		HostRoutePredicateIntegrationTests.class,
		UrlRoutePredicateIntegrationTests.class,
})
public class AdhocTestSuite {
}
