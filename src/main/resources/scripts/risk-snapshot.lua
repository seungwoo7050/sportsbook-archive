local maxExact = 9007199254740991
local now = tonumber(ARGV[1])
local retention = tonumber(ARGV[2])
local userId = ARGV[13]
local currency = ARGV[14]
local count = tonumber(ARGV[15])
local expiredCount = 0

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
  return nil
end

local function failure(message) return {ok = false, error = message} end

local function split(encoded, expected)
  local values = {}
  for value in string.gmatch(encoded or "", "[^,]+") do table.insert(values, value) end
  if #values ~= expected then return nil end
  return values
end

local function decrement(key, amount)
  local current = exact(redis.call("GET", key), false)
  local nextValue = current - amount
  if nextValue == 0 then redis.call("DEL", key)
  else redis.call("SET", key, string.format("%.0f", nextValue)) end
end

local function amount(member)
  return exact(string.match(member, "|([0-9]+)$"), true)
end

local function capture(entries, sum, window)
  local errorText = typeError(entries, "zset") or typeError(sum, "string")
  if errorText then return failure(errorText) end
  if keyType(entries) == "none" then
    redis.call("DEL", sum)
    return {ok = true, value = "0"}
  end
  local total = exact(redis.call("GET", sum), false)
  if not total then return failure("missing or corrupt rolling sum") end
  local expired = redis.call("ZRANGEBYSCORE", entries, "-inf", now - window)
  local removed = 0
  for _, member in ipairs(expired) do
    local value = amount(member)
    if not value or removed > maxExact - value then return failure("corrupt rolling member") end
    removed = removed + value
  end
  if removed > total then return failure("rolling sum underflow") end
  if #expired > 0 then redis.call("ZREMRANGEBYSCORE", entries, "-inf", now - window) end
  total = total - removed
  if redis.call("ZCARD", entries) == 0 then
    redis.call("DEL", entries, sum)
    total = 0
  else
    redis.call("SET", sum, string.format("%.0f", total), "PX", window + 300000)
    redis.call("PEXPIRE", entries, window + 300000)
  end
  return {ok = true, value = string.format("%.0f", total)}
end

local function active(entries, sum)
  local errorText = typeError(entries, "zset") or typeError(sum, "string")
  if errorText then return nil, errorText end
  if keyType(entries) == "none" then redis.call("DEL", sum); return "0", nil end
  local value = exact(redis.call("GET", sum), false)
  if not value then return nil, "missing or corrupt active sum" end
  return string.format("%.0f", value), nil
end

local function limitSlot(counter, activeValue, activeError, field)
  if not counter.ok then return counter end
  if activeError then return failure(activeError) end
  local errorText = typeError(KEYS[9], "hash")
  if errorText then return failure(errorText) end
  local raw = redis.call("HGET", KEYS[9], field)
  if raw and not exact(raw, false) then return failure("corrupt limit override") end
  return {ok = true, committed = counter.value, active = activeValue,
    override = raw or cjson.null}
end

if not now or now < 0 or now > maxExact or not count or count < 1
  or not retention or retention <= 0 or #KEYS ~= 17 + count * 2 then
  return redis.error_reply("invalid snapshot request")
end
for _, index in ipairs({3, 4, 5, 6, 8, 10, 12}) do
  local value = tonumber(ARGV[index])
  if not value or value <= 0 or value > maxExact then
    return redis.error_reply("invalid snapshot policy")
  end
end
for _, index in ipairs({7, 9, 11}) do
  if ARGV[index] ~= "0" and ARGV[index] ~= "1" then
    return redis.error_reply("invalid snapshot policy flag")
  end
end
if not userId or not string.match(userId, "^[0-9a-f%-]+$")
  or not currency or not string.match(currency, "^[A-Z]+$") then
  return redis.error_reply("invalid snapshot identity")
end
for index = 1, count do
  if not string.match(ARGV[15 + index] or "", "^[0-9a-f%-]+$") then
    return redis.error_reply("invalid snapshot selection")
  end
end

local activeBase = "risk:reservations:user:{" .. userId .. "}"
local plans, stakeTotals, expectedStakes = {}, {}, {}
local expectedSelections, selectionTotal, expectedSelectionCards = 0, 0, {}
local cleanupError = typeError(KEYS[10], "zset") or typeError(KEYS[13], "zset")
  or typeError(KEYS[14], "string") or typeError(KEYS[17], "string")
