package org.springframework.cloud.gateway;

import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
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
public class GatewayController {

	private final WebClient webClient;
	private final DataBufferFactory bufferFactory;

	public GatewayController() {
		webClient = WebClient.builder(new ReactorClientHttpConnector()).build();
		bufferFactory = new DefaultDataBufferFactory();
	}

	@GetMapping(path = "/")
	public Flux<Void> home(ServerWebExchange exchange) {
		ClientRequest<Void> request = ClientRequest
				.GET("http://httpbin.org/get")
				.accept(MediaType.APPLICATION_JSON)
				.build();

		return this.webClient.exchange(request).flatMap(clientResponse -> {
			ServerHttpResponse response = exchange.getResponse();
			response.getHeaders().setContentType(clientResponse.headers().contentType().get());
			response.setStatusCode(clientResponse.statusCode());
			return response.writeWith(clientResponse.body((inputMessage, context) -> inputMessage.getBody()));
			// return response;
		});
	}
}
