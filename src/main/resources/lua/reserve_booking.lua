-- Redis 原子预约脚本
-- KEYS[1] = 资源时段剩余库存 key，例如 slot:1:available
-- KEYS[2] = 已预约用户集合 key，例如 slot:1:booked-users
-- ARGV[1] = 当前用户 userId

local available = redis.call('GET', KEYS[1])

-- Redis 中没有库存 key，说明还没有预热
if not available then
    return -2
end

-- 用户已经预约过该时段
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -1
end

-- 库存不足
if tonumber(available) <= 0 then
    return 0
end

-- 原子扣减库存，并记录用户已预约
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])

return 1