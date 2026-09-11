package org.example.hotelreservation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("order_night")
public class OrderNight {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private LocalDate stayDate;
    private Integer rooms;
    private BigDecimal price;
}
