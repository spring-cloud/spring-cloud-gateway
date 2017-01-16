package org.springframework.cloud.gateway.filter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.RESPONSE_COMMITTED_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.getAttribute;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class WriteResponseFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(WriteResponseFilter.class);

	public static final int WRITE_RESPONSE_FILTER_ORDER = -1;

	@Override
	public int getOrder() {
		return WRITE_RESPONSE_FILTER_ORDER;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		// NOTICE: nothing in "pre" filter stage as CLIENT_RESPONSE_ATTR is not added
		// until the WebHandler is run
		return chain.filter(exchange).then(() -> {
			ClientResponse clientResponse = getAttribute(exchange, CLIENT_RESPONSE_ATTR, ClientResponse.class);
			if (clientResponse == null) {
				return Mono.empty();
			}
			log.trace("WriteResponseFilter start");
			ServerHttpResponse response = exchange.getResponse();

			//make other filters aware that the response has been committed
			response.beforeCommit(() -> {
				exchange.getAttributes().put(RESPONSE_COMMITTED_ATTR, true);
				return Mono.empty();
			});

			Flux<DataBuffer> body = clientResponse.body((inputMessage, context) -> inputMessage.getBody());
			return response.writeWith(body);
		});
	}

}