if cleanupError then return redis.error_reply(cleanupError) end
local activeBetIds = redis.call("ZRANGE", KEYS[10], 0, -1)
for _, activeBetId in ipairs(activeBetIds) do
  local lifecycle = "risk:reservation:" .. activeBetId
  if keyType(lifecycle) ~= "hash" then return redis.error_reply("missing lifecycle") end
  local state = redis.call("HGET", lifecycle, "state")
  local expiresAt = exact(redis.call("HGET", lifecycle, "expiresAt"), false)
  if not expiresAt then return redis.error_reply("corrupt lifecycle expiry") end
  local stakeText = redis.call("HGET", lifecycle, "stake")
  local countText = redis.call("HGET", lifecycle, "selectionCount")
  local oldCurrency = redis.call("HGET", lifecycle, "currency")
  local stake, oldCount = exact(stakeText, true), exact(countText, true)
  local oldSelections = split(redis.call("HGET", lifecycle, "selections"), oldCount or -1)
  if redis.call("HGET", lifecycle, "userId") ~= userId or not stake or not oldCount
    or not oldCurrency or not string.match(oldCurrency, "^[A-Z]+$") or not oldSelections then
    return redis.error_reply("corrupt active lifecycle")
  end
  local stakeBase = activeBase .. ":stakes:" .. string.lower(oldCurrency)
  local entries, sum = stakeBase .. ":entries", stakeBase .. ":sum"
  local planError = typeError(entries, "zset") or typeError(sum, "string")
  if planError or not redis.call("ZSCORE", entries, activeBetId .. "|" .. stakeText)
    or not redis.call("ZSCORE", KEYS[13], activeBetId .. "|" .. countText) then
    return redis.error_reply(planError or "missing active footprint")
  end
  local expected = expectedStakes[sum] or {entries = entries, amount = 0, count = 0}
  if expected.amount > maxExact - stake or expectedSelections > maxExact - oldCount then
    return redis.error_reply("active aggregate exceeds exact range")
  end
  expected.amount, expected.count = expected.amount + stake, expected.count + 1
  expectedStakes[sum], expectedSelections = expected, expectedSelections + oldCount
  for _, selectionId in ipairs(oldSelections) do
    local selectionKey = activeBase .. ":selection:" .. selectionId
    local selectionError = typeError(selectionKey, "zset")
    if selectionError or not redis.call("ZSCORE", selectionKey, activeBetId) then
      return redis.error_reply(selectionError or "missing selection footprint")
    end
    expectedSelectionCards[selectionKey] = (expectedSelectionCards[selectionKey] or 0) + 1
  end
  if state ~= "RESERVED" or expiresAt <= now then
    table.insert(plans, {activeBetId, lifecycle, state, stakeText, countText,
      entries, sum, oldSelections})
    stakeTotals[sum] = (stakeTotals[sum] or 0) + stake
    selectionTotal = selectionTotal + oldCount
  end
end
for sum, expected in pairs(expectedStakes) do
  local current = exact(redis.call("GET", sum), false)
  if current ~= expected.amount or redis.call("ZCARD", expected.entries) ~= expected.count then
    return redis.error_reply("inconsistent active stake aggregate")
  end
end
if not expectedStakes[KEYS[12]]
  and (keyType(KEYS[11]) ~= "none" or keyType(KEYS[12]) ~= "none") then
  return redis.error_reply("orphan active stake aggregate")
end
local currentSelections = exact(redis.call("GET", KEYS[14]), false)
if #activeBetIds == 0 then
  if keyType(KEYS[13]) ~= "none" or keyType(KEYS[14]) ~= "none" then
    return redis.error_reply("orphan active selection aggregate")
  end
elseif currentSelections ~= expectedSelections or redis.call("ZCARD", KEYS[13]) ~= #activeBetIds then
  return redis.error_reply("inconsistent active selection aggregate")
end
for selectionKey, expected in pairs(expectedSelectionCards) do
  if redis.call("ZCARD", selectionKey) ~= expected then
    return redis.error_reply("inconsistent active selection footprint")
  end
end
for index = 1, count do
  local selectionKey = KEYS[17 + count + index]
  if typeError(selectionKey, "zset")
    or redis.call("ZCARD", selectionKey) ~= (expectedSelectionCards[selectionKey] or 0) then
    return redis.error_reply("orphan active selection footprint")
  end
end
local gauge = exact(redis.call("GET", KEYS[17]), false)
if keyType(KEYS[17]) ~= "none" and not gauge then
  return redis.error_reply("corrupt active gauge")
