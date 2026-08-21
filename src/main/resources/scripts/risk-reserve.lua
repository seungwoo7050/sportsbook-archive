local maxExact = 9007199254740991
local now, lease, retention = tonumber(ARGV[2]), tonumber(ARGV[3]), tonumber(ARGV[4])
local fingerprint, userId, betId = ARGV[5], ARGV[6], ARGV[7]
local stakeText, currency, countText = ARGV[8], ARGV[9], ARGV[10]

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
local function response(payload)
  payload.version, payload.expired = "1", "0"
  return cjson.encode(payload)
end

local stake, selectionCount = exact(stakeText, true), exact(countText, true)
if ARGV[1] ~= "1" or not exact(ARGV[2], false) or not exact(ARGV[3], true)
  or not exact(ARGV[4], true) or retention <= lease or not stake or not selectionCount
  or not fingerprint or not string.match(fingerprint, "^[0-9a-f]+$") or #fingerprint ~= 64
  or #KEYS ~= 18 + selectionCount * 2 or #ARGV ~= 33 + selectionCount then
  return redis.error_reply("invalid reservation request")
end
local selections, seen = {}, {}
for index = 1, selectionCount do
  local selectionId = ARGV[33 + index]
  if not selectionId or seen[selectionId] then return redis.error_reply("invalid selection") end
  seen[selectionId] = true; table.insert(selections, selectionId)
end
local errorText = typeError(KEYS[1], "hash") or typeError(KEYS[2], "zset")
  or typeError(KEYS[3], "zset") or typeError(KEYS[4], "string")
  or typeError(KEYS[5], "zset") or typeError(KEYS[6], "string")
  or typeError(KEYS[7], "hash") or typeError(KEYS[18], "string")
if errorText then return redis.error_reply(errorText) end
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
  if keyType(entries) == "none" then redis.call("DEL", sum); return 0, nil end
  local total = exact(redis.call("GET", sum), false)
  if not total then return nil, "missing or corrupt rolling sum" end
  local expired, removed = redis.call("ZRANGEBYSCORE", entries, "-inf", now - window), 0
  for _, member in ipairs(expired) do
    local value = memberAmount(member)
    if not value or removed > maxExact - value then return nil, "corrupt rolling member" end
    removed = removed + value
  end
  if removed > total then return nil, "rolling sum underflow" end
  if #expired > 0 then redis.call("ZREMRANGEBYSCORE", entries, "-inf", now - window) end
  total = total - removed
  if redis.call("ZCARD", entries) == 0 then redis.call("DEL", entries, sum); return 0, nil end
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
persist("RESERVED", "[]")
redis.call("HSET", KEYS[1], "reservedAt", string.format("%.0f", now),
  "expiresAt", string.format("%.0f", expiresAt))
redis.call("ZADD", KEYS[2], now, betId); redis.call("ZADD", KEYS[3], now, betId .. "|" .. stakeText)
redis.call("SET", KEYS[4], string.format("%.0f", nextStake))
redis.call("ZADD", KEYS[5], now, betId .. "|" .. countText)
redis.call("SET", KEYS[6], string.format("%.0f", nextSelections))
redis.call("SET", KEYS[18], string.format("%.0f", nextGauge))
return response({status = "APPROVED", state = "RESERVED",
  expiresAt = string.format("%.0f", expiresAt), token = fingerprint,
  replayed = false, patternsJson = "[]"})
