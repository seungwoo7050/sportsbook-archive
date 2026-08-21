local maxExact = 9007199254740991
local now, lease, retention = tonumber(ARGV[2]), tonumber(ARGV[3]), tonumber(ARGV[4])
local fingerprint, userId, betId = ARGV[5], ARGV[6], ARGV[7]
local stakeText, currency, countText = ARGV[8], ARGV[9], ARGV[10]
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
end
local function checkedAdd(left, right)
  if left > maxExact - right then return nil end
  return left + right
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
local function response(payload)
  payload.version, payload.expired = "1", string.format("%.0f", expiredCount)
  return cjson.encode(payload)
end

local stake, selectionCount = exact(stakeText, true), exact(countText, true)
if ARGV[1] ~= "1" or not exact(ARGV[2], false) or not exact(ARGV[3], true)
  or not exact(ARGV[4], true) or retention <= lease or not stake or not selectionCount
  or not fingerprint or not string.match(fingerprint, "^[0-9a-f]+$") or #fingerprint ~= 64
  or #KEYS ~= 18 + selectionCount * 2 or #ARGV ~= 33 + selectionCount then
  return redis.error_reply("invalid reservation request")
end
for _, index in ipairs({8, 10, 16, 17, 18, 19, 21, 22, 25, 26, 29, 30, 32, 33}) do
  if not exact(ARGV[index], true) then return redis.error_reply("invalid reservation number") end
end
for index = 11, 15 do
  if not exact(ARGV[index], false) then return redis.error_reply("invalid reservation limit") end
end
for _, index in ipairs({20, 24, 28}) do
  if ARGV[index] ~= "0" and ARGV[index] ~= "1" then
    return redis.error_reply("invalid reservation policy flag")
  end
end
if not string.match(userId or "", "^[0-9a-f%-]+$")
  or not string.match(betId or "", "^[0-9a-f%-]+$")
  or not string.match(currency or "", "^[A-Z]+$") then
  return redis.error_reply("invalid reservation identity")
end
local selections, seen = {}, {}
for index = 1, selectionCount do
  local selectionId = ARGV[33 + index]
  if not selectionId or seen[selectionId] or not string.match(selectionId, "^[0-9a-f%-]+$") then
    return redis.error_reply("invalid selection")
  end
  seen[selectionId] = true; table.insert(selections, selectionId)
end
local acceptedKey = "risk:event:fingerprint:" .. betId
local errorText = typeError(KEYS[1], "hash") or typeError(KEYS[2], "zset")
  or typeError(KEYS[3], "zset") or typeError(KEYS[4], "string")
  or typeError(KEYS[5], "zset") or typeError(KEYS[6], "string")
  or typeError(KEYS[7], "hash") or typeError(KEYS[18], "string")
  or typeError(acceptedKey, "string")
if errorText then return redis.error_reply(errorText) end
if redis.call("EXISTS", acceptedKey) == 1 then
  return response({status = "CONFLICT", replayed = false})
end

local activeBase, cleanups, stakeDecrements =
  "risk:reservations:user:{" .. userId .. "}", {}, {}
local selectionDecrement, expectedSelections = 0, 0
local expectedStakes = {[KEYS[4]] = 0}
local expectedStakeEntries = {[KEYS[3]] = 0}
local expectedSelectionEntries = {}
for index = 1, selectionCount do
  expectedSelectionEntries[KEYS[18 + selectionCount + index]] = 0
