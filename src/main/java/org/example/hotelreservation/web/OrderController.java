package org.example.hotelreservation.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.common.ApiResult;
import org.example.hotelreservation.dto.CreateOrderRequest;
import org.example.hotelreservation.dto.OrderResponse;
import org.example.hotelreservation.security.SecurityUtils;
import org.example.hotelreservation.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ApiResult<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResult.ok(orderService.create(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/mine")
    public ApiResult<List<OrderResponse>> mine() {
        return ApiResult.ok(orderService.mine(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResult<OrderResponse> detail(@PathVariable Long id) {
        return ApiResult.ok(orderService.detail(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/pay")
    public ApiResult<OrderResponse> pay(@PathVariable Long id) {
        return ApiResult.ok(orderService.pay(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResult<OrderResponse> cancel(@PathVariable Long id) {
        return ApiResult.ok(orderService.cancel(SecurityUtils.currentUserId(), id));
    }
}
