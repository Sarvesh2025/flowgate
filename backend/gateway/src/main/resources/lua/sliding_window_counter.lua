-- Sliding window counter: current count + weighted previous window.
-- KEYS[1] key prefix
-- ARGV[1] window ms
-- ARGV[2] limit
-- ARGV[3] now ms
-- ARGV[4] ttl ms (keep previous window alive)
-- Returns { allowed (0|1), remaining }

local base = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

local id = math.floor(now / window)
local curr_key = base .. ':' .. id
local prev_key = base .. ':' .. (id - 1)

local current = tonumber(redis.call('GET', curr_key)) or 0
local previous = tonumber(redis.call('GET', prev_key)) or 0
local weight = (window - (now % window)) / window
local estimate = previous * weight + current

if estimate >= limit then
  return { 0, 0 }
end

current = redis.call('INCR', curr_key)
redis.call('PEXPIRE', curr_key, ttl)
estimate = previous * weight + current
local remaining = math.floor(limit - estimate)
if remaining < 0 then
  remaining = 0
end
return { 1, remaining }