end
local activeBetIds = redis.call("ZRANGE", KEYS[2], 0, -1)
for _, activeBetId in ipairs(activeBetIds) do
  local lifecycle = "risk:reservation:" .. activeBetId
  if keyType(lifecycle) ~= "hash" then return redis.error_reply("missing active lifecycle") end
  local state = redis.call("HGET", lifecycle, "state")
  local expiresAt = exact(redis.call("HGET", lifecycle, "expiresAt"), false)
  if not expiresAt then return redis.error_reply("corrupt active expiry") end
  local oldStakeText = redis.call("HGET", lifecycle, "stake")
  local oldCountText = redis.call("HGET", lifecycle, "selectionCount")
  local oldCurrency = redis.call("HGET", lifecycle, "currency")
  local oldStake, oldCount = exact(oldStakeText, true), exact(oldCountText, true)
  local oldSelections = split(redis.call("HGET", lifecycle, "selections"), oldCount or -1)
  if redis.call("HGET", lifecycle, "userId") ~= userId or not oldStake
    or not oldCount or not oldCurrency or not oldSelections then
    return redis.error_reply("corrupt active lifecycle")
  end
  if not string.match(oldCurrency, "^[A-Z]+$") then return redis.error_reply("corrupt currency") end
  local stakeBase = activeBase .. ":stakes:" .. string.lower(oldCurrency)
  local entries, sum = stakeBase .. ":entries", stakeBase .. ":sum"
  local cleanupError = typeError(entries, "zset") or typeError(sum, "string")
  if cleanupError or not redis.call("ZSCORE", entries, activeBetId .. "|" .. oldStakeText)
    or not redis.call("ZSCORE", KEYS[5], activeBetId .. "|" .. oldCountText) then
    return redis.error_reply(cleanupError or "missing active footprint")
  end
  local nextExpectedStake = checkedAdd(expectedStakes[sum] or 0, oldStake)
  local nextExpectedSelections = checkedAdd(expectedSelections, oldCount)
  if not nextExpectedStake or not nextExpectedSelections then
    return redis.error_reply("active footprint exceeds exact range")
  end
  expectedStakes[sum], expectedSelections = nextExpectedStake, nextExpectedSelections
  expectedStakeEntries[entries] = (expectedStakeEntries[entries] or 0) + 1
  for _, selectionId in ipairs(oldSelections) do
    local key = activeBase .. ":selection:" .. selectionId
    local selectionError = typeError(key, "zset")
    if selectionError or not redis.call("ZSCORE", key, activeBetId) then
      return redis.error_reply(selectionError or "missing selection footprint")
    end
    expectedSelectionEntries[key] = (expectedSelectionEntries[key] or 0) + 1
  end
  if state ~= "RESERVED" or expiresAt <= now then
    table.insert(cleanups, {activeBetId, lifecycle, state, oldStakeText, oldCountText,
      entries, sum, oldSelections})
    local previous = stakeDecrements[sum] or 0
    if previous > maxExact - oldStake or selectionDecrement > maxExact - oldCount then
      return redis.error_reply("active cleanup exceeds exact range")
    end
    stakeDecrements[sum] = previous + oldStake; selectionDecrement = selectionDecrement + oldCount
  end
end
for sum, value in pairs(expectedStakes) do
  if exact(redis.call("GET", sum) or "0", false) ~= value then
    return redis.error_reply("corrupt active stake aggregate")
  end
end
for entries, count in pairs(expectedStakeEntries) do
  if redis.call("ZCARD", entries) ~= count then
    return redis.error_reply("corrupt active stake entries")
  end
end
if exact(redis.call("GET", KEYS[6]) or "0", false) ~= expectedSelections then
  return redis.error_reply("corrupt active selection aggregate")
end
if redis.call("ZCARD", KEYS[5]) ~= #activeBetIds then
  return redis.error_reply("corrupt active selection entries")
end
for key, count in pairs(expectedSelectionEntries) do
  if redis.call("ZCARD", key) ~= count then
    return redis.error_reply("corrupt per-selection entries")
  end
end
local activeGauge = exact(redis.call("GET", KEYS[18]) or "0", false)
if not activeGauge or activeGauge < #activeBetIds then
  return redis.error_reply("corrupt active gauge")
end
for sum, value in pairs(stakeDecrements) do
  local current = exact(redis.call("GET", sum), false)
  if not current or current < value then return redis.error_reply("corrupt active stake sum") end
