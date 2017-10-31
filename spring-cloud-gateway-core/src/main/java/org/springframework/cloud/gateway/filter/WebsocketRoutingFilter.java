package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

import reactor.core.publisher.Mono;

/**
 * @author Spencer Gibb
 */
public class WebsocketRoutingFilter implements GlobalFilter, Ordered {
	public static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

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
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || (!scheme.equals("ws") && !scheme.equals("wss"))) {
			return chain.filter(exchange);
		}
		setAlreadyRouted(exchange);

		return this.webSocketService.handleRequest(exchange,
				new ProxyWebSocketHandler(requestUrl, this.webSocketClient, exchange.getRequest().getHeaders()));
	}

	private static class ProxyWebSocketHandler implements WebSocketHandler {

		private final WebSocketClient client;
		private final URI url;
		private final HttpHeaders headers;
		private final List<String> subProtocols;

		public ProxyWebSocketHandler(URI url, WebSocketClient client, HttpHeaders headers) {
			this.client = client;
			this.url = url;
			this.headers = new HttpHeaders();//headers;
			//TODO: better strategy to filter these headers?
			headers.entrySet().forEach(header -> {
				if (!header.getKey().toLowerCase().startsWith("sec-websocket")
						&& !header.getKey().equalsIgnoreCase("upgrade")
						&& !header.getKey().equalsIgnoreCase("connection")) {
					this.headers.addAll(header.getKey(), header.getValue());
				}
			});
			List<String> protocols = headers.get(SEC_WEBSOCKET_PROTOCOL);
			if (protocols != null) {
				this.subProtocols = protocols;
			} else {
				this.subProtocols = Collections.emptyList();
			}
		}

		@Override
		public List<String> getSubProtocols() {
			return this.subProtocols;
		}

		@Override
		public Mono<Void> handle(WebSocketSession session) {
			// pass headers along so custom headers can be sent through
			return client.execute(url, this.headers, new WebSocketHandler() {
				@Override
				public Mono<Void> handle(WebSocketSession proxySession) {
					// Use retain() for Reactor Netty
					Mono<Void> proxySessionSend = proxySession
							.send(session.receive().doOnNext(WebSocketMessage::retain));
							// .log("proxySessionSend", Level.FINE);
					Mono<Void> serverSessionSend = session
							.send(proxySession.receive().doOnNext(WebSocketMessage::retain));
							// .log("sessionSend", Level.FINE);
					return Mono.when(proxySessionSend, serverSessionSend).then();
				}

				/**
				 * Copy subProtocols so they are available downstream.
				 * @return
				 */
				@Override
				public List<String> getSubProtocols() {
					return ProxyWebSocketHandler.this.subProtocols;
				}
			});
		}
	}
}
