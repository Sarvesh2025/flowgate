-- Sliding window log: one sorted-set entry per request (score = timestamp).
-- KEYS[1] zset key
-- ARGV[1] window ms
-- ARGV[2] limit
-- ARGV[3] now ms
-- ARGV[4] ttl ms
-- ARGV[5] unique member (so two requests in the same ms do not collide)
-- Returns { allowed (0|1), remaining }

local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])
local member = ARGV[5]

redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
local count = redis.call('ZCARD', key)

if count >= limit then
  redis.call('PEXPIRE', key, ttl)
  return { 0, 0 }
end

redis.call('ZADD', key, now, member)
redis.call('PEXPIRE', key, ttl)
return { 1, limit - count - 1 }
