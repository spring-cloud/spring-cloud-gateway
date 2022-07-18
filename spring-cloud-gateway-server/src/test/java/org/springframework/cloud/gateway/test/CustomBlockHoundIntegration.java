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

package org.springframework.cloud.gateway.test;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * @author Tim Ysewyn
 */
public class CustomBlockHoundIntegration implements BlockHoundIntegration {

	@Override
	public void applyTo(BlockHound.Builder builder) {
		// builder.blockingMethodCallback(it -> {
		// Error error = new Error(it.toString());
		// error.printStackTrace();
		// throw error;
		// });

		// Uses Unsafe#park
		builder.allowBlockingCallsInside("reactor.core.scheduler.SchedulerTask", "dispose");

		// Uses
		// ch.qos.logback.classic.spi.PackagingDataCalculator#getImplementationVersion
		builder.allowBlockingCallsInside(
				"org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler",
				"logError");
		builder.allowBlockingCallsInside("reactor.util.Loggers$Slf4JLogger", "debug");
		builder.allowBlockingCallsInside("reactor.util.Loggers$Slf4JLogger", "info");
		builder.allowBlockingCallsInside("reactor.util.Loggers$Slf4JLogger", "error");

		// Uses org.springframework.util.JdkIdGenerator#generateId
		// Uses UUID#randomUUID
		builder.allowBlockingCallsInside("org.springframework.web.server.session.InMemoryWebSessionStore",
				"lambda$createWebSession$0");

		// Uses java.util.Random#nextInt
		builder.allowBlockingCallsInside("org.springframework.util.MimeTypeUtils", "generateMultipartBoundary");

		// SPRING DATA REDIS RELATED

		// Uses Unsafe#park
		builder.allowBlockingCallsInside("org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory",
				"getReactiveConnection");

		// NETTY RELATED

		// Uses Thread#sleep
		builder.allowBlockingCallsInside("io.netty.channel.nio.NioEventLoop", "handleLoopException");
		builder.allowBlockingCallsInside("io.netty.util.concurrent.SingleThreadEventExecutor", "confirmShutdown");

		// Uses Unsafe#park
		builder.allowBlockingCallsInside("io.netty.util.concurrent.GlobalEventExecutor", "execute");
		builder.allowBlockingCallsInside("io.netty.util.concurrent.SingleThreadEventExecutor$6", "run");
		// builder.allowBlockingCallsInside("io.netty.util.concurrent.GlobalEventExecutor",
		// "takeTask");
		// builder.allowBlockingCallsInside("io.netty.util.concurrent.GlobalEventExecutor",
		// "addTask");
		builder.allowBlockingCallsInside("io.netty.util.concurrent.FastThreadLocalRunnable", "run");

		// SECURITY RELATED

		// For HTTPS traffic
		builder.allowBlockingCallsInside("io.netty.handler.ssl.SslHandler", "channelActive");
		builder.allowBlockingCallsInside("io.netty.handler.ssl.SslHandler", "channelInactive");
		builder.allowBlockingCallsInside("io.netty.handler.ssl.SslHandler", "unwrap");
		builder.allowBlockingCallsInside("io.netty.handler.ssl.SslContext", "newClientContextInternal");

		// Uses org.springframework.security.crypto.bcrypt.BCrypt#gensalt
		// Uses java.security.SecureRandom#nextBytes
		builder.allowBlockingCallsInside(
				"org.springframework.security.authentication.AbstractUserDetailsReactiveAuthenticationManager",
				"lambda$authenticate$4");

		// Uses java.io.RandomAccessFile#readBytes
		builder.allowBlockingCallsInside("org.springframework.context.annotation.ConfigurationClassParser", "parse");
		builder.allowBlockingCallsInside(
				"org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader", "loadBeanDefinitions");
		builder.allowBlockingCallsInside("org.springframework.core.type.classreading.SimpleMetadataReader",
				"getClassReader");

		builder.allowBlockingCallsInside("io.micrometer.context.ContextRegistry", "loadContextAccessors");
		builder.allowBlockingCallsInside("io.micrometer.context.ContextRegistry", "loadThreadLocalAccessors");
	}

}
