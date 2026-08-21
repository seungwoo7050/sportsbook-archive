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
local errorText = typeError(bets, "zset") or typeError(selectionEntries, "zset")
  or typeError(selectionSum, "string") or typeError(KEYS[2], "string")
if errorText then return redis.error_reply(errorText) end
local expectedStakes, expectedStakeCards, expectedSelectionCards = {}, {}, {}
local expectedSelections, seenCurrent = 0, false
local activeBetIds = redis.call("ZRANGE", bets, 0, -1)
for _, activeBetId in ipairs(activeBetIds) do
  local lifecycle = "risk:reservation:" .. activeBetId
  if keyType(lifecycle) ~= "hash" then return redis.error_reply("missing active lifecycle") end
  local oldStakeText = redis.call("HGET", lifecycle, "stake")
  local oldCountText = redis.call("HGET", lifecycle, "selectionCount")
  local oldCurrency = redis.call("HGET", lifecycle, "currency")
  local oldStake, oldCount = exact(oldStakeText, true), exact(oldCountText, true)
  local oldSelections = split(redis.call("HGET", lifecycle, "selections"), oldCount or -1)
  if redis.call("HGET", lifecycle, "userId") ~= userId
    or redis.call("HGET", lifecycle, "betId") ~= activeBetId or not oldStake or not oldCount
    or not oldCurrency or not string.match(oldCurrency, "^[A-Z]+$") or not oldSelections then
    return redis.error_reply("corrupt active lifecycle")
  end
  local prefix = base .. ":stakes:" .. string.lower(oldCurrency)
  local entries, sum = prefix .. ":entries", prefix .. ":sum"
  local footprintError = typeError(entries, "zset") or typeError(sum, "string")
  if footprintError or not redis.call("ZSCORE", entries, activeBetId .. "|" .. oldStakeText)
    or not redis.call("ZSCORE", selectionEntries, activeBetId .. "|" .. oldCountText) then
    return redis.error_reply(footprintError or "missing active reservation footprint")
  end
  local expectedStake = expectedStakes[sum] or 0
  if expectedStake > maxExact - oldStake or expectedSelections > maxExact - oldCount then
    return redis.error_reply("active aggregate exceeds exact range")
  end
  expectedStakes[sum], expectedSelections = expectedStake + oldStake, expectedSelections + oldCount
  expectedStakeCards[entries] = (expectedStakeCards[entries] or 0) + 1
  for _, selectionId in ipairs(oldSelections) do
    local key = base .. ":selection:" .. selectionId
    local itemError = typeError(key, "zset")
    if itemError or not redis.call("ZSCORE", key, activeBetId) then
      return redis.error_reply(itemError or "missing per-selection footprint")
    end
    expectedSelectionCards[key] = (expectedSelectionCards[key] or 0) + 1
  end
  if activeBetId == betId then seenCurrent = true end
end
if not seenCurrent then return redis.error_reply("missing active reservation footprint") end
for sum, expected in pairs(expectedStakes) do
  if exact(redis.call("GET", sum), false) ~= expected then
    return redis.error_reply("inconsistent active stake aggregate")
  end
end
for entries, expected in pairs(expectedStakeCards) do
  if redis.call("ZCARD", entries) ~= expected then return redis.error_reply("orphan active stake entry") end
end
if exact(redis.call("GET", selectionSum), false) ~= expectedSelections
  or redis.call("ZCARD", selectionEntries) ~= #activeBetIds then
  return redis.error_reply("inconsistent active selection aggregate")
end
for key, expected in pairs(expectedSelectionCards) do
  if redis.call("ZCARD", key) ~= expected then return redis.error_reply("orphan active selection entry") end
end
local gauge = exact(redis.call("GET", KEYS[2]), false)
if not gauge or gauge < #activeBetIds then return redis.error_reply("corrupt active gauge") end

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
if expiresAt <= now then
  removeActive()
  redis.call("HSET", KEYS[1], "state", "EXPIRED", "expiredAt", string.format("%.0f", now))
  redis.call("PEXPIRE", KEYS[1], retention)
  return "EXPIRED"
