package org.springframework.cloud.gateway.fu;


import org.springframework.boot.SpringApplication;

import static org.springframework.cloud.gateway.fu.filter.FilterFunctions.addRequestHeader;
import static org.springframework.cloud.gateway.fu.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.fu.predicate.GatewayPredicates.host;
import static org.springframework.fu.jafu.Jafu.application;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;


public class GatewayFuTests {

	private static SpringApplication app = application(app -> {
		app.server(server -> server.router(router -> {
			router.GET("/get",
					http("http://httpbin.org:80"))
					.filter(addRequestHeader("X-Foo", "Bar"));
			router.add(route(host("*.header.org"),
					http("http://httpbin.org:80")));
		}));
	});

	public static void main(String[] args) {
		app.run(args);
	}
}
