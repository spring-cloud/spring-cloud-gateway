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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.handler.WebSocketHandlerFunction;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.server.HandshakeFailureException;
import org.springframework.web.socket.server.HandshakeHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.HandshakeInterceptorChain;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@link WebSocketHandlerFunction} that proxies a WebSocket connection to the URL
 * resolved for the route.
 *
 * <p>
 * The handshake runs the configured {@link HandshakeInterceptor}s (by default an origin
 * check), then connects to the backend, and only then completes the client handshake
 * with the subprotocol the backend accepted. If the backend cannot be reached the client
 * receives {@code 502 Bad Gateway}.
 */
public class WebSocketProxyHandlerFunction implements WebSocketHandlerFunction {

	private static final Log log = LogFactory.getLog(WebSocketProxyHandlerFunction.class);

	private static final String SEC_WEBSOCKET_PREFIX = "sec-websocket-";

	private final WebSocketClient webSocketClient;

	private final HandshakeHandler handshakeHandler;

	private final List<HandshakeInterceptor> handshakeInterceptors;

	private final ObjectProvider<RequestHttpHeadersFilter> requestHttpHeadersFiltersProvider;

	private volatile @Nullable List<RequestHttpHeadersFilter> requestHttpHeadersFilters;

	private final WebSocketProxyProperties properties;

	public WebSocketProxyHandlerFunction(WebSocketClient webSocketClient, HandshakeHandler handshakeHandler,
			List<HandshakeInterceptor> handshakeInterceptors,
			ObjectProvider<RequestHttpHeadersFilter> requestHttpHeadersFilters, WebSocketProxyProperties properties) {
		this.webSocketClient = webSocketClient;
		this.handshakeHandler = handshakeHandler;
		this.handshakeInterceptors = List.copyOf(handshakeInterceptors);
		this.requestHttpHeadersFiltersProvider = requestHttpHeadersFilters;
		this.properties = properties;
	}