end

local function memberAmount(member) return exact(string.match(member, "|([0-9]+)$"), true) end
local function planWindow(entries, sum, amount, window)
  local errorValue = typeError(entries, "zset") or typeError(sum, "string")
  if errorValue then return nil, errorValue end
  local total = 0
  if keyType(entries) ~= "none" then
    total = exact(redis.call("GET", sum), false)
    if not total then return nil, "missing or corrupt rolling sum" end
  end
  local expired = redis.call("ZRANGEBYSCORE", entries, "-inf", now - window)
  for _, member in ipairs(expired) do
    local value = memberAmount(member)
    if not value or total < value then return nil, "corrupt rolling member" end
    total = total - value
  end
  if total > maxExact - amount then return nil, "rolling sum exceeds exact range" end
  local amountText = string.format("%.0f", amount)
  if redis.call("ZSCORE", entries, betId .. "|" .. amountText) then
    return nil, "reservation already exists in committed window"
  end
  return {entries, sum, window, amountText, total + amount}
end

local limitBase, plans = "risk:limit:{" .. userId .. "}:", {}
local currencyLower = string.lower(currency)
local dimensions = {{"stake-daily:" .. currencyLower, ARGV[5], stake},
  {"stake-weekly:" .. currencyLower, ARGV[6], stake}, {"stake-monthly:" .. currencyLower, ARGV[7], stake},
  {"selections-per-minute", ARGV[8], count}}
for _, dimension in ipairs(dimensions) do
  local prefix = limitBase .. dimension[1]
  local plan, planError = planWindow(prefix .. ":entries", prefix .. ":sum", dimension[3], tonumber(dimension[2]))
  if planError then return redis.error_reply(planError) end
  table.insert(plans, plan)
end

local historyBase = "risk:history:{" .. userId .. "}"
local historyBets, historyStakes = historyBase .. ":bets", historyBase .. ":stakes:" .. currencyLower
local historyError = typeError(historyBets, "zset") or typeError(historyStakes, "zset")
if historyError then return redis.error_reply(historyError) end
for _, selectionId in ipairs(selections) do
  local itemError = typeError(historyBase .. ":selection:" .. selectionId, "zset")
  if itemError then return redis.error_reply(itemError) end
end

removeActive()
for _, plan in ipairs(plans) do
  redis.call("ZREMRANGEBYSCORE", plan[1], "-inf", now - plan[3])
  redis.call("ZADD", plan[1], now, betId .. "|" .. plan[4])
  redis.call("SET", plan[2], string.format("%.0f", plan[5]), "PX", plan[3] + 300000)
  redis.call("PEXPIRE", plan[1], plan[3] + 300000)
end
local historyTtl, stakeLimit = tonumber(ARGV[11]), tonumber(ARGV[12])
redis.call("ZREMRANGEBYSCORE", historyBets, "-inf", now - tonumber(ARGV[9]))
redis.call("ZADD", historyBets, "NX", now, betId)
redis.call("ZADD", historyStakes, "NX", now, betId .. "|" .. stakeText)
local stakeCard = redis.call("ZCARD", historyStakes)
if stakeCard > stakeLimit then redis.call("ZREMRANGEBYRANK", historyStakes, 0, stakeCard - stakeLimit - 1) end
for _, selectionId in ipairs(selections) do
  local key = historyBase .. ":selection:" .. selectionId
  redis.call("ZREMRANGEBYSCORE", key, "-inf", now - tonumber(ARGV[10]))
  redis.call("ZADD", key, "NX", now, betId); redis.call("PEXPIRE", key, historyTtl)
end
redis.call("PEXPIRE", historyBets, historyTtl); redis.call("PEXPIRE", historyStakes, historyTtl)
redis.call("HSET", KEYS[1], "state", "COMMITTED", "committedAt", string.format("%.0f", now))
redis.call("PEXPIRE", KEYS[1], retention)
return "APPLIED"
