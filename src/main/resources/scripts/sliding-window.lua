local entriesKey = KEYS[1]
local sumKey = KEYS[2]
local mode = ARGV[1]
local now = tonumber(ARGV[2])
local window = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])
local maxExact = 9007199254740991

if not now or not window or not ttl or window <= 0 or ttl < window then
  return redis.error_reply("invalid sliding-window timing")
end
if mode ~= "READ" and mode ~= "RECORD" then
  return redis.error_reply("invalid sliding-window mode")
end
if redis.call("EXISTS", entriesKey) == 0 then
  redis.call("DEL", sumKey)
  if mode == "READ" then
    return {"0", "0"}
  end
end

local stored = redis.call("GET", sumKey)
local total = stored and tonumber(stored) or 0
if not total or total < 0 or total > maxExact then
  return redis.error_reply("corrupt sliding-window sum")
end

local expired = redis.call("ZRANGEBYSCORE", entriesKey, "-inf", now - window)
for _, member in ipairs(expired) do
  local encoded = string.match(member, "|([0-9]+)$")
  local amount = encoded and tonumber(encoded) or nil
  if not amount or amount <= 0 or amount > maxExact or total < amount then
    return redis.error_reply("corrupt sliding-window member")
  end
  total = total - amount
end
if #expired > 0 then
  redis.call("ZREM", entriesKey, unpack(expired))
end

local added = 0
if mode == "RECORD" then
  local member = ARGV[5]
  local amount = tonumber(ARGV[6])
  if not member or member == "" or not amount or amount <= 0 or amount > maxExact then
    return redis.error_reply("invalid sliding-window entry")
  end
  if total > maxExact - amount then
    return redis.error_reply("sliding-window sum exceeds exact range")
  end
  added = redis.call("ZADD", entriesKey, "NX", now, member)
  if added == 1 then
    total = total + amount
  end
end

if redis.call("ZCARD", entriesKey) == 0 then
  redis.call("DEL", entriesKey, sumKey)
  total = 0
else
  redis.call("SET", sumKey, string.format("%.0f", total), "PX", ttl)
  redis.call("PEXPIRE", entriesKey, ttl)
end
return {string.format("%.0f", total), tostring(added)}
