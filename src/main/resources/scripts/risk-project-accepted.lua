local maxExact = 9007199254740991
local now, retention = tonumber(ARGV[2]), tonumber(ARGV[3])
local fingerprint, userId, betId = ARGV[4], ARGV[5], ARGV[6]
local stake, currency, selectionCount = tonumber(ARGV[7]), ARGV[8], tonumber(ARGV[9])

local function exact(text, positive)
  if not text or not string.match(text, "^%d+$") then return nil end
  local value = tonumber(text)
  if not value or value > maxExact or (positive and value <= 0) then return nil end
  return value
end
local function keyType(key) return redis.call("TYPE", key).ok end
local function typeError(key, expected)
  local actual = keyType(key)
  if actual ~= "none" and actual ~= expected then
    return "wrong Redis type for " .. key
  end
end
local function split(encoded, expected)
  local values = {}
  for value in string.gmatch(encoded or "", "[^,]+") do table.insert(values, value) end
  if #values == expected then return values end
end

if ARGV[1] ~= "1" or #KEYS ~= 2 or #ARGV ~= 18
  or not exact(ARGV[2], false) or not exact(ARGV[3], true)
  or not fingerprint or not string.match(fingerprint, "^[0-9a-f]+$") or #fingerprint ~= 64
  or not userId or not betId or not string.match(userId, "^[0-9a-f%-]+$")
  or not string.match(betId, "^[0-9a-f%-]+$") or not exact(ARGV[7], true)
  or not currency or not string.match(currency, "^[A-Z]+$")
  or not exact(ARGV[9], true) or not split(ARGV[10], selectionCount or -1) then
  return redis.error_reply("invalid accepted projection request")
end
for index = 11, 18 do
  if not exact(ARGV[index], true) then return redis.error_reply("invalid projection policy") end
end

if keyType(KEYS[1]) ~= "none" then
  return redis.error_reply("reservation lifecycle appeared before accepted projection")
end
local markerError = typeError(KEYS[2], "string")
if markerError then return redis.error_reply(markerError) end
local retained = redis.call("GET", KEYS[2])
if retained then
  if retained == fingerprint then return "REPLAYED" end
  return "CONFLICT"
end

local selections = split(ARGV[10], selectionCount)
local function memberAmount(member)
  return exact(string.match(member, "|([0-9]+)$"), true)
end
local function planWindow(entries, sum, amount, window)
  local errorText = typeError(entries, "zset") or typeError(sum, "string")
  if errorText then return nil, errorText end
  local total = 0
  if keyType(entries) ~= "none" then
    total = exact(redis.call("GET", sum), false)
    if not total then return nil, "missing or corrupt rolling sum" end
    local calculated = 0
    for _, member in ipairs(redis.call("ZRANGE", entries, 0, -1)) do
      local amount = memberAmount(member)
      if not amount or calculated > maxExact - amount then
        return nil, "corrupt rolling member"
      end
      calculated = calculated + amount
    end
    if calculated ~= total then return nil, "inconsistent rolling sum" end
  end
  local expired = redis.call("ZRANGEBYSCORE", entries, "-inf", now - window)
  for _, member in ipairs(expired) do
    local amount = memberAmount(member)
    if not amount or total < amount then return nil, "corrupt rolling member" end
    total = total - amount
  end
  if total > maxExact - amount then return nil, "rolling sum exceeds exact range" end
  local amountText = string.format("%.0f", amount)
  if redis.call("ZSCORE", entries, betId .. "|" .. amountText) then
    return nil, "accepted projection already exists in rolling window"
  end
  return {entries, sum, window, amountText, total + amount}
end