	@Override
	public ServerResponse handle(ServerRequest request) {
		URI routeUri = request.attribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR)
			.map(URI.class::cast)
			.orElseThrow(() -> new IllegalStateException("No routeUri resolved"));
		MultiValueMap<String, String> params = MvcUtils.encodeQueryParams(request.params());
		// @formatter:off
		URI url = UriComponentsBuilder.fromUri(request.uri())
				.scheme(toWebSocketScheme(routeUri.getScheme()))
				.host(routeUri.getHost())
				.port(routeUri.getPort())
				.replaceQueryParams(params)
				.build(true)
				.toUri();
		// @formatter:on
		return new WebSocketProxyServerResponse(url, backendHeaders(request), requestedProtocols(request));
	}

	static String toWebSocketScheme(String scheme) {
		return switch (scheme.toLowerCase(Locale.ROOT)) {
			case "http", "ws" -> "ws";
			case "https", "wss" -> "wss";
			default -> throw new IllegalArgumentException("Unsupported scheme for WebSocket proxying: " + scheme);
		};
	}

	private HttpHeaders backendHeaders(ServerRequest request) {
		HttpHeaders headers = request.headers().asHttpHeaders();
		for (RequestHttpHeadersFilter filter : getRequestHttpHeadersFilters()) {
			headers = filter.apply(headers, request);
		}
		// the WebSocket client negotiates its own handshake with the backend and always
		// sends the backend host
		HttpHeaders filtered = new HttpHeaders();
		headers.forEach((name, values) -> {
			String lowerName = name.toLowerCase(Locale.ROOT);
			if (!lowerName.startsWith(SEC_WEBSOCKET_PREFIX) && !HttpHeaders.HOST.equalsIgnoreCase(name)) {
				filtered.addAll(name, values);
			}
		});
		return filtered;
	}

	private List<RequestHttpHeadersFilter> getRequestHttpHeadersFilters() {
		List<RequestHttpHeadersFilter> filters = this.requestHttpHeadersFilters;
		if (filters == null) {
			filters = this.requestHttpHeadersFiltersProvider.orderedStream().toList();
			this.requestHttpHeadersFilters = filters;
		}
		return filters;
	}

	static List<String> requestedProtocols(ServerRequest request) {
		List<String> protocols = new ArrayList<>();
		for (String value : request.headers().header(WebSocketHttpHeaders.SEC_WEBSOCKET_PROTOCOL)) {
			for (String protocol : StringUtils.tokenizeToStringArray(value, ",")) {
				protocols.add(protocol);
			}
		}
		return protocols;
	}

	private WebSocketSession connect(WebSocketProxyBridge bridge, URI url, HttpHeaders headers,
			List<String> protocols) throws Exception {
		WebSocketHttpHeaders webSocketHeaders = new WebSocketHttpHeaders(headers);
		if (!protocols.isEmpty()) {
			webSocketHeaders.setSecWebSocketProtocol(protocols);
		}
		CompletableFuture<WebSocketSession> future = this.webSocketClient.execute(bridge.backendHandler(),
				webSocketHeaders, url);
		try {
			return future.get(this.properties.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			future.cancel(true);
			throw ex;
		}
		catch (ExecutionException ex) {
			throw (ex.getCause() instanceof Exception cause) ? cause : ex;
		}
	}

	private WebSocketProxyBridge newBridge() {
		return new WebSocketProxyBridge((int) this.properties.getSendTimeLimit().toMillis(),
				(int) this.properties.getSendBufferSizeLimit().toBytes(),
				(int) this.properties.getMaxMessageSize().toBytes());
	}

	private final class WebSocketProxyServerResponse implements ServerResponse {

		private final URI url;

		private final HttpHeaders backendHeaders;

		private final List<String> protocols;

		private final HttpHeaders headers = new HttpHeaders();

		private final MultiValueMap<String, Cookie> cookies = new LinkedMultiValueMap<>();

		private WebSocketProxyServerResponse(URI url, HttpHeaders backendHeaders, List<String> protocols) {
			this.url = url;
			this.backendHeaders = backendHeaders;
			this.protocols = protocols;
		}

		@Override
		public HttpStatusCode statusCode() {
			return HttpStatus.SWITCHING_PROTOCOLS;
		}

		@Override
		public HttpHeaders headers() {
			return this.headers;
		}

		@Override
		public MultiValueMap<String, Cookie> cookies() {
			return this.cookies;
		}

		@Override
		public @Nullable ModelAndView writeTo(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
				Context context) {
			// headers and cookies added by after filters go on the handshake response
			this.headers.forEach((name, values) -> values.forEach(value -> servletResponse.addHeader(name, value)));
			this.cookies.values().forEach(values -> values.forEach(servletResponse::addCookie));

			ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
			ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
			WebSocketProxyBridge bridge = newBridge();
			HandshakeInterceptorChain chain = new HandshakeInterceptorChain(handshakeInterceptors,
					bridge.clientHandler());
			Map<String, Object> attributes = new HashMap<>();
			try {
				if (!chain.applyBeforeHandshake(request, response, attributes)) {
					return null;
				}
				try {
					connect(bridge, this.url, this.backendHeaders, this.protocols);
				}
				catch (Exception ex) {
					if (ex instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
					if (log.isDebugEnabled()) {
						log.debug("Failed to connect to WebSocket backend " + this.url, ex);
					}
					response.setStatusCode(HttpStatus.BAD_GATEWAY);
					return null;
				}
				if (!handshakeHandler.doHandshake(request, response, bridge.clientHandler(), attributes)) {
					bridge.abort();
					return null;
				}
				chain.applyAfterHandshake(request, response, null);
			}
			catch (Exception ex) {
				bridge.abort();
				HandshakeFailureException failure = (ex instanceof HandshakeFailureException hfe) ? hfe
						: new HandshakeFailureException("Uncaught failure for request " + request.getURI(), ex);
				chain.applyAfterHandshake(request, response, failure);
				throw failure;
			}
			finally {
				response.close();
			}
			return null;
		}

	}

}
