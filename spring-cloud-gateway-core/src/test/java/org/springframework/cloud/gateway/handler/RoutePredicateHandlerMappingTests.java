package org.springframework.cloud.gateway.handler;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.hamcrest.Matchers.containsString;

/**
 * @author Simon Baslé
 */
public class RoutePredicateHandlerMappingTests {

	@Rule
	public OutputCapture outputCapture = new OutputCapture();

	@Test
	public void lookupRouteFromSyncPredicates() {
		Route routeFalse = Route.async()
				.id("routeFalse")
				.uri("http://localhost")
				.predicate(swe -> false)
				.build();
		Route routeFail = Route.async()
				.id("routeFail")
				.uri("http://localhost")
				.predicate(swe -> { throw new IllegalStateException("boom"); })
				.build();
		Route routeTrue = Route.async()
				.id("routeTrue")
				.uri("http://localhost")
				.predicate(swe -> true)
				.build();
		RouteLocator routeLocator =
				() -> Flux.just(routeFalse, routeFail, routeTrue).hide();
		RoutePredicateHandlerMapping mapping =
				new RoutePredicateHandlerMapping(null, routeLocator, new GlobalCorsProperties(), new MockEnvironment());

		final Mono<Route> routeMono =
				mapping.lookupRoute(Mockito.mock(ServerWebExchange.class));

		StepVerifier.create(routeMono.map(Route::getId))
				.expectNext("routeTrue")
				.verifyComplete();

		outputCapture.expect(containsString("Error applying predicate for route: routeFail"));
		outputCapture.expect(containsString("java.lang.IllegalStateException: boom"));
	}

	@Test
	public void lookupRouteFromAsyncPredicates() {
		Route routeFalse = Route.async()
				.id("routeFalse")
				.uri("http://localhost")
				.asyncPredicate(swe -> Mono.just(false))
				.build();
		Route routeError = Route.async()
				.id("routeError")
				.uri("http://localhost")
				.asyncPredicate(swe -> Mono.error(new IllegalStateException("boom1")))
				.build();
		Route routeFail = Route.async()
				.id("routeFail")
				.uri("http://localhost")
				.asyncPredicate(swe -> { throw new IllegalStateException("boom2"); })
				.build();
		Route routeTrue = Route.async()
				.id("routeTrue")
				.uri("http://localhost")
				.asyncPredicate(swe -> Mono.just(true))
				.build();
		RouteLocator routeLocator =
				() -> Flux.just(routeFalse, routeError, routeFail, routeTrue).hide();
		RoutePredicateHandlerMapping mapping =
				new RoutePredicateHandlerMapping(null, routeLocator, new GlobalCorsProperties(), new MockEnvironment());

		final Mono<Route> routeMono =
				mapping.lookupRoute(Mockito.mock(ServerWebExchange.class));

		StepVerifier.create(routeMono.map(Route::getId))
				.expectNext("routeTrue")
				.verifyComplete();

		outputCapture.expect(containsString("Error applying predicate for route: routeError"));
		outputCapture.expect(containsString("java.lang.IllegalStateException: boom1"));

		outputCapture.expect(containsString("Error applying predicate for route: routeFail"));
		outputCapture.expect(containsString("java.lang.IllegalStateException: boom2"));
	}
}