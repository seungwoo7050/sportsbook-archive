local now = tonumber(ARGV[1])
local betMember = ARGV[2]
local stakeMember = ARGV[3]
local rapidWindow = tonumber(ARGV[4])
local stakeLimit = tonumber(ARGV[5])
local repeatedWindow = tonumber(ARGV[6])
local ttl = tonumber(ARGV[7])
local maxExact = 9007199254740991

if #KEYS < 3 or not now or now < 0 or now > maxExact then
  return redis.error_reply("invalid history identity")
end
if not betMember or betMember == "" or not stakeMember or stakeMember == "" then
  return redis.error_reply("invalid history member")
end
local encodedAmount = string.match(stakeMember, "|([0-9]+)$")
local amount = encodedAmount and tonumber(encodedAmount) or nil
if not amount or amount <= 0 or amount > maxExact then
  return redis.error_reply("invalid history stake")
end
if not rapidWindow or rapidWindow <= 0 or not stakeLimit or stakeLimit <= 0 then
  return redis.error_reply("invalid history bound")
end
if not repeatedWindow or repeatedWindow <= 0 or not ttl or ttl < repeatedWindow then
  return redis.error_reply("invalid history retention")
end
for _, key in ipairs(KEYS) do
  local keyType = redis.call("TYPE", key).ok
  if keyType ~= "none" and keyType ~= "zset" then
    return redis.error_reply("history key has wrong type")
  end
end

redis.call("ZREMRANGEBYSCORE", KEYS[1], "-inf", now - rapidWindow)
for index = 3, #KEYS do
  redis.call("ZREMRANGEBYSCORE", KEYS[index], "-inf", now - repeatedWindow)
end

local betAdded = redis.call("ZADD", KEYS[1], "NX", now, betMember)
local stakeAdded = redis.call("ZADD", KEYS[2], "NX", now, stakeMember)
for index = 3, #KEYS do
  redis.call("ZADD", KEYS[index], "NX", now, betMember)
end

local stakeCount = redis.call("ZCARD", KEYS[2])
if stakeCount > stakeLimit then
  redis.call("ZREMRANGEBYRANK", KEYS[2], 0, stakeCount - stakeLimit - 1)
end
for _, key in ipairs(KEYS) do
  if redis.call("ZCARD", key) == 0 then
    redis.call("DEL", key)
  else
    redis.call("PEXPIRE", key, ttl)
  end
end
return {tostring(betAdded), tostring(stakeAdded)}
