package org.example.hotelreservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.hotelreservation.entity.OrderNight;

@Mapper
public interface OrderNightMapper extends BaseMapper<OrderNight> {
}
