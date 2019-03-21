/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.server;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.rsocket.RSocketFactory;
import io.rsocket.SocketAcceptor;
import io.rsocket.micrometer.MicrometerDuplexConnectionInterceptor;
import io.rsocket.plugins.RSocketInterceptor;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.rsocket.autoconfigure.GatewayRSocketProperties;
import org.springframework.cloud.gateway.rsocket.autoconfigure.GatewayRSocketProperties.Server.TransportType;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

public class GatewayRSocketServer implements Ordered, SmartLifecycle {

	private static final Log log = LogFactory.getLog(GatewayRSocketServer.class);

	private static final RSocketInterceptor[] EMPTY_INTERCEPTORS = new RSocketInterceptor[0];

	private final GatewayRSocketProperties properties;

	private final SocketAcceptor socketAcceptor;

	private final List<RSocketInterceptor> serverInterceptors;

	private final AtomicBoolean running = new AtomicBoolean();

	private CloseableChannel closeableChannel;

	private final MeterRegistry meterRegistry;

	public GatewayRSocketServer(GatewayRSocketProperties properties,
			SocketAcceptor socketAcceptor, MeterRegistry meterRegistry) {
		this(properties, socketAcceptor, meterRegistry, EMPTY_INTERCEPTORS);
	}

	public GatewayRSocketServer(GatewayRSocketProperties properties,
			SocketAcceptor socketAcceptor, MeterRegistry meterRegistry,
			RSocketInterceptor... interceptors) {
		Assert.notNull(properties, "properties may not be null");
		Assert.notNull(socketAcceptor, "socketAcceptor may not be null");
		Assert.notNull(meterRegistry, "meterRegistry may not be null");
		Assert.notNull(interceptors, "interceptors may not be null");
		this.properties = properties;
		this.socketAcceptor = socketAcceptor;
		this.meterRegistry = meterRegistry;
		this.serverInterceptors = Arrays.asList(interceptors);
	}

	@Override
	public int getOrder() {
		// return 0;
		return HIGHEST_PRECEDENCE;
	}

	@Override
	public void start() {
		if (running.compareAndSet(false, true)) {
			startServer();
		}
	}

	@Override
	public void stop() {
		if (running.compareAndSet(true, false)) {
			if (log.isInfoEnabled()) {
				log.info("Stopping Gateway RSocket Server");
			}
			if (closeableChannel != null) {
				closeableChannel.dispose();
			}
		}
	}

	@Override
	public boolean isRunning() {
		return running.get();
	}

	protected void startServer() {
		GatewayRSocketProperties.Server server = properties.getServer();
		int port = server.getPort();

		TransportType transportType = server.getTransport();
		TcpServerTransport transport;
		switch (transportType) {
		case TCP:
			transport = TcpServerTransport.create(port);
			break;
		default:
			throw new IllegalArgumentException(
					"No support for server transport " + transportType);
		}

		if (log.isInfoEnabled()) {
			log.info("Starting Gateway RSocket Server on port: " + port + ", transport: "
					+ transportType);
		}

		RSocketFactory.ServerRSocketFactory factory = RSocketFactory.receive();

		serverInterceptors.forEach(factory::addServerPlugin);

		List<String> micrometerTags = server.getMicrometerTags();
		Tag[] tags = Tags.of(micrometerTags.toArray(new String[] {}))
				.and("gateway.id", properties.getId()).stream()
				.collect(Collectors.toList()).toArray(new Tag[] {});

		factory
				// TODO: add as bean like serverInterceptors above
				.addConnectionPlugin(
						new MicrometerDuplexConnectionInterceptor(meterRegistry, tags))
				.errorConsumer(throwable -> {
					if (log.isDebugEnabled()) {
						log.debug("Error with connection", throwable);
					}
				}) // TODO: add configurable errorConsumer
				.acceptor(this.socketAcceptor).transport(transport).start()
				.map(closeableChannel -> {
					this.closeableChannel = closeableChannel;
					return closeableChannel;
				}).subscribe();
	}

}
