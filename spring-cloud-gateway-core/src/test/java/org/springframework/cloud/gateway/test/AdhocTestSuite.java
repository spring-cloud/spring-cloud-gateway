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
import org.springframework.cloud.gateway.handler.predicate.AfterRequestPredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.BeforeRequestPredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.BetweenRequestPredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateIntegrationTests;
import org.springframework.cloud.gateway.handler.predicate.MethodRequestPredicateFactoryTests;
import org.springframework.cloud.gateway.handler.predicate.PathRequestPredicateFactoryTests;

/**
 * @author Spencer Gibb
 */
@Ignore
@RunWith(Suite.class)
@SuiteClasses({GatewayIntegrationTests.class,
		FormIntegrationTests.class,
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
		// RequestPredicateFactory tests
		AfterRequestPredicateFactoryTests.class,
		BeforeRequestPredicateFactoryTests.class,
		BetweenRequestPredicateFactoryTests.class,
		HostRoutePredicateIntegrationTests.class,
		MethodRequestPredicateFactoryTests.class,
		PathRequestPredicateFactoryTests.class,
})
public class AdhocTestSuite {
}
