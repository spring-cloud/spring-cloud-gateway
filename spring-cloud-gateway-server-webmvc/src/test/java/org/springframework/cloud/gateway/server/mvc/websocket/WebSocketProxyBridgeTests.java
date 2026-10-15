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

import java.nio.ByteBuffer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link WebSocketProxyBridge} with mocked sessions.
 */
public class WebSocketProxyBridgeTests {

	private static final int MAX_MESSAGE_SIZE = 1024;

	private WebSocketProxyBridge bridge;

	private WebSocketSession backend;

	private WebSocketSession client;

	@BeforeEach
	void setup() {
		bridge = new WebSocketProxyBridge(1000, 1024 * 1024, MAX_MESSAGE_SIZE);
		backend = openSession("backend");
		client = openSession("client");
	}

	private static WebSocketSession openSession(String id) {
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getId()).thenReturn(id);
		when(session.isOpen()).thenReturn(true);
		return session;
	}

	private void connectBoth() throws Exception {
		bridge.backendHandler().afterConnectionEstablished(backend);
		bridge.clientHandler().afterConnectionEstablished(client);
	}

	@Test
	void messagesAreRelayedBothWays() throws Exception {
		connectBoth();
		TextMessage fromClient = new TextMessage("from client");
		BinaryMessage fromBackend = new BinaryMessage(new byte[] { 1, 2, 3 });

		bridge.clientHandler().handleMessage(client, fromClient);
		bridge.backendHandler().handleMessage(backend, fromBackend);

		verify(backend).sendMessage(fromClient);
		verify(client).sendMessage(fromBackend);
	}

	@Test
	void pongsAreRelayedBothWays() throws Exception {
		connectBoth();
		PongMessage fromClient = new PongMessage(ByteBuffer.wrap(new byte[] { 1 }));
		PongMessage fromBackend = new PongMessage(ByteBuffer.wrap(new byte[] { 2 }));

		bridge.clientHandler().handleMessage(client, fromClient);
		bridge.backendHandler().handleMessage(backend, fromBackend);

		verify(backend).sendMessage(fromClient);
		verify(client).sendMessage(fromBackend);
	}

	@Test
	void backendMessagesBeforeClientConnectsAreBufferedInOrder() throws Exception {
		bridge.backendHandler().afterConnectionEstablished(backend);
		TextMessage first = new TextMessage("first");
		TextMessage second = new TextMessage("second");
		TextMessage third = new TextMessage("third");
		bridge.backendHandler().handleMessage(backend, first);
		bridge.backendHandler().handleMessage(backend, second);

		bridge.clientHandler().afterConnectionEstablished(client);
		bridge.backendHandler().handleMessage(backend, third);

		InOrder inOrder = inOrder(client);
		inOrder.verify(client).sendMessage(first);
		inOrder.verify(client).sendMessage(second);
		inOrder.verify(client).sendMessage(third);
	}

	@Test
	void backendCloseBeforeClientConnectsIsAppliedOnceClientConnects() throws Exception {
		bridge.backendHandler().afterConnectionEstablished(backend);
		bridge.backendHandler().handleMessage(backend, new TextMessage("bye"));
		bridge.backendHandler().afterConnectionClosed(backend, new CloseStatus(4999, "backend-close"));
		verify(client, never()).close(new CloseStatus(4999, "backend-close"));

		bridge.clientHandler().afterConnectionEstablished(client);

		InOrder inOrder = inOrder(client);
		inOrder.verify(client).sendMessage(new TextMessage("bye"));
		inOrder.verify(client).close(new CloseStatus(4999, "backend-close"));
	}

	@Test
	void backendCloseIsRelayedToClient() throws Exception {
		connectBoth();

		bridge.backendHandler().afterConnectionClosed(backend, new CloseStatus(4999, "backend-close"));

		verify(client).close(new CloseStatus(4999, "backend-close"));
	}

	@Test
	void clientCloseIsRelayedToBackend() throws Exception {
		connectBoth();

		bridge.clientHandler().afterConnectionClosed(client, new CloseStatus(4998, "client-close"));

		verify(backend).close(new CloseStatus(4998, "client-close"));
	}

	@Test
	void closeIsNotSentToSessionThatIsAlreadyClosed() throws Exception {
		connectBoth();
		when(backend.isOpen()).thenReturn(false);

		bridge.clientHandler().afterConnectionClosed(client, CloseStatus.NORMAL);

		verify(backend, never()).close(CloseStatus.NORMAL);
	}

	@Test
	void reservedCloseCodesAreReplacedBeforeSending() throws Exception {
		connectBoth();

		bridge.clientHandler().afterConnectionClosed(client, CloseStatus.NO_STATUS_CODE);

		verify(backend).close(CloseStatus.NORMAL);
		assertThat(WebSocketProxyBridge.sendable(CloseStatus.NO_CLOSE_FRAME)).isEqualTo(CloseStatus.GOING_AWAY);
		assertThat(WebSocketProxyBridge.sendable(CloseStatus.TLS_HANDSHAKE_FAILURE))
			.isEqualTo(CloseStatus.GOING_AWAY);
		assertThat(WebSocketProxyBridge.sendable(CloseStatus.POLICY_VIOLATION))
			.isEqualTo(CloseStatus.POLICY_VIOLATION);
	}

	@Test
	void abortClosesBackend() throws Exception {
		bridge.backendHandler().afterConnectionEstablished(backend);

		bridge.abort();

		verify(backend).close(CloseStatus.GOING_AWAY);
	}

	@Test
	void abortBeforeBackendConnectsDoesNothing() {
		bridge.abort();

		verify(backend, never()).isOpen();
	}

	@Test
	void clientIsOfferedTheSubProtocolTheBackendAccepted() throws Exception {
		SubProtocolCapable clientHandler = (SubProtocolCapable) bridge.clientHandler();
		assertThat(clientHandler.getSubProtocols()).isEmpty();

		when(backend.getAcceptedProtocol()).thenReturn("Echo-V2");
		bridge.backendHandler().afterConnectionEstablished(backend);

		assertThat(clientHandler.getSubProtocols()).containsExactly("echo-v2");
	}

	@Test
	void noSubProtocolOfferedWhenBackendAcceptedNone() throws Exception {
		when(backend.getAcceptedProtocol()).thenReturn("");
		bridge.backendHandler().afterConnectionEstablished(backend);

		assertThat(((SubProtocolCapable) bridge.clientHandler()).getSubProtocols()).isEmpty();
	}

	@Test
	void messageSizeLimitIsAppliedToBothSessions() throws Exception {
		connectBoth();

		verify(backend).setTextMessageSizeLimit(MAX_MESSAGE_SIZE);
		verify(backend).setBinaryMessageSizeLimit(MAX_MESSAGE_SIZE);
		verify(client).setTextMessageSizeLimit(MAX_MESSAGE_SIZE);
		verify(client).setBinaryMessageSizeLimit(MAX_MESSAGE_SIZE);
	}

}
