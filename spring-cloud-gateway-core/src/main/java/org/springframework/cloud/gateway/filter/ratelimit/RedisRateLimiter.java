package org.springframework.cloud.gateway.filter.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * See https://stripe.com/blog/rate-limiters and
 * https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d#file-1-check_request_rate_limiter-rb-L11-L34
 *
 * @author Spencer Gibb
 */
public class RedisRateLimiter implements RateLimiter {
	private Log log = LogFactory.getLog(getClass());

	private final ReactiveRedisTemplate<Object, Object> redisTemplate;

	public RedisRateLimiter(ReactiveRedisTemplate<Object, Object> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	/**
	 * This uses a basic token bucket algorithm and relies on the fact that Redis scripts execute atomically.
	 * No other operations can run between fetching the count and writing the new count.
	 * @param replenishRate
	 * @param burstCapacity
	 * @param id
	 * @return
	 */
	@Override
	//TODO: signature? params (tuple?).
	//TODO: change to Mono<?>
	public Response isAllowed(String id, long replenishRate, long burstCapacity) {

		try {
			// Make a unique key per user.
			String key = "request_rate_limiter." + id;

			// You need two Redis keys for Token Bucket.
			// String tokensKey = key + ".tokens";
			// String timestampKey = key + ".timestamp";

			// The arguments to the LUA script. time() returns unixtime in seconds.
			long now = Instant.now().getEpochSecond();
			int requested = 1;

			double fillTime = (double)burstCapacity / (double)replenishRate;
			int ttl = (int)Math.floor(fillTime * 2);

			Mono<Boolean> booleanMono = this.redisTemplate.hasKey(key);
			Boolean hasKey = booleanMono.block();

			Mono<List<Object>> valuesMono;
			if (hasKey) {
				valuesMono = this.redisTemplate.opsForHash().multiGet(key, Arrays.asList("tokens", "timestamp"));
			} else {
				valuesMono = Mono.just(new ArrayList<>());
			}
			Mono<Response> responseMono = valuesMono.map(objects -> {
				Long lastTokens = null;

				if (objects.size() >= 1) {
					lastTokens= (Long) objects.get(0);
				}
				if (lastTokens == null) {
					lastTokens = burstCapacity;
				}

				Long lastRefreshed = null;
				if (objects.size() >= 2) {
					lastRefreshed = (Long) objects.get(1);
				}
				if (lastRefreshed == null) {
					lastRefreshed = 0L;
				}

				long delta = Math.max(0, (now - lastRefreshed));
				long filledTokens = Math.min(burstCapacity, lastTokens + (delta * replenishRate));
				boolean allowed = filledTokens >= requested;
				long newTokens = filledTokens;
				if (allowed) {
					newTokens = filledTokens - requested;
				}

				HashMap<Object, Object> updated = new HashMap<>();
				updated.put("tokens", newTokens);
				updated.put("timestamp", now);
				Mono<Boolean> putAllMono = this.redisTemplate.opsForHash().putAll(key, updated);
				Mono<Boolean> expireMono = this.redisTemplate.expire(key, Duration.ofSeconds(ttl));

				Flux<Tuple2<Boolean, Boolean>> zip = Flux.zip(putAllMono, expireMono);
				Tuple2<Boolean, Boolean> objects1 = zip.blockLast();

				Response response = new Response(allowed, newTokens);

				if (log.isDebugEnabled()) {
					log.debug("response: " + response);
				}

				return response;
			});

			return responseMono.block();

		} catch (Exception e) {
			/* We don't want a hard dependency on Redis to allow traffic.
			Make sure to set an alert so you know if this is happening too much.
			Stripe's observed failure rate is 0.01%. */
			log.error("Error determining if user allowed from redis", e);
		}
		return new Response(true, -1);
	}
}
