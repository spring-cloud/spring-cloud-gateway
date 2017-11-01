package org.springframework.cloud.gateway.filter.ratelimit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.tuple.Tuple;

import static org.springframework.tuple.TupleBuilder.tuple;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * See https://stripe.com/blog/rate-limiters and
 * https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d#file-1-check_request_rate_limiter-rb-L11-L34
 *
 * @author Spencer Gibb
 */
public class RedisRateLimiter implements RateLimiter {
	public static final String REPLENISH_RATE_KEY = "replenishRate";
	public static final String BURST_CAPACITY_KEY = "burstCapacity";

	private Log log = LogFactory.getLog(getClass());

	private final ReactiveRedisTemplate<String, String> redisTemplate;
	private final RedisScript<List<Long>> script;

	public RedisRateLimiter(ReactiveRedisTemplate<String, String> redisTemplate,
			RedisScript<List<Long>> script) {
		this.redisTemplate = redisTemplate;
		this.script = script;
	}

	public static Tuple args(int replenishRate, int burstCapacity) {
		return tuple().of(REPLENISH_RATE_KEY, replenishRate, BURST_CAPACITY_KEY, burstCapacity);
	}

	/**
	 * This uses a basic token bucket algorithm and relies on the fact that Redis scripts
	 * execute atomically. No other operations can run between fetching the count and
	 * writing the new count.
	 * @param id
	 * @param args
	 * @return
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Mono<Response> isAllowed(String id, Tuple args) {
		// How many requests per second do you want a user to be allowed to do?
		int replenishRate = args.getInt(REPLENISH_RATE_KEY);

		// How much bursting do you want to allow?
		int burstCapacity;
		if (args.hasFieldName(BURST_CAPACITY_KEY)) {
			burstCapacity = args.getInt(BURST_CAPACITY_KEY);
		} else {
			burstCapacity = 0;
		}

		try {
			// Make a unique key per user.
			String prefix = "request_rate_limiter." + id;

			// You need two Redis keys for Token Bucket.
			List<String> keys = Arrays.asList(prefix + ".tokens", prefix + ".timestamp");

			// The arguments to the LUA script. time() returns unixtime in seconds.
			List<String> scriptArgs = Arrays.asList(replenishRate + "", burstCapacity + "",
					Instant.now().getEpochSecond() + "", "1");
			// allowed, tokens_left = redis.eval(SCRIPT, keys, args)
			Flux<List<Long>> flux = this.redisTemplate.execute(this.script, keys, scriptArgs);
					// .log("redisratelimiter", Level.FINER);
			return flux.onErrorResume(throwable -> Flux.just(Arrays.asList(1L, -1L)))
					.reduce(new ArrayList<Long>(), (longs, l) -> {
						longs.addAll(l);
						return longs;
					}) .map(results -> {
						boolean allowed = results.get(0) == 1L;
						Long tokensLeft = results.get(1);

						Response response = new Response(allowed, tokensLeft);

						if (log.isDebugEnabled()) {
							log.debug("response: " + response);
						}
						return response;
					});
		}
		catch (Exception e) {
			/*
			 * We don't want a hard dependency on Redis to allow traffic. Make sure to set
			 * an alert so you know if this is happening too much. Stripe's observed
			 * failure rate is 0.01%.
			 */
			log.error("Error determining if user allowed from redis", e);
		}
		return Mono.just(new Response(true, -1));
	}
}
