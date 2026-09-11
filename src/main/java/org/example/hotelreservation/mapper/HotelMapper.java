package org.example.hotelreservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.hotelreservation.entity.Hotel;

@Mapper
public interface HotelMapper extends BaseMapper<Hotel> {
}
