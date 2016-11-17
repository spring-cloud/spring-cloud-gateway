package org.springframework.cloud.gateway;

import java.util.Optional;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.reactive.ClientRequest;
import org.springframework.web.client.reactive.WebClient;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 */
@RestController
@SuppressWarnings("unused")
public class GatewayController {

	private final WebClient webClient;

	public GatewayController(WebClient webClient) {
		this.webClient = webClient;
	}

	//TODO: plugin to request mappings
	@GetMapping(path = "/")
	public Flux<Void> home(ServerWebExchange exchange) {
		Optional<String> requestUrl = exchange.getAttribute("requestUrl");
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
		});
	}
}
