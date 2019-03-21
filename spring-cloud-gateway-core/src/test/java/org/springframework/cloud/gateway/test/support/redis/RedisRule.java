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

package org.springframework.cloud.gateway.test.support.redis;

import java.io.IOException;
import java.net.ServerSocket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.rules.ExternalResource;
import redis.embedded.RedisServer;

import static java.lang.String.format;
import static java.util.stream.IntStream.range;

public final class RedisRule extends ExternalResource {

	public static final int DEFAULT_REDIS_PORT = 6379;

	private final int port;

	private final boolean ignoreDefaultPortFailure;

	private Log log = LogFactory.getLog(getClass());

	private RedisServer redisServer;

	private RedisRule(int port) {
		this(port, false);
	}

	private RedisRule(int port, boolean ignoreDefaultPortFailure) {
		this.port = port;
		this.ignoreDefaultPortFailure = ignoreDefaultPortFailure;
	}

	public static RedisRule bindToDefaultPort() {
		return new RedisRule(DEFAULT_REDIS_PORT, true);
	}

	public static RedisRule bindToDefaultPort(int port) {
		return new RedisRule(port);
	}

	public static RedisRule bindToFirstOpenPort(int startInclusive, int endExclusive) {
		return new RedisRule(findOpenPort(startInclusive, endExclusive));
	}

	private static int findOpenPort(final int startInclusive, final int endExclusive) {
		return range(startInclusive, endExclusive).filter(RedisRule::testPort).findFirst()
				.orElseThrow(() -> new IllegalStateException(
						format("No open port found in the range [%d, %d]", startInclusive,
								endExclusive)));
	}

	private static boolean testPort(int port) {
		try {
			new ServerSocket(port).close();
			return true;
		}
		catch (final IOException ex) {
			return false;
		}
	}

	@Override
	protected void before() {
		try {
			redisServer = RedisServer.builder().port(port).setting("maxmemory 16MB")
					.build();
			redisServer.start();
		}
		catch (final Exception e) {
			if (port == DEFAULT_REDIS_PORT && ignoreDefaultPortFailure) {
				log.info(
						"Unable to start embedded Redis on default port. Ignoring error. Assuming redis is already running.");
			}
			else {
				throw new RuntimeException(format(
						"Error while initializing the Redis server" + " on port %d",
						port), e);
			}
		}
	}

	@Override
	protected void after() {
		redisServer.stop();
	}

}
