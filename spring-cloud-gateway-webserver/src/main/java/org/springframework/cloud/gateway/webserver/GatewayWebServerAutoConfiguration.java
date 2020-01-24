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

package org.springframework.cloud.gateway.webserver;

import java.io.IOException;

import io.undertow.Undertow;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.UpgradeProtocol;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.webapp.WebAppContext;
import org.xnio.OptionMap;
import org.xnio.SslClientAuthMode;
import org.xnio.Xnio;
import reactor.netty.http.server.HttpServer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.embedded.NettyWebServerFactoryCustomizer;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.socket.client.JettyWebSocketClient;
import org.springframework.web.reactive.socket.client.TomcatWebSocketClient;
import org.springframework.web.reactive.socket.client.UndertowWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

/**
 * @author Spencer Gibb
 */
@Configuration(proxyBeanMethods = false)
public class GatewayWebServerAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ Server.class, Loader.class, WebAppContext.class })
	public static class JettyWebServerFactoryCustomizerConfiguration {

		@Bean
		@ConditionalOnMissingBean(WebSocketClient.class)
		public JettyWebSocketClient jettyWebSocketClient() {
			return new JettyWebSocketClient();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HttpServer.class)
	// Only enable netty if all other web servers are missing
	@ConditionalOnMissingClass({ "org.eclipse.jetty.server.Server",
			"org.eclipse.jetty.util.Loader", "org.eclipse.jetty.webapp.WebAppContext",
			"org.apache.catalina.startup.Tomcat", "org.apache.coyote.UpgradeProtocol",
			"io.undertow.Undertow", "org.xnio.SslClientAuthMode" })
	public static class NettyWebServerConfiguration {

		@Bean
		@ConditionalOnProperty(name = "spring.cloud.gateway.httpserver.wiretap")
		public NettyWebServerFactoryCustomizer nettyServerWiretapCustomizer(
				Environment environment, ServerProperties serverProperties) {
			return new NettyWebServerFactoryCustomizer(environment, serverProperties) {
				@Override
				public void customize(NettyReactiveWebServerFactory factory) {
					factory.addServerCustomizers(httpServer -> httpServer.wiretap(true));
					super.customize(factory);
				}
			};
		}

		// ReactorNettyWebSocketClient is still in GatwayAutoConfiguration because it
		// needs HttpClient

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ Tomcat.class, UpgradeProtocol.class })
	public static class TomcatWebServerConfiguration {

		@Bean
		@ConditionalOnMissingBean(WebSocketClient.class)
		public TomcatWebSocketClient tomcatWebSocketClient() {
			return new TomcatWebSocketClient();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ Undertow.class, SslClientAuthMode.class })
	public static class UndertowWebServerConfiguration {

		@Bean
		@ConditionalOnMissingBean(WebSocketClient.class)
		public UndertowWebSocketClient undertowWebSocketClient() throws IOException {
			return new UndertowWebSocketClient(
					Xnio.getInstance().createWorker(OptionMap.EMPTY));
		}

	}

}