end
if #activeBetIds > 0 and (not gauge or gauge < #activeBetIds) then
  return redis.error_reply("corrupt active gauge")
end
for _, plan in ipairs(plans) do
  redis.call("ZREM", KEYS[10], plan[1])
  redis.call("ZREM", plan[6], plan[1] .. "|" .. plan[4])
  redis.call("ZREM", KEYS[13], plan[1] .. "|" .. plan[5])
  for _, selectionId in ipairs(plan[8]) do
    redis.call("ZREM", activeBase .. ":selection:" .. selectionId, plan[1])
  end
  if plan[3] == "RESERVED" then
    redis.call("HSET", plan[2], "state", "EXPIRED", "expiredAt", string.format("%.0f", now))
    redis.call("PEXPIRE", plan[2], retention)
    expiredCount = expiredCount + 1
  end
end
for sum, value in pairs(stakeTotals) do decrement(sum, value) end
if selectionTotal > 0 then decrement(KEYS[14], selectionTotal) end
if #plans > 0 then decrement(KEYS[17], #plans) end
local activeStake, activeStakeError = active(KEYS[11], KEYS[12])
local activeSelections, activeSelectionError = active(KEYS[13], KEYS[14])
local limits = {
  STAKE_DAILY = limitSlot(capture(KEYS[1], KEYS[2], tonumber(ARGV[3])),
    activeStake, activeStakeError,
    "STAKE_DAILY:" .. currency),
  STAKE_WEEKLY = limitSlot(capture(KEYS[3], KEYS[4], tonumber(ARGV[4])),
    activeStake, activeStakeError,
    "STAKE_WEEKLY:" .. currency),
  STAKE_MONTHLY = limitSlot(capture(KEYS[5], KEYS[6], tonumber(ARGV[5])),
    activeStake, activeStakeError,
    "STAKE_MONTHLY:" .. currency),
  SELECTIONS_PER_MINUTE = limitSlot(capture(KEYS[7], KEYS[8], tonumber(ARGV[6])),
    activeSelections, activeSelectionError,
    "SELECTIONS_PER_MINUTE")
}

local function patternCount(confirmed, activeKey, enabled, window)
  if not enabled then return {ok = true, value = "0"} end
  local errorText = typeError(confirmed, "zset") or typeError(activeKey, "zset")
  if errorText then return failure(errorText) end
  local cutoff = "(" .. (now - window)
  local value = redis.call("ZCOUNT", confirmed, cutoff, "+inf")
    + redis.call("ZCOUNT", activeKey, cutoff, "+inf")
  if value > maxExact then return failure("pattern count exceeds exact range") end
  return {ok = true, value = string.format("%.0f", value)}
end

local function confirmedStakes()
  if ARGV[9] ~= "1" then return {ok = true, value = ""} end
  local errorText = typeError(KEYS[16], "zset") or typeError(KEYS[11], "zset")
  if errorText then return failure(errorText) end
  local samples = {}
  for _, key in ipairs({KEYS[16], KEYS[11]}) do
    local raw = redis.call("ZRANGE", key, 0, -1, "WITHSCORES")
    for index = 1, #raw, 2 do
      local encoded = string.match(raw[index], "|([0-9]+)$")
      if not exact(encoded, true) then return failure("corrupt stake history member") end
      table.insert(samples, {value = encoded, score = tonumber(raw[index + 1]), member = raw[index]})
    end
  end
  table.sort(samples, function(left, right)
    if left.score == right.score then return left.member < right.member end
    return left.score < right.score
  end)
  local values = cjson.decode("[]")
  local first = math.max(1, #samples - tonumber(ARGV[10]) + 1)
  for index = first, #samples do
    table.insert(values, samples[index].value)
  end
  return {ok = true, value = table.concat(values, ",")}
end

local rapid = patternCount(KEYS[15], KEYS[10], ARGV[7] == "1", tonumber(ARGV[8]))
local selectionFacts = cjson.decode("[]")
for index = 1, count do
  table.insert(selectionFacts, {selectionId = ARGV[15 + index],
    slot = patternCount(KEYS[17 + index], KEYS[17 + count + index],
      ARGV[11] == "1", tonumber(ARGV[12]))})
end
return cjson.encode({version = "1", expired = string.format("%.0f", expiredCount), limits = limits,
  patterns = {rapid = rapid, stakes = confirmedStakes(), selections = selectionFacts}})