end
if #cleanups > 0 then
  local selectionsTotal, gauge = exact(redis.call("GET", KEYS[6]), false),
    exact(redis.call("GET", KEYS[18]), false)
  if not selectionsTotal or selectionsTotal < selectionDecrement or not gauge or gauge < #cleanups then
    return redis.error_reply("corrupt active totals")
  end
end
for _, item in ipairs(cleanups) do
  redis.call("ZREM", KEYS[2], item[1]); redis.call("ZREM", item[6], item[1] .. "|" .. item[4])
  redis.call("ZREM", KEYS[5], item[1] .. "|" .. item[5])
  for _, selectionId in ipairs(item[8]) do
    local key = activeBase .. ":selection:" .. selectionId
    redis.call("ZREM", key, item[1]); if redis.call("ZCARD", key) == 0 then redis.call("DEL", key) end
  end
  if item[3] == "RESERVED" then
    redis.call("HSET", item[2], "state", "EXPIRED", "expiredAt", string.format("%.0f", now))
    redis.call("PEXPIRE", item[2], retention); expiredCount = expiredCount + 1
  end
end
for sum, value in pairs(stakeDecrements) do decrement(sum, value) end
if selectionDecrement > 0 then decrement(KEYS[6], selectionDecrement) end
if #cleanups > 0 then decrement(KEYS[18], #cleanups) end
if redis.call("ZCARD", KEYS[2]) == 0 then redis.call("DEL", KEYS[2]) end
if redis.call("ZCARD", KEYS[5]) == 0 then redis.call("DEL", KEYS[5]) end
for _, item in ipairs(cleanups) do
  if redis.call("ZCARD", item[6]) == 0 then redis.call("DEL", item[6]) end
end
local existing = redis.call("HGET", KEYS[1], "state")
if existing then
  if redis.call("HGET", KEYS[1], "fingerprint") ~= fingerprint then
    return response({status = "CONFLICT", replayed = false})
  end
  local patternsJson = redis.call("HGET", KEYS[1], "patternsJson") or "[]"
  local decoded, patterns = pcall(cjson.decode, patternsJson)
  if not decoded or type(patterns) ~= "table" or string.sub(patternsJson, 1, 1) ~= "[" then
    return redis.error_reply("corrupt reservation patterns")
  end
  if existing == "RESERVED" or existing == "COMMITTED" then
    return response({status = "APPROVED", state = existing,
      expiresAt = redis.call("HGET", KEYS[1], "expiresAt"), token = fingerprint,
      replayed = true, patternsJson = patternsJson})
  end
  if existing == "REJECTED" then
    local rejection = redis.call("HGET", KEYS[1], "rejection")
    if not rejection then return redis.error_reply("corrupt reservation rejection") end
    return response({status = "REJECTED", rejection = rejection,
      replayed = true, patternsJson = patternsJson})
  end
  if existing == "EXPIRED" or existing == "RELEASED" then
    return response({status = "REJECTED", rejection = "RISK_RESERVATION_" .. existing,
      replayed = true, patternsJson = patternsJson})
  end
  return redis.error_reply("unknown reservation state")
end

local function effective(field, fallback)
  return exact(redis.call("HGET", KEYS[7], field) or fallback, false)
end
local function memberAmount(member)
  return exact(string.match(member, "|([0-9]+)$"), true)
end
local function readWindow(entries, sum, window)
  local errorText = typeError(entries, "zset") or typeError(sum, "string")
  if errorText then return nil, errorText end
  local values, actual, removed = redis.call("ZRANGE", entries, 0, -1, "WITHSCORES"), 0, 0
  local cutoff = now - window
  for index = 1, #values, 2 do
    local value, score = memberAmount(values[index]), tonumber(values[index + 1])
    local nextActual = value and checkedAdd(actual, value) or nil
    if not nextActual or not score then return nil, "corrupt rolling member" end
    actual = nextActual
    if score <= cutoff then
      local nextRemoved = checkedAdd(removed, value)
      if not nextRemoved then return nil, "corrupt rolling member" end
      removed = nextRemoved
    end
  end
  local stored = exact(redis.call("GET", sum) or "0", false)
  if not stored or stored ~= actual then return nil, "corrupt rolling aggregate" end
  if removed > 0 then redis.call("ZREMRANGEBYSCORE", entries, "-inf", cutoff) end
  local total = actual - removed
  if total == 0 then redis.call("DEL", entries, sum); return 0, nil end
  redis.call("SET", sum, string.format("%.0f", total), "PX", window + 300000)
  redis.call("PEXPIRE", entries, window + 300000)
  return total, nil
