package org.springframework.cloud.gateway.filter.ratelimit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * See https://stripe.com/blog/rate-limiters and
 * https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d#file-1-check_request_rate_limiter-rb-L11-L34
 *
 * @author Spencer Gibb
 */
public class RedisRateLimiter implements RateLimiter {
	private Log log = LogFactory.getLog(getClass());

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<List> script;

	public RedisRateLimiter(StringRedisTemplate redisTemplate, RedisScript<List> script) {
		this.redisTemplate = redisTemplate;
		this.script = script;
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
	//TODO: signature? params (tuple?). Return type, tokens left?
	public Response isAllowed(String id, int replenishRate, int burstCapacity) {

		try {
			// Make a unique key per user.
			String prefix = "request_rate_limiter." + id;

			// You need two Redis keys for Token Bucket.
			List<String> keys = Arrays.asList(prefix + ".tokens", prefix + ".timestamp");

			// The arguments to the LUA script. time() returns unixtime in seconds.
			String[] args = new String[]{ replenishRate+"", burstCapacity +"", Instant.now().getEpochSecond()+"", "1"};
			// allowed, tokens_left = redis.eval(SCRIPT, keys, args)
			List results = this.redisTemplate.execute(this.script, keys, args);

			boolean allowed = new Long(1L).equals(results.get(0));
			Long tokensLeft = (Long) results.get(1);

			Response response = new Response(allowed, tokensLeft);

			if (log.isDebugEnabled()) {
				log.debug("response: "+response);
			}
			return response;

		} catch (Exception e) {
			/* We don't want a hard dependency on Redis to allow traffic.
			Make sure to set an alert so you know if this is happening too much.
			Stripe's observed failure rate is 0.01%. */
			log.error("Error determining if user allowed from redis", e);
		}
		return new Response(true, -1);
	}
}
