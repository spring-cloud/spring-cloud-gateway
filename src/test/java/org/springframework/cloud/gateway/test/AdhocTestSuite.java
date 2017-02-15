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
