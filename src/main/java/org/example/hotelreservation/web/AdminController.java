package org.example.hotelreservation.web;

import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.common.ApiResult;
import org.example.hotelreservation.service.AdminService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端接口层：只做路由与统一返回，业务交给 {@link AdminService}。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/es/rebuild")
    public ApiResult<Map<String, Object>> rebuildEs() {
        return ApiResult.ok(adminService.rebuildEsIndex());
    }

    @PostMapping("/inventory/reload/{roomTypeId}")
    public ApiResult<Void> reload(@PathVariable Long roomTypeId) {
        adminService.reloadInventory(roomTypeId);
        return ApiResult.ok();
    }

    @PostMapping("/inventory/reload-all")
    public ApiResult<Map<String, Object>> reloadAll() {
        return ApiResult.ok(adminService.reloadAllInventory());
    }

    @PostMapping("/orders/close-expired")
    public ApiResult<Map<String, Object>> closeExpired() {
        return ApiResult.ok(adminService.closeExpiredOrders());
    }
}
