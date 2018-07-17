package org.springframework.cloud.gateway.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import reactor.core.publisher.Mono;

public class GatewayMetricsFilter implements GlobalFilter, Ordered {

	private MeterRegistry meterRegistry;

	public GatewayMetricsFilter(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Override
	public int getOrder() {
		return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		long start = System.nanoTime();
		return chain.filter(exchange).then(Mono.defer(() -> {
			HttpStatus statusCode = exchange.getResponse().getStatusCode();
			boolean success = statusCode.is2xxSuccessful();
			Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
			Iterable<Tag> iterableTags = Arrays.asList(
					Tag.of("success", Boolean.toString(success)),
					Tag.of("httpStatus", statusCode.name()),
					Tag.of("routeId", route.getId()),
					Tag.of("routeUri", route.getUri().toString()));
			meterRegistry.timer("gateway.requests", iterableTags)
					.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
			return Mono.empty();
		}));
	}
}
