/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.server.mvc.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Relays messages and close events between the client session and the backend session
 * of a single proxied WebSocket connection.
 *
 * <p>
 * The backend is connected before the client handshake completes, so messages the
 * backend sends in between are buffered until the client session is established.
 *
 * <p>
 * Text, binary and pong messages are relayed. Ping messages are not: the servlet
 * WebSocket API never delivers them to the application, and the container answers each
 * ping with a pong itself.
 */
class WebSocketProxyBridge {

	private static final Log log = LogFactory.getLog(WebSocketProxyBridge.class);

	private final int sendTimeLimit;

	private final int sendBufferSizeLimit;

	private final int maxMessageSize;

	private final Object lock = new Object();

	// set before the client handshake completes and read without the lock
	private volatile @Nullable WebSocketSession backendSession;

	private @Nullable WebSocketSession clientSession;

	private final List<WebSocketMessage<?>> pendingClientMessages = new ArrayList<>();

	private @Nullable CloseStatus pendingClientClose;

	private final BackendHandler backendHandler = new BackendHandler();

	private final ClientHandler clientHandler = new ClientHandler();

	WebSocketProxyBridge(int sendTimeLimit, int sendBufferSizeLimit, int maxMessageSize) {
		this.sendTimeLimit = sendTimeLimit;
		this.sendBufferSizeLimit = sendBufferSizeLimit;
		this.maxMessageSize = maxMessageSize;
	}

	/**
	 * Handler for the gateway's connection to the backend.
	 */
	WebSocketHandler backendHandler() {
		return this.backendHandler;
	}

	/**
	 * Handler for the client's connection to the gateway. Offers only the subprotocol
	 * the backend accepted.
	 */
	WebSocketHandler clientHandler() {
		return this.clientHandler;
	}

	/**
	 * Closes the backend connection when the client handshake does not complete.
	 */
	void abort() {
		close(this.backendSession, CloseStatus.GOING_AWAY);
	}

	private WebSocketSession decorate(WebSocketSession session) {
		session.setTextMessageSizeLimit(this.maxMessageSize);
		session.setBinaryMessageSizeLimit(this.maxMessageSize);
		return new ConcurrentWebSocketSessionDecorator(session, this.sendTimeLimit, this.sendBufferSizeLimit);
	}

	private static void close(@Nullable WebSocketSession session, CloseStatus status) {
		if (session == null || !session.isOpen()) {
			return;
		}
		try {
			session.close(sendable(status));
		}
		catch (IOException ex) {
			if (log.isDebugEnabled()) {
				log.debug("Failed to close WebSocket session " + session.getId(), ex);
			}
		}
	}

	/**
	 * Some close codes are reserved for reporting a missing or abnormal close and must
	 * not be sent in a close frame.
	 */
	static CloseStatus sendable(CloseStatus status) {
		return switch (status.getCode()) {
			case 1005 -> CloseStatus.NORMAL;
			case 1006, 1015 -> CloseStatus.GOING_AWAY;
			default -> status;
		};
	}

	private class BackendHandler implements WebSocketHandler {

		@Override
		public void afterConnectionEstablished(WebSocketSession session) {
			backendSession = decorate(session);
		}

		@Override
		public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws IOException {
			// sending while holding the lock keeps buffered messages in order
			synchronized (lock) {
				if (clientSession == null) {
					pendingClientMessages.add(message);
				}
				else {
					clientSession.sendMessage(message);
				}
			}
		}

		@Override
		public void handleTransportError(WebSocketSession session, Throwable exception) {
			if (log.isDebugEnabled()) {
				log.debug("Transport error on backend WebSocket session " + session.getId(), exception);
			}
		}

		@Override
		public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
			WebSocketSession client;
			synchronized (lock) {
				if (clientSession == null) {
					pendingClientClose = closeStatus;
					return;
				}
				client = clientSession;
			}
			close(client, closeStatus);
		}

		@Override
		public boolean supportsPartialMessages() {
			return false;
		}

	}

	private class ClientHandler implements WebSocketHandler, SubProtocolCapable {

		@Override
		public List<String> getSubProtocols() {
			WebSocketSession backend = backendSession;
			String protocol = (backend != null) ? backend.getAcceptedProtocol() : null;
			if (!StringUtils.hasText(protocol)) {
				return Collections.emptyList();
			}
			// AbstractHandshakeHandler compares against lower case values
			return List.of(protocol.toLowerCase(Locale.ROOT));
		}

		@Override
		public void afterConnectionEstablished(WebSocketSession session) throws IOException {
			WebSocketSession client = decorate(session);
			CloseStatus closeStatus;
			synchronized (lock) {
				for (WebSocketMessage<?> message : pendingClientMessages) {
					client.sendMessage(message);
				}
				pendingClientMessages.clear();
				clientSession = client;
				closeStatus = pendingClientClose;
			}
			if (closeStatus != null) {
				close(client, closeStatus);
			}
		}

		@Override
		public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws IOException {
			WebSocketSession backend = backendSession;
			if (backend != null) {
				backend.sendMessage(message);
			}
		}

		@Override
		public void handleTransportError(WebSocketSession session, Throwable exception) {
			if (log.isDebugEnabled()) {
				log.debug("Transport error on client WebSocket session " + session.getId(), exception);
			}
		}

		@Override
		public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
			close(backendSession, closeStatus);
		}

		@Override
		public boolean supportsPartialMessages() {
			return false;
		}

	}

}
