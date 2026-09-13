package org.example.hotelreservation.common;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(0, "OK"),
    BAD_REQUEST(400, "请求参数不合法"),
    UNAUTHORIZED(401, "请先登录"),
    FORBIDDEN(403, "没有权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    SOLD_OUT(410, "所选日期库存不足"),
    ORDER_STATUS_INVALID(411, "订单状态不允许该操作"),
    DUPLICATE_REQUEST(412, "重复请求"),
    INVENTORY_UNAVAILABLE(503, "库存服务暂不可用，请稍后重试"),
    SERVER_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
