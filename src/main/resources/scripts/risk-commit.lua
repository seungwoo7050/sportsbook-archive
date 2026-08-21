local maxExact = 9007199254740991
local now = tonumber(ARGV[2])
local retention = tonumber(ARGV[3])
local token = ARGV[4]

local function exact(text, positive)
  if not text or not string.match(text, "^%d+$") then return nil end
  local value = tonumber(text)
  if not value or value > maxExact or (positive and value <= 0) then return nil end
  return value
end

local function keyType(key)
  return redis.call("TYPE", key).ok
end

if ARGV[1] ~= "1" or #KEYS ~= 2 or #ARGV ~= 12
  or not exact(ARGV[2], false) or not exact(ARGV[3], true)
  or not token or #token ~= 64 or not string.match(token, "^[0-9a-f]+$") then
  return redis.error_reply("invalid commit request")
end
for index = 5, 12 do
  if not exact(ARGV[index], true) then return redis.error_reply("invalid commit policy") end
end

local lifecycleType = keyType(KEYS[1])
if lifecycleType == "none" then return "NOT_FOUND" end
if lifecycleType ~= "hash" then return redis.error_reply("wrong reservation lifecycle type") end
if redis.call("HGET", KEYS[1], "fingerprint") ~= token then return "CONFLICT" end

local state = redis.call("HGET", KEYS[1], "state")
if state == "COMMITTED" then return "REPLAYED" end
if state == "EXPIRED" or state == "RELEASED" or state == "REJECTED" then
  return "TOMBSTONED"
end
if state ~= "RESERVED" then return redis.error_reply("unknown reservation state") end

local expiresAt = exact(redis.call("HGET", KEYS[1], "expiresAt"), false)
if not expiresAt then return redis.error_reply("corrupt reservation expiry") end
if expiresAt <= now then
  redis.call("HSET", KEYS[1], "state", "EXPIRED", "expiredAt", string.format("%.0f", now))
  redis.call("PEXPIRE", KEYS[1], retention)
  return "EXPIRED"
end

redis.call("HSET", KEYS[1], "state", "COMMITTED", "committedAt", string.format("%.0f", now))
redis.call("PEXPIRE", KEYS[1], retention)
return "APPLIED"
