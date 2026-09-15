package org.example.hotelreservation.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hotelreservation.common.BizException;
import org.example.hotelreservation.common.ResultCode;
import org.example.hotelreservation.entity.RoomInventory;
import org.example.hotelreservation.mapper.RoomInventoryMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 日历库存网关。
 *
 * <p>权威数据在 MySQL：{@code UPDATE ... WHERE available >= n} 保证不超订。
 * Redis + Lua 负责把绝大多数「没房」请求挡在数据库之外，并保证跨多晚要么全扣要么全不扣。
 *
 * <p>崩溃语义（面试高频）：
 * <ul>
 *   <li>Lua 成功、DB 失败：执行 restore Lua，Redis 回到扣减前。</li>
 *   <li>Lua 成功、进程在写库前宕机：Redis 偏少（少卖），不超订；管理员可 reload。</li>
 *   <li>Redis 宕机：扣减失败关闭（拒绝下单），避免 Redis 已预占/已扣到 0 但 MySQL 未更新时流量打到 DB 超卖；读路径仍可降级 MySQL。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StringRedisTemplate redis;
    private final RoomInventoryMapper inventoryMapper;

    private final DefaultRedisScript<Long> deductScript = new DefaultRedisScript<>();
    private final DefaultRedisScript<Long> restoreScript = new DefaultRedisScript<>();

    // ===== 加载 classpath 下扣减/回补 Lua，启动时绑定到 RedisScript =====
    @PostConstruct
    void initScripts() {
        deductScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/deduct_inventory.lua")));
        deductScript.setResultType(Long.class);
        restoreScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/restore_inventory.lua")));
        restoreScript.setResultType(Long.class);
    }

    public List<RoomInventory> loadRows(Long roomTypeId, List<LocalDate> nights) {
        return inventoryMapper.selectList(new LambdaQueryWrapper<RoomInventory>()
                .eq(RoomInventory::getRoomTypeId, roomTypeId)
                .in(RoomInventory::getStayDate, nights)
                .orderByAsc(RoomInventory::getStayDate));
    }

    /**
     * 预热：只在 key 不存在时写入，避免覆盖正在进行的 Lua 扣减。
     */
    public void warmUp(Long roomTypeId, List<LocalDate> nights) {
        List<RoomInventory> rows = loadRows(roomTypeId, nights);
        Map<LocalDate, RoomInventory> byDate = rows.stream()
                .collect(Collectors.toMap(RoomInventory::getStayDate, Function.identity()));
        for (LocalDate night : nights) {
            RoomInventory row = byDate.get(night);
            if (row == null) {
                throw new BizException(ResultCode.SOLD_OUT, "日期 " + night + " 未开放预订");
            }
            String key = InventoryKeys.available(roomTypeId, night);
            redis.opsForValue().setIfAbsent(key, String.valueOf(row.getAvailable()), Duration.ofDays(3));
        }
    }

    /**
     * Redis Lua 预占。false=售罄；Redis 不可用时抛 INVENTORY_UNAVAILABLE（不降级打 MySQL 扣减）。
     */
    public boolean tryDeductRedis(Long roomTypeId, List<LocalDate> nights, int rooms) {
        try {
            // ===== 先预热 key，再 Lua：先校验所有晚再统一 decr =====
            warmUp(roomTypeId, nights);
            Long ok = redis.execute(deductScript, keys(roomTypeId, nights), String.valueOf(rooms));
            return ok != null && ok == 1L;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            // ===== 失败关闭：不降级 MySQL 扣减，宁可暂时下不了单 =====
            log.warn("redis deduct unavailable, reject order without mysql fallback: {}", ex.getMessage());
            throw new BizException(ResultCode.INVENTORY_UNAVAILABLE);
        }
    }

    /** 下单失败或关单时回补 Redis；失败只打日志，可人工 reload。 */
    public void restoreRedis(Long roomTypeId, List<LocalDate> nights, int rooms) {
        try {
            // ===== Lua 回补与扣减对称，按晚 incr =====
            redis.execute(restoreScript, keys(roomTypeId, nights), String.valueOf(rooms));
        } catch (Exception ex) {
            log.warn("redis restore failed, admin reload required: {}", ex.getMessage());
        }
    }

    /**
     * MySQL 逐晚扣减。任一天失败则抛错，由外层事务回滚 + Redis 补偿。
     */
    @Transactional
    public void deductMysql(Long roomTypeId, List<LocalDate> nights, int rooms) {
        for (LocalDate night : nights) {
            int updated = inventoryMapper.deductIfEnough(roomTypeId, night, rooms);
            if (updated != 1) {
                throw new BizException(ResultCode.SOLD_OUT, "日期 " + night + " 库存不足");
            }
        }
    }

    @Transactional
    public void restoreMysql(Long roomTypeId, List<LocalDate> nights, int rooms) {
        for (LocalDate night : nights) {
            inventoryMapper.restore(roomTypeId, night, rooms);
        }
    }

    /**
     * 报价用：取入住区间内最小可用间数。
     * 读路径允许降级 MySQL；与写路径「失败关闭」策略不同（面试要能对比）。
     */
    public int minAvailable(Long roomTypeId, List<LocalDate> nights) {
        try {
            warmUp(roomTypeId, nights);
            List<String> values = redis.opsForValue().multiGet(keys(roomTypeId, nights));
            if (values == null || values.size() != nights.size() || values.stream().anyMatch(v -> v == null)) {
                return minFromDb(roomTypeId, nights);
            }
            return values.stream().mapToInt(Integer::parseInt).min().orElse(0);
        } catch (Exception ex) {
            // ===== 读可降级 =====
            return minFromDb(roomTypeId, nights);
        }
    }

    public void reloadRoomType(Long roomTypeId) {
        List<RoomInventory> rows = inventoryMapper.selectList(new LambdaQueryWrapper<RoomInventory>()
                .eq(RoomInventory::getRoomTypeId, roomTypeId)
                .ge(RoomInventory::getStayDate, LocalDate.now()));
        for (RoomInventory row : rows) {
            redis.opsForValue().set(
                    InventoryKeys.available(roomTypeId, row.getStayDate()),
                    String.valueOf(row.getAvailable()),
                    Duration.ofDays(3)
            );
        }
    }

    private int minFromDb(Long roomTypeId, List<LocalDate> nights) {
        List<RoomInventory> rows = loadRows(roomTypeId, nights);
        if (rows.size() != nights.size()) {
            return 0;
        }
        return rows.stream().mapToInt(RoomInventory::getAvailable).min().orElse(0);
    }

    private List<String> keys(Long roomTypeId, List<LocalDate> nights) {
        List<String> keys = new ArrayList<>(nights.size());
        for (LocalDate night : nights) {
            keys.add(InventoryKeys.available(roomTypeId, night));
        }
        return keys;
    }
}
