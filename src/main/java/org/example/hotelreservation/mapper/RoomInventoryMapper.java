package org.example.hotelreservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.example.hotelreservation.entity.RoomInventory;

@Mapper
public interface RoomInventoryMapper extends BaseMapper<RoomInventory> {

    /**
     * 单日库存扣减。available >= rooms 保证即使 Redis 误放行，MySQL 也不会超订。
     */
    @Update("""
            UPDATE room_inventory
            SET available = available - #{rooms}, version = version + 1
            WHERE room_type_id = #{roomTypeId}
              AND stay_date = #{stayDate}
              AND available >= #{rooms}
              AND deleted = 0
            """)
    int deductIfEnough(@Param("roomTypeId") Long roomTypeId,
                       @Param("stayDate") java.time.LocalDate stayDate,
                       @Param("rooms") int rooms);

    @Update("""
            UPDATE room_inventory
            SET available = available + #{rooms}, version = version + 1
            WHERE room_type_id = #{roomTypeId}
              AND stay_date = #{stayDate}
              AND deleted = 0
            """)
    int restore(@Param("roomTypeId") Long roomTypeId,
                @Param("stayDate") java.time.LocalDate stayDate,
                @Param("rooms") int rooms);
}
