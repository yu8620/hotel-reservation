package org.example.hotelreservation.dto;

import lombok.Builder;
import lombok.Data;
import org.example.hotelreservation.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private String orderNo;
    private String requestId;
    private Long hotelId;
    private String hotelName;
    private Long roomTypeId;
    private String roomTypeName;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Integer nights;
    private Integer rooms;
    private BigDecimal amount;
    private BigDecimal penaltyAmount;
    private OrderStatus status;
    private LocalDateTime expireTime;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private List<Night> nightsDetail;

    @Data
    @Builder
    public static class Night {
        private LocalDate stayDate;
        private BigDecimal price;
        private Integer rooms;
    }
}