end

local singleLimit = effective("SINGLE_BET_MAX:" .. currency, ARGV[11])
if not singleLimit then return redis.error_reply("corrupt single-bet limit") end
local function persist(state, patternsJson)
  redis.call("HSET", KEYS[1], "state", state, "fingerprint", fingerprint, "token", fingerprint,
    "userId", userId, "betId", betId, "stake", stakeText, "currency", currency,
    "selectionCount", countText, "selections", table.concat(selections, ","),
    "patternsJson", patternsJson)
  redis.call("PEXPIRE", KEYS[1], retention)
end
if stake > singleLimit then
  persist("REJECTED", "[]")
  redis.call("HSET", KEYS[1], "rejection", "SINGLE_BET_MAX_EXCEEDED",
    "rejectedAt", string.format("%.0f", now))
  return response({status = "REJECTED", rejection = "SINGLE_BET_MAX_EXCEEDED",
    replayed = false, patternsJson = "[]"})
end

local activeStake = exact(redis.call("GET", KEYS[4]) or "0", false)
local activeSelections = exact(redis.call("GET", KEYS[6]) or "0", false)
local gauge = exact(redis.call("GET", KEYS[18]) or "0", false)
local nextStake = activeStake and checkedAdd(activeStake, stake) or nil
local nextSelections = activeSelections and checkedAdd(activeSelections, selectionCount) or nil
local nextGauge = gauge and checkedAdd(gauge, 1) or nil
local expiresAt = checkedAdd(now, lease)
if not nextStake or not nextSelections or not nextGauge or not expiresAt then
  return redis.error_reply("active reservation total exceeds exact range")
end
if redis.call("ZSCORE", KEYS[2], betId) or redis.call("ZSCORE", KEYS[3], betId .. "|" .. stakeText)
  or redis.call("ZSCORE", KEYS[5], betId .. "|" .. countText) then
  return redis.error_reply("orphan active reservation footprint")
end
local names = {"STAKE_DAILY", "STAKE_WEEKLY", "STAKE_MONTHLY"}
for index = 1, 3 do
  local committed, committedError = readWindow(KEYS[6 + index * 2], KEYS[7 + index * 2],
    tonumber(ARGV[15 + index]))
  local limit = effective(names[index] .. ":" .. currency, ARGV[11 + index])
  local current = committed and checkedAdd(committed, activeStake) or nil
  local candidate = current and checkedAdd(current, stake) or nil
  if committedError or not limit or not candidate then
    return redis.error_reply(committedError or "invalid rolling capacity")
  end
  if candidate > limit then
    persist("REJECTED", "[]")
    local code = names[index] .. "_LIMIT_EXCEEDED"
    redis.call("HSET", KEYS[1], "rejection", code, "rejectedAt", string.format("%.0f", now))
    return response({status = "REJECTED", rejection = code,
      replayed = false, patternsJson = "[]"})
  end
end
local committedSelections, committedSelectionError =
  readWindow(KEYS[14], KEYS[15], tonumber(ARGV[19]))
local selectionLimit = effective("SELECTIONS_PER_MINUTE", ARGV[15])
local selectionCurrent = committedSelections and checkedAdd(committedSelections, activeSelections) or nil
local selectionCandidate = selectionCurrent and checkedAdd(selectionCurrent, selectionCount) or nil
if committedSelectionError or not selectionLimit or not selectionCandidate then
  return redis.error_reply(committedSelectionError or "invalid selection capacity")
