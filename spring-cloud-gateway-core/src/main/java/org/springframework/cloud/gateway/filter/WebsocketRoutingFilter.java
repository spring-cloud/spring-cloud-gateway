package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.logging.Level;

import org.springframework.core.Ordered;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class WebsocketRoutingFilter implements GlobalFilter, Ordered {
	private final WebSocketClient webSocketClient;
	private final WebSocketService webSocketService;

	public WebsocketRoutingFilter(WebSocketClient webSocketClient) {
		this(webSocketClient, new HandshakeWebSocketService());
	}

	public WebsocketRoutingFilter(WebSocketClient webSocketClient,
			WebSocketService webSocketService) {
		this.webSocketClient = webSocketClient;
		this.webSocketService = webSocketService;
	}

	@Override
	public int getOrder() {
		return 2000000;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		String scheme = requestUrl.getScheme();
		if (!scheme.equals("ws") && !scheme.equals("wss")) {
			return chain.filter(exchange);
		}

		return this.webSocketService.handleRequest(exchange, new ProxyWebSocketHandler(requestUrl, this.webSocketClient));
	}

	private static class ProxyWebSocketHandler implements WebSocketHandler {

		private final WebSocketClient client;
		private final URI url;

		public ProxyWebSocketHandler(URI url, WebSocketClient client) {
			this.client = client;
			this.url = url;
		}

		@Override
		public Mono<Void> handle(WebSocketSession session) {
			return client.execute(url, proxySession -> {
				// Use retain() for Reactor Netty
				Mono<Void> proxySessionSend = proxySession
						.send(session.receive().doOnNext(WebSocketMessage::retain))
						.log("proxySessionSend", Level.FINE);
				Mono<Void> serverSessionSend = session
						.send(proxySession.receive().doOnNext(WebSocketMessage::retain))
						.log("sessionSend", Level.FINE);
				return Mono.when(proxySessionSend, serverSessionSend).then();
			});
		}
	}
}
