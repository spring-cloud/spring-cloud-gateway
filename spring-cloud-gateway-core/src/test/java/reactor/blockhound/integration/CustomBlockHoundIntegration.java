/*
 * Copyright 2019-2019 the original author or authors.
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

package reactor.blockhound.integration;

import reactor.blockhound.BlockHound;

/**
 * @author Tim Ysewyn
 */
public class CustomBlockHoundIntegration implements BlockHoundIntegration {

	@Override
	public void applyTo(BlockHound.Builder builder) {
		try {
			Class.forName("ch.qos.logback.classic.spi.PackagingDataCalculator");
			// Used from
			// org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler.logError
			builder.allowBlockingCallsInside(
					"ch.qos.logback.classic.spi.PackagingDataCalculator",
					"getImplementationVersion");
		}
		catch (ClassNotFoundException e) {
		}
		try {
			Class.forName("org.springframework.util.JdkIdGenerator");
			// Used from
			// org.springframework.web.server.session.InMemoryWebSessionStore$InMemoryWebSession.<init>
			builder.allowBlockingCallsInside("org.springframework.util.JdkIdGenerator",
					"generateId");
		}
		catch (ClassNotFoundException e) {
		}
		try {
			Class.forName("javax.net.ssl.SSLContext");
			// Used from
			// org.springframework.cloud.commons.httpclient.DefaultApacheHttpClientConnectionManagerFactory.newConnectionManager
			builder.allowBlockingCallsInside("javax.net.ssl.SSLContext", "init");
		}
		catch (ClassNotFoundException e) {
		}
		try {
			Class.forName("org.springframework.security.crypto.bcrypt.BCrypt");
			// Uses java.security.SecureRandom.nextBytes
			// Used from
			// org.springframework.security.authentication.AbstractUserDetailsReactiveAuthenticationManager.lambda$authenticate$4
			builder.allowBlockingCallsInside(
					"org.springframework.security.crypto.bcrypt.BCrypt", "gensalt");
		}
		catch (ClassNotFoundException e) {
		}
		try {
			Class.forName("io.netty.handler.ssl.SslHandler");
			// For HTTPS traffic
			builder.allowBlockingCallsInside("io.netty.handler.ssl.SslHandler",
					"channelActive");
			builder.allowBlockingCallsInside("io.netty.handler.ssl.SslHandler", "unwrap");
		}
		catch (ClassNotFoundException e) {
		}
		try {
			Class.forName("org.springframework.util.MimeTypeUtils");
			// Uses java.util.Random.nextInt
			builder.allowBlockingCallsInside("org.springframework.util.MimeTypeUtils",
					"generateMultipartBoundary");
		}
		catch (ClassNotFoundException e) {
		}

		// Test support
		builder.allowBlockingCallsInside(
				"org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactoryIntegrationTests$TestConfig",
				"sleep");
	}

}
