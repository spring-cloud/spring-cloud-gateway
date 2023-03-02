redis.replicate_commands()

local tokens_key = KEYS[1]
local timestamp_key = KEYS[2]
--redis.log(redis.LOG_WARNING, "tokens_key " .. tokens_key)

local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])
local refill_unit_sec = tonumber(ARGV[5])

-- default value 1 sec
if refill_unit_sec == nil then
  refill_unit_sec = 1
end

local fill_time = (capacity / rate) * refill_unit_sec
local ttl = math.floor(fill_time*2)
local refill_per_sec = rate / refill_unit_sec

-- for testing, it should use redis system time in production
if now == nil then
  now = redis.call('TIME')[1]
end

--redis.log(redis.LOG_WARNING, "rate " .. ARGV[1])
--redis.log(redis.LOG_WARNING, "capacity " .. ARGV[2])
--redis.log(redis.LOG_WARNING, "now " .. now)
--redis.log(redis.LOG_WARNING, "requested " .. ARGV[4])
--redis.log(redis.LOG_WARNING, "filltime " .. fill_time)
--redis.log(redis.LOG_WARNING, "ttl " .. ttl)

local last_tokens = tonumber(redis.call("get", tokens_key))
if last_tokens == nil then
  last_tokens = capacity
end
--redis.log(redis.LOG_WARNING, "last_tokens " .. last_tokens)

local last_refreshed = tonumber(redis.call("get", timestamp_key))
if last_refreshed == nil then
  last_refreshed = 0
end
--redis.log(redis.LOG_WARNING, "last_refreshed " .. last_refreshed)

local delta = math.max(0, now - last_refreshed)
-- fill only after refill_unit_sec from last refilled
local filling_token = 0

local last_filled_time = last_refreshed

if delta >= refill_unit_sec then
  filling_token = delta * refill_per_sec
  last_filled_time = now
end

local filled_tokens = math.min(capacity, last_tokens + filling_token)
local allowed = filled_tokens >= requested
local new_tokens = filled_tokens
local allowed_num = 0
if allowed then
  new_tokens = filled_tokens - requested
  allowed_num = 1
end

--redis.log(redis.LOG_WARNING, "delta " .. delta)
--redis.log(redis.LOG_WARNING, "filled_tokens " .. filled_tokens)
--redis.log(redis.LOG_WARNING, "allowed_num " .. allowed_num)
--redis.log(redis.LOG_WARNING, "new_tokens " .. new_tokens)

if ttl > 0 then
  redis.call("setex", tokens_key, ttl, new_tokens)
  redis.call("setex", timestamp_key, ttl, last_filled_time)
end

-- return { allowed_num, new_tokens, capacity, filled_tokens, requested, new_tokens }
return { allowed_num, new_tokens }
