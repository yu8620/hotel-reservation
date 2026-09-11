-- 区间原子扣减：入住期内每一晚都够才扣，否则全部不动。
-- KEYS[i] = inv:{roomTypeId}:{yyyyMMdd}
-- ARGV[1] = 间数
-- 返回 1 成功 / 0 库存不足
local rooms = tonumber(ARGV[1])
if rooms == nil or rooms < 1 then
    return 0
end
for i = 1, #KEYS do
    local avail = tonumber(redis.call('GET', KEYS[i]) or '-1')
    if avail < rooms then
        return 0
    end
end
for i = 1, #KEYS do
    redis.call('DECRBY', KEYS[i], rooms)
end
return 1
