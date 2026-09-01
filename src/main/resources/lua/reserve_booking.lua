-- Redis 原子预约脚本
-- KEYS 按 [available, booked-users] 成对传入
-- ARGV[1] = 当前用户 userId

for i = 1, #KEYS, 2 do
    local available = redis.call('GET', KEYS[i])
    if not available then return -2 end
    if redis.call('SISMEMBER', KEYS[i + 1], ARGV[1]) == 1 then return -1 end
    if tonumber(available) <= 0 then return 0 end
end

for i = 1, #KEYS, 2 do
    redis.call('DECR', KEYS[i])
    redis.call('SADD', KEYS[i + 1], ARGV[1])
end

return 1
