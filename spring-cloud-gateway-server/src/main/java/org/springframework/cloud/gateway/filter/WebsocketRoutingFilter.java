/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter.filterRequest;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.containsEncodedParts;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

/**
 * @author Spencer Gibb
 * @author Nikita Konev
 */
public class WebsocketRoutingFilter implements GlobalFilter, Ordered {

	/**
	 * Sec-Websocket protocol.
	 */
	public static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

	private static final Log log = LogFactory.getLog(WebsocketRoutingFilter.class);

	private final WebSocketClient webSocketClient;

	private final WebSocketService webSocketService;

	private final ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider;

	// do not use this headersFilters directly, use getHeadersFilters() instead.
	private volatile List<HttpHeadersFilter> headersFilters;

	public WebsocketRoutingFilter(WebSocketClient webSocketClient, WebSocketService webSocketService,
			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider) {
		this.webSocketClient = webSocketClient;
		this.webSocketService = webSocketService;
		this.headersFiltersProvider = headersFiltersProvider;
	}

	/* for testing */
	static String convertHttpToWs(String scheme) {
		scheme = scheme.toLowerCase();
		return "http".equals(scheme) ? "ws" : "https".equals(scheme) ? "wss" : scheme;
	}

	@Override
	public int getOrder() {
		// Before NettyRoutingFilter since this routes certain http requests
		return Ordered.LOWEST_PRECEDENCE - 1;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		changeSchemeIfIsWebSocketUpgrade(exchange);

		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		String scheme = requestUrl.getScheme();

		if (isAlreadyRouted(exchange) || (!"ws".equals(scheme) && !"wss".equals(scheme))) {
			return chain.filter(exchange);
		}
		setAlreadyRouted(exchange);

		HttpHeaders headers = exchange.getRequest().getHeaders();
		HttpHeaders filtered = filterRequest(getHeadersFilters(), exchange);

		List<String> protocols = getProtocols(headers);

		return this.webSocketService.handleRequest(exchange,
				new ProxyWebSocketHandler(requestUrl, this.webSocketClient, filtered, protocols));
	}

	/* for testing */ List<String> getProtocols(HttpHeaders headers) {
		List<String> protocols = headers.get(SEC_WEBSOCKET_PROTOCOL);
		if (protocols != null) {
			ArrayList<String> updatedProtocols = new ArrayList<>();
			for (int i = 0; i < protocols.size(); i++) {
				String protocol = protocols.get(i);
				updatedProtocols.addAll(Arrays.asList(StringUtils.tokenizeToStringArray(protocol, ",")));
			}
			protocols = updatedProtocols;
		}
		return protocols;
	}

	/* for testing */ List<HttpHeadersFilter> getHeadersFilters() {
		if (this.headersFilters == null) {
			this.headersFilters = this.headersFiltersProvider.getIfAvailable(ArrayList::new);

			// remove host header unless specifically asked not to
			headersFilters.add((headers, exchange) -> {
				HttpHeaders filtered = new HttpHeaders();
				filtered.addAll(headers);
				filtered.remove(HttpHeaders.HOST);
				boolean preserveHost = exchange.getAttributeOrDefault(PRESERVE_HOST_HEADER_ATTRIBUTE, false);
				if (preserveHost) {
					String host = exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST);
					filtered.add(HttpHeaders.HOST, host);
				}
				return filtered;
			});

			headersFilters.add((headers, exchange) -> {
				HttpHeaders filtered = new HttpHeaders();
				for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
					if (!entry.getKey().toLowerCase().startsWith("sec-websocket")) {
						filtered.addAll(entry.getKey(), entry.getValue());
					}
				}
				return filtered;
			});
		}

		return this.headersFilters;
	}

	static void changeSchemeIfIsWebSocketUpgrade(ServerWebExchange exchange) {
		// Check the Upgrade
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		String scheme = requestUrl.getScheme().toLowerCase();
		String upgrade = exchange.getRequest().getHeaders().getUpgrade();
		// change the scheme if the socket client send a "http" or "https"
		if ("WebSocket".equalsIgnoreCase(upgrade) && ("http".equals(scheme) || "https".equals(scheme))) {
			String wsScheme = convertHttpToWs(scheme);
			boolean encoded = containsEncodedParts(requestUrl);
			URI wsRequestUrl = UriComponentsBuilder.fromUri(requestUrl).scheme(wsScheme).build(encoded).toUri();
			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, wsRequestUrl);
			if (log.isTraceEnabled()) {
				log.trace("changeSchemeTo:[" + wsRequestUrl + "]");
			}
		}
	}

	private static class ProxyWebSocketHandler implements WebSocketHandler {

		private final WebSocketClient client;

		private final URI url;

		private final HttpHeaders headers;

		private final List<String> subProtocols;

		ProxyWebSocketHandler(URI url, WebSocketClient client, HttpHeaders headers, List<String> protocols) {
			this.client = client;
			this.url = url;
			this.headers = headers;
			if (protocols != null) {
				this.subProtocols = protocols;
			}
			else {
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
					Mono<Void> serverClose = proxySession.closeStatus().filter(__ -> session.isOpen())
							.flatMap(session::close);
					Mono<Void> proxyClose = session.closeStatus().filter(__ -> proxySession.isOpen())
							.flatMap(proxySession::close);
					// Use retain() for Reactor Netty
					Mono<Void> proxySessionSend = proxySession
							.send(session.receive().doOnNext(WebSocketMessage::retain).doOnNext(webSocketMessage -> {
								if (log.isTraceEnabled()) {
									log.trace("proxySession(send from client): " + proxySession.getId()
											+ ", corresponding session:" + session.getId() + ", packet: "
											+ webSocketMessage.getPayloadAsText());
								}
							}));
					// .log("proxySessionSend", Level.FINE);
					Mono<Void> serverSessionSend = session.send(
							proxySession.receive().doOnNext(WebSocketMessage::retain).doOnNext(webSocketMessage -> {
								if (log.isTraceEnabled()) {
									log.trace("session(send from backend): " + session.getId()
											+ ", corresponding proxySession:" + proxySession.getId() + " packet: "
											+ webSocketMessage.getPayloadAsText());
								}
							}));
					// .log("sessionSend", Level.FINE);
					// Ensure closeStatus from one propagates to the other
					Mono.when(serverClose, proxyClose).subscribe();
					// Complete when both sessions are done
					return Mono.zip(proxySessionSend, serverSessionSend).then();
				}

				/**
				 * Copy subProtocols so they are available downstream.
				 * @return available subProtocols.
				 */
				@Override
				public List<String> getSubProtocols() {
					return ProxyWebSocketHandler.this.subProtocols;
				}
			});
		}

	}

}
