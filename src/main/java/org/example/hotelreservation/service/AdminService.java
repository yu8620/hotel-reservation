package org.example.hotelreservation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.entity.RoomType;
import org.example.hotelreservation.inventory.InventoryService;
import org.example.hotelreservation.mapper.RoomTypeMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端应用服务：承接运维类用例，避免 Controller 直接依赖 Mapper / 基础设施组件。
 * <p>对应阿里手册分层：Controller → Service →（领域组件 / DAO）。
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final HotelQueryService hotelQueryService;
    private final InventoryService inventoryService;
    private final RoomTypeMapper roomTypeMapper;
    private final OrderService orderService;

    /** 全量重建 ES 酒店索引（ES 不可用时由下层消化异常）。 */
    public Map<String, Object> rebuildEsIndex() {
        int count = hotelQueryService.rebuildIndex();
        Map<String, Object> result = new HashMap<>();
        result.put("indexed", count);
        return result;
    }

    /** 将指定房型的日历库存从 MySQL 回填到 Redis 镜像。 */
    public void reloadInventory(Long roomTypeId) {
        inventoryService.reloadRoomType(roomTypeId);
    }

    /** 回填全部房型库存镜像；用于 Redis 恢复后的校准。 */
    public Map<String, Object> reloadAllInventory() {
        List<RoomType> types = roomTypeMapper.selectList(new LambdaQueryWrapper<RoomType>());
        for (RoomType type : types) {
            inventoryService.reloadRoomType(type.getId());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("roomTypes", types.size());
        return result;
    }

    /** 补偿关闭已过期未支付订单（MQ 丢失时的兜底入口）。 */
    public Map<String, Object> closeExpiredOrders() {
        int n = orderService.closeExpiredOrders();
        Map<String, Object> result = new HashMap<>();
        result.put("closed", n);
        return result;
    }
}
