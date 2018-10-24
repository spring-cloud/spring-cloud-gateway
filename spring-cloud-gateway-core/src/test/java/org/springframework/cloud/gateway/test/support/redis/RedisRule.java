package org.springframework.cloud.gateway.test.support.redis;

import static java.lang.String.format;
import static java.util.stream.IntStream.range;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.rules.ExternalResource;
import redis.embedded.RedisServer;

public class RedisRule extends ExternalResource {

	public static RedisRule bindToPort(final int port) {
		return new RedisRule(port);
	}

	public static RedisRule bindToFirstOpenPort(final int startInclusive, final int endExclusive) {
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

	private RedisServer redisServer;

	private RedisRule(final int port) {
		this.port = port;
	}

	@Override
	protected void before() {
		try {
			redisServer = RedisServer.builder().port(port).setting("maxmemory 16MB").build();
			redisServer.start();
		} catch (final Exception e) {
			throw new RuntimeException(format("Error while initializing the Redis server"
					+ " on port %d", port), e);
		}
	}

	@Override
	protected void after() {
		redisServer.stop();
	}

}