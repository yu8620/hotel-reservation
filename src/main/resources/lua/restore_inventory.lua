-- 回补库存：关单 / 取消 / DB 扣减失败补偿。
-- KEYS[i] = inv:{roomTypeId}:{yyyyMMdd}
-- ARGV[1] = 间数
local rooms = tonumber(ARGV[1])
if rooms == nil or rooms < 1 then
    return 0
end
for i = 1, #KEYS do
    redis.call('INCRBY', KEYS[i], rooms)
end
return 1
