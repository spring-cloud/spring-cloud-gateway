package org.springframework.cloud.gateway.test.support.redis;

import java.io.IOException;
import java.net.ServerSocket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.rules.ExternalResource;
import redis.embedded.RedisServer;

import static java.lang.String.format;
import static java.util.stream.IntStream.range;

public class RedisRule extends ExternalResource {

	private Log log = LogFactory.getLog(getClass());

	public static final int DEFAULT_REDIS_PORT = 6379;

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
		return range(startInclusive, endExclusive)
				.filter(RedisRule::testPort)
				.findFirst()
				.orElseThrow(() ->new IllegalStateException(format(
						"No open port found in the range [%d, %d]", startInclusive, endExclusive)));
	}

	private static boolean testPort(int port) {
		try {
			new ServerSocket(port).close();
			return true;
		} catch (final IOException ex) {
			return false;
		}
	}

	private final int port;
	private final boolean ignoreDefaultPortFailure;

	private RedisServer redisServer;

	private RedisRule(int port) {
		this(port, false);
	}

	private RedisRule(int port, boolean ignoreDefaultPortFailure) {
		this.port = port;
		this.ignoreDefaultPortFailure = ignoreDefaultPortFailure;
	}

	@Override
	protected void before() {
		try {
			redisServer = RedisServer.builder().port(port).setting("maxmemory 16MB").build();
			redisServer.start();
		} catch (final Exception e) {
			if (port == DEFAULT_REDIS_PORT && ignoreDefaultPortFailure) {
				log.info("Unable to start embedded Redis on default port. Ignoring error. Assuming redis is already running.");
			} else {
				throw new RuntimeException(format("Error while initializing the Redis server"
						+ " on port %d", port), e);
			}
		}
	}

	@Override
	protected void after() {
		redisServer.stop();
	}

}