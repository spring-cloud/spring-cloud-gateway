package org.springframework.cloud.gateway;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.client.reactive.ClientRequest;
import org.springframework.web.client.reactive.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;

/**
 * @author Spencer Gibb
 */
public class GatewayWebHandler implements WebHandler {

	private final GatewayProperties properties;
	private final WebClient webClient;

	public GatewayWebHandler(GatewayProperties properties, WebClient webClient) {
		this.properties = properties;
		this.webClient = webClient;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		//TODO: move to constant
		Optional<URI> requestUrl = exchange.getAttribute("requestUrl");
		ServerHttpRequest request = exchange.getRequest();
		ClientRequest<Void> clientRequest = ClientRequest
				.method(request.getMethod(), requestUrl.get())
				.headers(request.getHeaders())
				.build();

		return this.webClient.exchange(clientRequest).flatMap(clientResponse -> {
			ServerHttpResponse response = exchange.getResponse();
			response.getHeaders().putAll(clientResponse.headers().asHttpHeaders());
			response.setStatusCode(clientResponse.statusCode());
			Flux<DataBuffer> body = clientResponse.body((inputMessage, context) -> inputMessage.getBody());
			return response.writeWith(body);
		}).next(); // TODO: is this correct?
	}
}
