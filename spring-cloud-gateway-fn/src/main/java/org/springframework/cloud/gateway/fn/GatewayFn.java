/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.fn;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.WebServerException;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.ipc.netty.http.HttpResources;
import reactor.ipc.netty.http.server.HttpServer;
import reactor.ipc.netty.tcp.BlockingNettyContext;

public class GatewayFn {
	private static final Logger logger = LoggerFactory.getLogger(GatewayFn.class);
	private RouterFunction<ServerResponse> routerFunction;

	private int port = 8080;

	private InetAddress address;

	private HttpServer httpServer;
	private BlockingNettyContext nettyContext;

	public GatewayFn(int port) {
		httpServer = createHttpServer();
		this.port = port;
	}

	public GatewayFn setRouterFunction(RouterFunction<ServerResponse> routerFunction) {
		this.routerFunction = routerFunction;
		return this;
	}

	@NotNull
	private HttpServer createHttpServer() {
		return HttpServer.builder().options((options) -> {
			options.listenAddress(getListenAddress());
			/*if (getSsl() != null && getSsl().isEnabled()) {
				SslServerCustomizer sslServerCustomizer = new SslServerCustomizer(
						getSsl(), getSslStoreProvider());
				sslServerCustomizer.customize(options);
			}
			if (getCompression() != null && getCompression().getEnabled()) {
				CompressionCustomizer compressionCustomizer = new CompressionCustomizer(
						getCompression());
				compressionCustomizer.customize(options);
			}
			applyCustomizers(options);*/
		}).build();
	}

	public int getPort() {
		return port;
	}

	public InetAddress getAddress() {
		return address;
	}

	public void start() {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		// Use names and ensure unique to protect against duplicates
		List<String> names = new ArrayList<>(SpringFactoriesLoader.loadFactoryNames(RouterFunctionLocator.class, classLoader));

		names.forEach(name -> {
			try {
				RouterFunctionLocator locator = (RouterFunctionLocator) Class.forName(name).newInstance();
				RouterFunction<ServerResponse> fn = locator.locate();
				routerFunction = (routerFunction == null) ? fn : routerFunction.and(fn);
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				ReflectionUtils.rethrowRuntimeException(e);
			}
		});

		HttpHandler httpHandler = RouterFunctions.toHttpHandler(routerFunction);
		nettyContext = httpServer.start(new ReactorHttpHandlerAdapter(httpHandler));
		logger.info("Netty started on port(s): " + getPort());
		startDaemonAwaitThread(nettyContext);
	}

	private void startDaemonAwaitThread(BlockingNettyContext nettyContext) {
		Thread awaitThread = new Thread("server") {

			@Override
			public void run() {
				nettyContext.getContext().onClose().block();
			}

		};
		awaitThread.setContextClassLoader(getClass().getClassLoader());
		awaitThread.setDaemon(false);
		awaitThread.start();
	}

	public void stop() throws WebServerException {
		if (this.nettyContext != null) {
			this.nettyContext.shutdown();
			// temporary fix for gh-9146
			this.nettyContext.getContext().onClose()
					.doOnSuccess((o) -> HttpResources.reset()).block();
			this.nettyContext = null;
		}
	}

	private InetSocketAddress getListenAddress() {
		if (getAddress() != null) {
			return new InetSocketAddress(getAddress().getHostAddress(), getPort());
		}
		return new InetSocketAddress(getPort());
	}

}