end
if selectionCandidate > selectionLimit then
  persist("REJECTED", "[]")
  redis.call("HSET", KEYS[1], "rejection", "SELECTIONS_PER_MINUTE_LIMIT_EXCEEDED",
    "rejectedAt", string.format("%.0f", now))
  return response({status = "REJECTED", rejection = "SELECTIONS_PER_MINUTE_LIMIT_EXCEEDED",
    replayed = false, patternsJson = "[]"})
end
local function action(value)
  if value == "SUSPECT" or value == "REVIEW" or value == "BLOCK" then return value end
end
local matches = {}
local function addPattern(rule, configuredAction, reason)
  matches[rule] = {rule = rule, action = configuredAction, reason = reason}
end
if ARGV[20] == "1" then
  local rapidError = typeError(KEYS[16], "zset") or typeError(KEYS[2], "zset")
  local rapidWindow, rapidMax, rapidAction = tonumber(ARGV[21]), tonumber(ARGV[22]), action(ARGV[23])
  if rapidError or not rapidWindow or not rapidMax or not rapidAction then
    return redis.error_reply(rapidError or "invalid rapid policy")
  end
  local cutoff = "(" .. (now - rapidWindow)
  local rapidCount = redis.call("ZCOUNT", KEYS[16], cutoff, "+inf")
    + redis.call("ZCOUNT", KEYS[2], cutoff, "+inf") + 1
  if rapidCount >= rapidMax then
    addPattern("RAPID_BETTING", rapidAction, "rapid betting threshold reached")
  end
end
for index = 1, selectionCount do
  local activeKey = KEYS[18 + selectionCount + index]
  local activeError = typeError(activeKey, "zset")
  if activeError or redis.call("ZSCORE", activeKey, betId) then
    return redis.error_reply(activeError or "orphan per-selection footprint")
  end
end
if ARGV[28] == "1" then
  local repeatedWindow, repeatedMax, repeatedAction =
    tonumber(ARGV[29]), tonumber(ARGV[30]), action(ARGV[31])
  if not repeatedWindow or not repeatedMax or not repeatedAction then
    return redis.error_reply("invalid repeated policy")
  end
  for index, selectionId in ipairs(selections) do
    local confirmedKey, activeKey = KEYS[18 + index], KEYS[18 + selectionCount + index]
    local repeatedError = typeError(confirmedKey, "zset") or typeError(activeKey, "zset")
    if repeatedError then return redis.error_reply(repeatedError) end
    local cutoff = "(" .. (now - repeatedWindow)
    local repeatedCount = redis.call("ZCOUNT", confirmedKey, cutoff, "+inf")
      + redis.call("ZCOUNT", activeKey, cutoff, "+inf") + 1
    if repeatedCount > repeatedMax then
      addPattern("REPEATED_SAME_SELECTION", repeatedAction,
        "repeated selection threshold reached: SelectionId[value=" .. selectionId .. "]")
      break
    end
  end
end
local function addText(left, right)
  local output, carry, leftIndex, rightIndex = {}, 0, #left, #right
  while leftIndex > 0 or rightIndex > 0 or carry > 0 do
    local l = leftIndex > 0 and tonumber(string.sub(left, leftIndex, leftIndex)) or 0
    local r = rightIndex > 0 and tonumber(string.sub(right, rightIndex, rightIndex)) or 0
    local sum = l + r + carry
    table.insert(output, 1, tostring(sum % 10)); carry = math.floor(sum / 10)
    leftIndex, rightIndex = leftIndex - 1, rightIndex - 1
  end
  return table.concat(output)
end
local function multiplyText(value, multiplier)
  local output, carry = {}, 0
  for index = #value, 1, -1 do
    local product = tonumber(string.sub(value, index, index)) * multiplier + carry
    table.insert(output, 1, tostring(product % 10)); carry = math.floor(product / 10)
  end
  while carry > 0 do table.insert(output, 1, tostring(carry % 10)); carry = math.floor(carry / 10) end
  return table.concat(output)
