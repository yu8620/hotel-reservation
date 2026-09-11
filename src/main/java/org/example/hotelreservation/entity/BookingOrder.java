package org.example.hotelreservation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.example.hotelreservation.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("booking_order")
public class BookingOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String requestId;
    private Long userId;
    private Long hotelId;
    private Long roomTypeId;
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
