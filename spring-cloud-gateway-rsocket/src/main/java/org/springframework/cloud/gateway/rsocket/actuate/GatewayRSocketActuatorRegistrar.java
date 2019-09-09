/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.rsocket.actuate;

import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.rsocket.AbstractRSocket;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.SocketAcceptor;
import io.rsocket.frame.SetupFrameFlyweight;
import io.rsocket.util.DefaultPayload;

import org.springframework.cloud.gateway.rsocket.autoconfigure.GatewayRSocketProperties;
import org.springframework.cloud.gateway.rsocket.metadata.TagsMetadata;
import org.springframework.cloud.gateway.rsocket.routing.RoutingTable;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;

import static io.rsocket.metadata.WellKnownMimeType.APPLICATION_CBOR;
import static io.rsocket.metadata.WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA;

public class GatewayRSocketActuatorRegistrar implements SmartLifecycle {

	private final AtomicBoolean running = new AtomicBoolean();

	private final RoutingTable routingTable;

	private final RSocketMessageHandler messageHandler;

	private final GatewayRSocketProperties properties;

	public GatewayRSocketActuatorRegistrar(RoutingTable routingTable,
			RSocketMessageHandler messageHandler, GatewayRSocketProperties properties) {
		this.routingTable = routingTable;
		this.messageHandler = messageHandler;
		this.properties = properties;
	}

	@Override
	public void start() {
		if (this.running.compareAndSet(false, true)) {
			ConnectionSetupPayload connectionSetupPayload = getConnectionSetupPayload();
			SocketAcceptor responder = this.messageHandler.responder();
			responder.accept(connectionSetupPayload, new AbstractRSocket() {
			}).subscribe(rSocket -> {
				TagsMetadata tagsMetadata = TagsMetadata.builder()
						.routeId(properties.getRouteId())
						.serviceName(properties.getServiceName())
						// TODO: move to well known implementation key
						.with("proxy", Boolean.FALSE.toString()).build();
				routingTable.register(tagsMetadata, rSocket);
			});
		}
	}

	private ConnectionSetupPayload getConnectionSetupPayload() {
		DataBufferFactory dataBufferFactory = messageHandler.getRSocketStrategies()
				.dataBufferFactory();
		NettyDataBufferFactory ndbf = (NettyDataBufferFactory) dataBufferFactory;
		ByteBufAllocator byteBufAllocator = ndbf.getByteBufAllocator();
		Payload setupPayload = DefaultPayload.create(Unpooled.EMPTY_BUFFER,
				Unpooled.EMPTY_BUFFER);
		ByteBuf setup = SetupFrameFlyweight.encode(byteBufAllocator, false, 1, 1,
				MESSAGE_RSOCKET_COMPOSITE_METADATA.getString(),
				// TODO: configurable?
				APPLICATION_CBOR.getString(), setupPayload);
		return ConnectionSetupPayload.create(setup);
	}

	@Override
	public void stop() {
		if (this.running.compareAndSet(true, false)) {
			System.out.println();
		}
	}

	@Override
	public boolean isAutoStartup() {
		return true;
	}

	@Override
	public void stop(Runnable callback) {
		stop();
		if (callback != null) {
			callback.run();
		}
	}

	@Override
	public int getPhase() {
		return 0;
	}

	@Override
	public boolean isRunning() {
		return this.running.get();
	}

}
