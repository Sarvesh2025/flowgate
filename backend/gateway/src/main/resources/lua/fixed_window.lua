-- Fixed window: count in the current clock bucket only.
-- KEYS[1] key prefix
-- ARGV[1] window ms
-- ARGV[2] limit
-- ARGV[3] now ms
-- ARGV[4] ttl ms
-- Returns { allowed (0|1), remaining }

local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])
local key = KEYS[1] .. ':' .. math.floor(now / window)

local n = redis.call('INCR', key)
if n == 1 then
  redis.call('PEXPIRE', key, ttl)
end

if n > limit then
  return { 0, 0 }
end
return { 1, limit - n }
