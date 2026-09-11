package org.example.hotelreservation.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.common.ApiResult;
import org.example.hotelreservation.entity.RoomType;
import org.example.hotelreservation.inventory.InventoryService;
import org.example.hotelreservation.mapper.RoomTypeMapper;
import org.example.hotelreservation.service.HotelQueryService;
import org.example.hotelreservation.service.OrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final HotelQueryService hotelQueryService;
    private final InventoryService inventoryService;
    private final RoomTypeMapper roomTypeMapper;
    private final OrderService orderService;

    @PostMapping("/es/rebuild")
    public ApiResult<Map<String, Object>> rebuildEs() {
        int count = hotelQueryService.rebuildIndex();
        return ApiResult.ok(Map.of("indexed", count));
    }

    @PostMapping("/inventory/reload/{roomTypeId}")
    public ApiResult<Void> reload(@PathVariable Long roomTypeId) {
        inventoryService.reloadRoomType(roomTypeId);
        return ApiResult.ok();
    }

    @PostMapping("/inventory/reload-all")
    public ApiResult<Map<String, Object>> reloadAll() {
        var types = roomTypeMapper.selectList(new LambdaQueryWrapper<RoomType>());
        types.forEach(t -> inventoryService.reloadRoomType(t.getId()));
        return ApiResult.ok(Map.of("roomTypes", types.size()));
    }

    @PostMapping("/orders/close-expired")
    public ApiResult<Map<String, Object>> closeExpired() {
        int n = orderService.closeExpiredOrders();
        return ApiResult.ok(Map.of("closed", n));
    }
}
