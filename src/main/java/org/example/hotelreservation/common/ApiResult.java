package org.example.hotelreservation.common;

import lombok.Data;
import org.slf4j.MDC;

@Data
public class ApiResult<T> {
    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.setCode(ResultCode.SUCCESS.getCode());
        r.setMessage(ResultCode.SUCCESS.getMessage());
        r.setData(data);
        r.setTraceId(MDC.get(TraceIdFilter.TRACE_ID));
        return r;
    }

    public static ApiResult<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> fail(ResultCode code) {
        return fail(code, code.getMessage());
    }

    public static <T> ApiResult<T> fail(ResultCode code, String message) {
        ApiResult<T> r = new ApiResult<>();
        r.setCode(code.getCode());
        r.setMessage(message);
        r.setTraceId(MDC.get(TraceIdFilter.TRACE_ID));
        return r;
    }
}
