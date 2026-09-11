package org.example.hotelreservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.example.hotelreservation.entity.BookingOrder;
import org.example.hotelreservation.enums.OrderStatus;

@Mapper
public interface BookingOrderMapper extends BaseMapper<BookingOrder> {

    /**
     * 状态机 CAS：支付与超时关单同时到达时只有一条能成功。
     */
    @Update("""
            UPDATE booking_order
            SET status = #{next},
                pay_time = CASE WHEN #{next} = 'CONFIRMED' THEN NOW() ELSE pay_time END,
                cancel_time = CASE WHEN #{next} IN ('CANCELLED', 'CLOSED') THEN NOW() ELSE cancel_time END,
                cancel_reason = CASE WHEN #{reason} IS NULL THEN cancel_reason ELSE #{reason} END,
                penalty_amount = CASE WHEN #{penalty} IS NULL THEN penalty_amount ELSE #{penalty} END
            WHERE id = #{id} AND status = #{expect} AND deleted = 0
            """)
    int casStatus(@Param("id") Long id,
                  @Param("expect") OrderStatus expect,
                  @Param("next") OrderStatus next,
                  @Param("reason") String reason,
                  @Param("penalty") java.math.BigDecimal penalty);
}
