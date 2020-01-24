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

import io.undertow.Undertow;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.UpgradeProtocol;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.Test;
import org.xnio.SslClientAuthMode;
import reactor.netty.http.server.HttpServer;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.embedded.NettyWebServerFactoryCustomizer;
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.web.reactive.socket.client.JettyWebSocketClient;
import org.springframework.web.reactive.socket.client.TomcatWebSocketClient;
import org.springframework.web.reactive.socket.client.UndertowWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayWebServerAutoConfigurationTests {

	@Test
	public void jettyServerWorks() {
		new ReactiveWebApplicationContextRunner()
				.withClassLoader(new FilteredClassLoader(HttpServer.class, Tomcat.class,
						UpgradeProtocol.class, Undertow.class, SslClientAuthMode.class))
				.withConfiguration(
						AutoConfigurations.of(GatewayWebServerAutoConfiguration.class))
				.run(context -> {
					assertThat(context).hasSingleBean(JettyWebSocketClient.class)
							.hasSingleBean(WebSocketClient.class);
				});
	}

	@Test
	public void nettyServerWorks() {
		new ReactiveWebApplicationContextRunner()
				.withClassLoader(new FilteredClassLoader(Server.class, Loader.class,
						WebAppContext.class, Tomcat.class, UpgradeProtocol.class,
						Undertow.class, SslClientAuthMode.class))
				.withConfiguration(AutoConfigurations.of(
						ReactiveWebServerFactoryAutoConfiguration.class,
						GatewayWebServerAutoConfiguration.class))
				.withPropertyValues("spring.cloud.gateway.httpserver.wiretap=true")
				.run(context -> {
					assertThat(context)
							.hasSingleBean(NettyWebServerFactoryCustomizer.class)
							.doesNotHaveBean(WebSocketClient.class);
				});
	}

	@Test
	public void tomcatServerWorks() {
		new ReactiveWebApplicationContextRunner()
				.withClassLoader(new FilteredClassLoader(Server.class, Loader.class,
						WebAppContext.class, HttpServer.class, Undertow.class,
						SslClientAuthMode.class))
				.withConfiguration(
						AutoConfigurations.of(GatewayWebServerAutoConfiguration.class))
				.run(context -> {
					assertThat(context).hasSingleBean(TomcatWebSocketClient.class)
							.hasSingleBean(WebSocketClient.class);
				});
	}

	@Test
	public void undertowServerWorks() {
		new ReactiveWebApplicationContextRunner()
				.withClassLoader(new FilteredClassLoader(Server.class, Loader.class,
						WebAppContext.class, HttpServer.class, Tomcat.class,
						UpgradeProtocol.class))
				.withConfiguration(
						AutoConfigurations.of(GatewayWebServerAutoConfiguration.class))
				.run(context -> {
					assertThat(context).hasSingleBean(UndertowWebSocketClient.class)
							.hasSingleBean(WebSocketClient.class);
				});
	}

}
