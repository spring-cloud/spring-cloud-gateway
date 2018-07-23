package org.springframework.cloud.gateway.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.util.Arrays;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
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
		Sample sample = Timer.start(meterRegistry);
		return chain.filter(exchange).then(Mono.fromRunnable(() -> {
			endTimerRespectingCommit(exchange, sample);
		})).doOnError(t -> { // needed for example when netty routing filter times out
			endTimerRespectingCommit(exchange, sample);
		}).then();
	}

	private void endTimerRespectingCommit(ServerWebExchange exchange, Sample sample) {

		ServerHttpResponse response = exchange.getResponse();
		if (response.isCommitted()) {
			endTimerInner(exchange, sample);
		}
		else {
			response.beforeCommit(() -> {
				endTimerInner(exchange, sample);
				return Mono.empty();
			});
		}

	}

	private void endTimerInner(ServerWebExchange exchange, Sample sample) {
		String outcome = "CUSTOM";
		String status = "CUSTOM";
		HttpStatus statusCode = exchange.getResponse().getStatusCode();
		if (statusCode != null) {
			outcome = statusCode.series().name();
			status = statusCode.name();
		}
		else { // a non standard HTTPS status could be used. Let's be defensive here
			if (exchange.getResponse() instanceof AbstractServerHttpResponse) {
				Integer statusInt = ((AbstractServerHttpResponse) exchange.getResponse())
						.getStatusCodeValue();
				if (statusInt != null) {
					status = String.valueOf(statusInt);
				}
				else {
					status = "NA";
				}
			}
		}
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		Iterable<Tag> iterableTags = Arrays.asList(Tag.of("outcome", outcome),
				Tag.of("status", status), Tag.of("routeId", route.getId()),
				Tag.of("routeUri", route.getUri().toString()));
		sample.stop(meterRegistry.timer("gateway.requests", iterableTags));
	}
}
