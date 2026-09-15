-- ============================================================
-- 区间原子扣减（防超卖 / 防「只扣一半晚」）
-- KEYS[i] = inv:{roomTypeId}:{yyyyMMdd}  每一晚一个 key
-- ARGV[1] = 间数
-- 返回 1 成功 / 0 库存不足
-- 面试要点：先全部校验，再统一 DECRBY；中间失败不会留下半扣状态。
-- ============================================================

local rooms = tonumber(ARGV[1])
if rooms == nil or rooms < 1 then
    return 0
end

-- ===== 第一遍：校验入住区间内每一晚都够 =====
for i = 1, #KEYS do
    local avail = tonumber(redis.call('GET', KEYS[i]) or '-1')
    if avail < rooms then
        return 0
    end
end

-- ===== 第二遍：全部够才统一扣减 =====
for i = 1, #KEYS do
    redis.call('DECRBY', KEYS[i], rooms)
end
return 1
