local maxExact = 9007199254740991
local now = tonumber(ARGV[1])
local currency = ARGV[14]
local count = tonumber(ARGV[15])

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

local function limitSlot(counter, field)
  if not counter.ok then return counter end
  local errorText = typeError(KEYS[9], "hash")
  if errorText then return failure(errorText) end
  local raw = redis.call("HGET", KEYS[9], field)
  if raw and not exact(raw, false) then return failure("corrupt limit override") end
  return {ok = true, committed = counter.value, active = "0", override = raw or cjson.null}
end

if not now or now < 0 or now > maxExact or not count or count < 1
  or #KEYS ~= 17 + count * 2 then
  return redis.error_reply("invalid snapshot request")
end
local limits = {
  STAKE_DAILY = limitSlot(capture(KEYS[1], KEYS[2], tonumber(ARGV[3])),
    "STAKE_DAILY:" .. currency),
  STAKE_WEEKLY = limitSlot(capture(KEYS[3], KEYS[4], tonumber(ARGV[4])),
    "STAKE_WEEKLY:" .. currency),
  STAKE_MONTHLY = limitSlot(capture(KEYS[5], KEYS[6], tonumber(ARGV[5])),
    "STAKE_MONTHLY:" .. currency),
  SELECTIONS_PER_MINUTE = limitSlot(capture(KEYS[7], KEYS[8], tonumber(ARGV[6])),
    "SELECTIONS_PER_MINUTE")
}

local function confirmedCount(key, enabled, window)
  if not enabled then return {ok = true, value = "0"} end
  local errorText = typeError(key, "zset")
  if errorText then return failure(errorText) end
  local value = redis.call("ZCOUNT", key, "(" .. (now - window), "+inf")
  if value > maxExact then return failure("pattern count exceeds exact range") end
  return {ok = true, value = string.format("%.0f", value)}
end

local function confirmedStakes()
  if ARGV[9] ~= "1" then return {ok = true, value = ""} end
  local errorText = typeError(KEYS[16], "zset")
  if errorText then return failure(errorText) end
  local raw = redis.call("ZREVRANGE", KEYS[16], 0, tonumber(ARGV[10]) - 1)
  local values = cjson.decode("[]")
  for index = #raw, 1, -1 do
    local encoded = string.match(raw[index], "|([0-9]+)$")
    if not exact(encoded, true) then return failure("corrupt stake history member") end
    table.insert(values, encoded)
  end
  return {ok = true, value = table.concat(values, ",")}
end

local rapid = confirmedCount(KEYS[15], ARGV[7] == "1", tonumber(ARGV[8]))
local selectionFacts = cjson.decode("[]")
for index = 1, count do
  table.insert(selectionFacts, {selectionId = ARGV[15 + index],
    slot = confirmedCount(KEYS[17 + index], ARGV[11] == "1", tonumber(ARGV[12]))})
end
return cjson.encode({version = "1", expired = "0", limits = limits,
  patterns = {rapid = rapid, stakes = confirmedStakes(), selections = selectionFacts}})
