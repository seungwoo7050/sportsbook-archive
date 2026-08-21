local maxExact, now, retention = 9007199254740991, tonumber(ARGV[2]), tonumber(ARGV[3])
local token = ARGV[4]
local function exact(text, positive)
  if not text or not string.match(text, "^%d+$") then return nil end
  local value = tonumber(text)
  if not value or value > maxExact or (positive and value <= 0) then return nil end
  return value
end
local function keyType(key) return redis.call("TYPE", key).ok end
local function typeError(key, expected)
  local actual = keyType(key)
  if actual ~= "none" and actual ~= expected then return "wrong Redis type for " .. key end
end
local function split(encoded, expected)
  local values = {}
  for value in string.gmatch(encoded or "", "[^,]+") do table.insert(values, value) end
  if #values == expected then return values end
end
local function decrement(key, amount)
  local nextValue = exact(redis.call("GET", key), false) - amount
  if nextValue == 0 then redis.call("DEL", key)
  else redis.call("SET", key, string.format("%.0f", nextValue)) end
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
if state == "EXPIRED" or state == "RELEASED" or state == "REJECTED" then return "TOMBSTONED" end
if state ~= "RESERVED" then return redis.error_reply("unknown reservation state") end

local userId, betId = redis.call("HGET", KEYS[1], "userId"), redis.call("HGET", KEYS[1], "betId")
local stakeText, currency = redis.call("HGET", KEYS[1], "stake"), redis.call("HGET", KEYS[1], "currency")
local countText = redis.call("HGET", KEYS[1], "selectionCount")
local stake, count = exact(stakeText, true), exact(countText, true)
local expiresAt = exact(redis.call("HGET", KEYS[1], "expiresAt"), false)
local selections = split(redis.call("HGET", KEYS[1], "selections"), count or -1)
if not userId or not betId or not stake or not currency or not count or not expiresAt or not selections
  or not string.match(userId, "^[0-9a-f%-]+$") or not string.match(betId, "^[0-9a-f%-]+$")
  or not string.match(currency, "^[A-Z]+$") then return redis.error_reply("corrupt reservation identity") end

local base = "risk:reservations:user:{" .. userId .. "}"
local bets, stakeEntries = base .. ":bets", base .. ":stakes:" .. string.lower(currency) .. ":entries"
local stakeSum = base .. ":stakes:" .. string.lower(currency) .. ":sum"
local selectionEntries, selectionSum = base .. ":selections:entries", base .. ":selections:sum"
local errorText = typeError(bets, "zset") or typeError(stakeEntries, "zset")
  or typeError(stakeSum, "string") or typeError(selectionEntries, "zset")
  or typeError(selectionSum, "string") or typeError(KEYS[2], "string")
if errorText or not redis.call("ZSCORE", bets, betId)
  or not redis.call("ZSCORE", stakeEntries, betId .. "|" .. stakeText)
  or not redis.call("ZSCORE", selectionEntries, betId .. "|" .. countText) then
  return redis.error_reply(errorText or "missing active reservation footprint")
end
for _, selectionId in ipairs(selections) do
  local key, itemError = base .. ":selection:" .. selectionId
  itemError = typeError(key, "zset")
  if itemError or not redis.call("ZSCORE", key, betId) then
    return redis.error_reply(itemError or "missing per-selection footprint")
  end
end
local stakeTotal, selectionTotal = exact(redis.call("GET", stakeSum), false), exact(redis.call("GET", selectionSum), false)
local gauge = exact(redis.call("GET", KEYS[2]), false)
if not stakeTotal or stakeTotal < stake or not selectionTotal or selectionTotal < count
  or not gauge or gauge < 1 then return redis.error_reply("corrupt active total") end

local function removeActive()
  redis.call("ZREM", bets, betId); redis.call("ZREM", stakeEntries, betId .. "|" .. stakeText)
  redis.call("ZREM", selectionEntries, betId .. "|" .. countText)
  for _, selectionId in ipairs(selections) do
    local key = base .. ":selection:" .. selectionId
    redis.call("ZREM", key, betId); if redis.call("ZCARD", key) == 0 then redis.call("DEL", key) end
  end
  decrement(stakeSum, stake); decrement(selectionSum, count); decrement(KEYS[2], 1)
  if redis.call("ZCARD", bets) == 0 then redis.call("DEL", bets) end
  if redis.call("ZCARD", stakeEntries) == 0 then redis.call("DEL", stakeEntries) end
  if redis.call("ZCARD", selectionEntries) == 0 then redis.call("DEL", selectionEntries) end
end

removeActive()
if expiresAt <= now then
  redis.call("HSET", KEYS[1], "state", "EXPIRED", "expiredAt", string.format("%.0f", now))
  redis.call("PEXPIRE", KEYS[1], retention)
  return "EXPIRED"
end
redis.call("HSET", KEYS[1], "state", "COMMITTED", "committedAt", string.format("%.0f", now))
redis.call("PEXPIRE", KEYS[1], retention)
return "APPLIED"
