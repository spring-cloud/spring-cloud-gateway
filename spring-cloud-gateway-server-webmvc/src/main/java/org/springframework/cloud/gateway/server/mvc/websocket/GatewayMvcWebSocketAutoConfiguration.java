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

import java.util.List;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerContainer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.handler.WebSocketHandlerFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;

/**
 * Auto-configuration for proxying WebSocket connections with the WebMVC gateway.
 */
@AutoConfiguration
@ConditionalOnClass({ WebSocketHandler.class, ServerContainer.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = WebSocketProxyProperties.PREFIX + ".enabled", matchIfMissing = true)
@EnableConfigurationProperties(WebSocketProxyProperties.class)
public class GatewayMvcWebSocketAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(WebSocketHandlerFunction.class)
	public WebSocketProxyHandlerFunction webSocketProxyHandlerFunction(WebSocketProxyProperties properties,
			ObjectProvider<RequestHttpHeadersFilter> requestHttpHeadersFilters) {
		int maxMessageSize = (int) properties.getMaxMessageSize().toBytes();
		WebSocketContainer container = ContainerProvider.getWebSocketContainer();
		container.setDefaultMaxTextMessageBufferSize(maxMessageSize);
		container.setDefaultMaxBinaryMessageBufferSize(maxMessageSize);

		OriginHandshakeInterceptor originInterceptor = new OriginHandshakeInterceptor();
		originInterceptor.setAllowedOriginPatterns(properties.getAllowedOrigins());

		return new WebSocketProxyHandlerFunction(new StandardWebSocketClient(container), new DefaultHandshakeHandler(),
				List.of(originInterceptor), requestHttpHeadersFilters, properties);
	}

}
