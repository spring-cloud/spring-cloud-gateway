


redis.replicate_commands()

local key = KEYS[1] -- token bucket name
local capacity = tonumber(ARGV[1]) -- max capacity
local quota = tonumber(ARGV[2]) -- limit within the time window
local period = tonumber(ARGV[3]) -- time window size (seconds)
local quantity = tonumber(ARGV[4]) or 1 -- number of tokens required, default is 1
local timestamp = tonumber(redis.call('time')[1]) -- nurrent timestamp

local remain = capacity
if (redis.call('exists', key) == 0) then
    redis.call('hmset', key, 'remain', remain, 'timestamp', timestamp)
else
    local values = redis.call('hmget', key, 'remain', 'timestamp')
    remain = tonumber(values[1])
    local last_reset = tonumber(values[2])
    local delta_quota = math.floor(((timestamp - last_reset) / period) * quota)
    if (delta_quota > 0) then
        remain = remain + delta_quota
        if (remain > capacity) then
            remain = capacity
        end
        redis.call('hmset', key, 'remain', remain, 'timestamp', timestamp)
    end
end

remain = math.max(0, remain - quantity)
redis.call('hmset', key, 'remain', remain)
redis.call('expire', key, 600)

return { 0, remain }