package org.springframework.cloud.gateway.handler;

import java.net.URI;
import java.util.Optional;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class WebClientRoutingWebHandler implements WebHandler {

	private final WebClient webClient;

	public WebClientRoutingWebHandler(WebClient webClient) {
		this.webClient = webClient;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		Optional<URI> requestUrl = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		ServerHttpRequest request = exchange.getRequest();
		ClientRequest<Void> clientRequest = ClientRequest
				.method(request.getMethod(), requestUrl.get())
				.headers(request.getHeaders())
				.body((r, context) -> r.writeWith(request.getBody()));

		return this.webClient.exchange(clientRequest).flatMap(clientResponse -> {
			// Defer committing the response until all route filters have run
			// Put client response as ServerWebExchange attribute and write response later WriteResponseFilter

			exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, clientResponse);

			ServerHttpResponse response = exchange.getResponse();
			// put headers and status so filters can modify the response
			response.getHeaders().putAll(clientResponse.headers().asHttpHeaders());
			response.setStatusCode(clientResponse.statusCode());
			return Mono.<Void>empty();
		}).next(); // TODO: is this correct?
	}
}
