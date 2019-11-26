local tokens_key = KEYS[1]

local limit = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])

local start_count = 0
-- check if token exist else start quota at zero
local last_tokens = tonumber(redis.call("get", tokens_key))
if last_tokens == nil then
    redis.log(redis.LOG_WARNING, "last tokens ")
    -- set new token with ttl and value
    if ttl > 0 then
        redis.call("setex", tokens_key, ttl, start_count)
    else
        redis.call("set", tokens_key, start_count)
    end
    last_tokens = start_count
end

redis.call("incr", tokens_key)

-- set last_tokens + 1 because of the increment
local req_left = limit - (last_tokens + 1)

return { req_left }