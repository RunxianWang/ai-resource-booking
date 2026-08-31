-- KEYS[1] = available stock key
-- KEYS[2] = booked users set
-- ARGV[1] = user id
-- ARGV[2] = total stock ceiling

local removed = redis.call('SREM', KEYS[2], ARGV[1])
if removed == 0 then
    return 0
end

local available = redis.call('GET', KEYS[1])
if not available then
    return 0
end

if tonumber(available) < tonumber(ARGV[2]) then
    redis.call('INCR', KEYS[1])
    return 1
end

return 0
