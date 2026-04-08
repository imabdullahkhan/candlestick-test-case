local key = KEYS[1]
local bucket_start = ARGV[1]
local price = ARGV[2]

local current_time = redis.call('HGET', key, 'time')

if not current_time then
    redis.call('HMSET', key, 'time', bucket_start, 'open', price, 'high', price, 'low', price, 'close', price, 'volume', '1')
    return {}
end

local ct = tonumber(current_time)
local bs = tonumber(bucket_start)
local p = tonumber(price)

if bs == ct then
    local high = tonumber(redis.call('HGET', key, 'high'))
    local low = tonumber(redis.call('HGET', key, 'low'))
    if p > high then redis.call('HSET', key, 'high', price) end
    if p < low then redis.call('HSET', key, 'low', price) end
    redis.call('HSET', key, 'close', price)
    redis.call('HINCRBY', key, 'volume', 1)
    return {}
end

if bs > ct then
    local old_time = current_time
    local old_open = redis.call('HGET', key, 'open')
    local old_high = redis.call('HGET', key, 'high')
    local old_low = redis.call('HGET', key, 'low')
    local old_close = redis.call('HGET', key, 'close')
    local old_volume = redis.call('HGET', key, 'volume')
    redis.call('HMSET', key, 'time', bucket_start, 'open', price, 'high', price, 'low', price, 'close', price, 'volume', '1')
    return {old_time, old_open, old_high, old_low, old_close, old_volume}
end

return {'LATE', bucket_start, price}