end
local function greaterOrEqual(left, right)
  left, right = string.gsub(left, "^0+", ""), string.gsub(right, "^0+", "")
  if #left ~= #right then return #left > #right end
  return left >= right
end
local function suddenMatch()
  if ARGV[24] ~= "1" then return false end
  local errorText = typeError(KEYS[17], "zset") or typeError(KEYS[3], "zset")
  local multiplier, lookback = tonumber(ARGV[25]), tonumber(ARGV[26])
  if errorText or not multiplier or multiplier <= 1 or not lookback or lookback < 1 then
    return nil, errorText or "invalid sudden policy"
  end
  local samples = {}
  for _, key in ipairs({KEYS[17], KEYS[3]}) do
    local values = redis.call("ZRANGE", key, 0, -1, "WITHSCORES")
    for index = 1, #values, 2 do
      local text = string.match(values[index], "|([0-9]+)$")
      local amount = exact(text, true)
      if not amount then return nil, "corrupt sudden stake member" end
      table.insert(samples, {amount = amount, text = text,
        score = tonumber(values[index + 1]), member = values[index]})
    end
  end
  table.sort(samples, function(left, right)
    if left.score == right.score then return left.member < right.member end
    return left.score < right.score
  end)
  if #samples < lookback then return false end
  local recent = {}
  for index = #samples - lookback + 1, #samples do table.insert(recent, samples[index]) end
  table.sort(recent, function(left, right) return left.amount < right.amount end)
  local middle = math.floor(#recent / 2) + 1
  if #recent % 2 == 1 then
    return greaterOrEqual(stakeText, multiplyText(recent[middle].text, multiplier))
  end
  local medianSum = addText(recent[middle - 1].text, recent[middle].text)
  return greaterOrEqual(multiplyText(stakeText, 2), multiplyText(medianSum, multiplier))
end
local sudden, suddenError = suddenMatch()
if suddenError then return redis.error_reply(suddenError) end
if sudden then
  local suddenAction = action(ARGV[27])
  if not suddenAction then return redis.error_reply("invalid sudden action") end
  addPattern("SUDDEN_STAKE_INCREASE", suddenAction, "sudden stake threshold reached")
end
local patterns, firstBlock = {}, nil
for _, rule in ipairs({"RAPID_BETTING", "SUDDEN_STAKE_INCREASE", "REPEATED_SAME_SELECTION"}) do
  local match = matches[rule]
  if match then
    table.insert(patterns, match)
    if match.action == "BLOCK" and not firstBlock then firstBlock = rule end
  end
end
local patternsJson = #patterns == 0 and "[]" or cjson.encode(patterns)
if firstBlock then
  persist("REJECTED", patternsJson)
  redis.call("HSET", KEYS[1], "rejection", firstBlock, "rejectedAt", string.format("%.0f", now))
  return response({status = "REJECTED", rejection = firstBlock,
    replayed = false, patternsJson = patternsJson})
end
persist("RESERVED", patternsJson)
redis.call("HSET", KEYS[1], "reservedAt", string.format("%.0f", now),
  "expiresAt", string.format("%.0f", expiresAt))
redis.call("ZADD", KEYS[2], now, betId); redis.call("ZADD", KEYS[3], now, betId .. "|" .. stakeText)
redis.call("SET", KEYS[4], string.format("%.0f", nextStake))
redis.call("ZADD", KEYS[5], now, betId .. "|" .. countText)
redis.call("SET", KEYS[6], string.format("%.0f", nextSelections))
for index = 1, selectionCount do
  local activeKey = KEYS[18 + selectionCount + index]
  redis.call("ZADD", activeKey, now, betId)
end
redis.call("SET", KEYS[18], string.format("%.0f", nextGauge))
return response({status = "APPROVED", state = "RESERVED",
  expiresAt = string.format("%.0f", expiresAt), token = fingerprint,
  replayed = false, patternsJson = patternsJson})
