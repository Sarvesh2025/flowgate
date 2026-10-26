-- Atomic token bucket.
-- KEYS[1] redis key
-- ARGV[1] capacity
-- ARGV[2] refill tokens per millisecond
-- ARGV[3] now (unix ms)
-- ARGV[4] tokens requested
-- ARGV[5] key TTL ms
-- Returns { allowed (0|1), remaining tokens (floor) }

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_per_ms = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])
local ttl = tonumber(ARGV[5])

local data = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(data[1])
local ts = tonumber(data[2])

if tokens == nil or ts == nil then
  tokens = capacity
  ts = now
end

local elapsed = now - ts
if elapsed < 0 then
  elapsed = 0
end

tokens = math.min(capacity, tokens + (elapsed * refill_per_ms))

local allowed = 0
if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
end

redis.call('HSET', key, 'tokens', tokens, 'ts', now)
redis.call('PEXPIRE', key, ttl)

return { allowed, math.floor(tokens) }
