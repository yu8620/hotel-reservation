package org.example.hotelreservation.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hotelreservation.config.HotelProperties;
import org.example.hotelreservation.entity.Hotel;
import org.example.hotelreservation.entity.RoomInventory;
import org.example.hotelreservation.entity.RoomType;
import org.example.hotelreservation.entity.User;
import org.example.hotelreservation.enums.UserRole;
import org.example.hotelreservation.inventory.InventoryService;
import org.example.hotelreservation.mapper.HotelMapper;
import org.example.hotelreservation.mapper.RoomInventoryMapper;
import org.example.hotelreservation.mapper.RoomTypeMapper;
import org.example.hotelreservation.mapper.UserMapper;
import org.example.hotelreservation.search.HotelIndexService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserMapper userMapper;
    private final HotelMapper hotelMapper;
    private final RoomTypeMapper roomTypeMapper;
    private final RoomInventoryMapper inventoryMapper;
    private final PasswordEncoder passwordEncoder;
    private final HotelProperties properties;
    private final HotelIndexService hotelIndexService;
    private final InventoryService inventoryService;

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();
        seedHotels();
        seedInventory();
        try {
            hotelIndexService.rebuild(hotelMapper.selectList(null));
        } catch (Exception ex) {
            log.warn("skip es rebuild on startup: {}", ex.getMessage());
        }
        log.info("seed ready. admin/admin123  demo/demo123  压测房型见 README");
    }

    private void seedUsers() {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        insertUser("admin", "admin123", UserRole.ADMIN);
        insertUser("demo", "demo123", UserRole.USER);
    }

    private void insertUser(String username, String rawPassword, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        userMapper.insert(user);
    }

    private void seedHotels() {
        if (hotelMapper.selectCount(null) > 0) {
            return;
        }
        record RoomSpec(String name, int occupancy, String bed, int total, String price) {
        }
        record Spec(String name, String district, double lat, double lng, int star, String amenity, String desc,
                    RoomSpec[] rooms) {
        }
        List<Spec> specs = List.of(
                new Spec("南昌万达嘉华酒店", "红谷滩区会展路999号", 28.6894, 115.8582, 5, "泳池,健身房,行政酒廊",
                        "红谷滩地标酒店，压测默认用「豪华大床房」仅 3 间。",
                        new RoomSpec[]{
                                new RoomSpec("豪华大床房", 2, "1.8m大床", 3, "688"),
                                new RoomSpec("豪华双床房", 2, "1.35m双床", 8, "728"),
                                new RoomSpec("行政套房", 2, "2.0m大床", 4, "1288")
                        }),
                new Spec("南昌香格里拉大酒店", "东湖区中山西路108号", 28.6768, 115.8912, 5, "江景,泳池,中餐厅",
                        "赣江边五星，步行可达滕王阁。",
                        new RoomSpec[]{new RoomSpec("江景大床房", 2, "1.8m大床", 10, "980"), new RoomSpec("江景双床房", 2, "1.35m双床", 10, "1020")}),
                new Spec("南昌格兰云天国际酒店", "西湖区站前路168号", 28.6621, 115.9054, 4, "近火车站,自助餐",
                        "南昌站步行约8分钟。",
                        new RoomSpec[]{new RoomSpec("高级大床房", 2, "1.8m大床", 12, "398"), new RoomSpec("家庭房", 3, "1.5m+1.2m", 6, "528")}),
                new Spec("维也纳国际酒店(南昌八一广场店)", "东湖区八一大道166号", 28.6761, 115.8994, 4, "近地铁,商务中心",
                        "八一广场核心，适合出差。",
                        new RoomSpec[]{new RoomSpec("高级大床房", 2, "1.8m大床", 15, "328"), new RoomSpec("豪华双床房", 2, "1.2m双床", 12, "358")}),
                new Spec("全季酒店(南昌八一广场店)", "西湖区中山路219号", 28.6742, 115.8948, 3, "自助早餐,洗衣",
                        "中山路商圈，性价比商务连锁。",
                        new RoomSpec[]{new RoomSpec("大床房", 2, "1.8m大床", 16, "268"), new RoomSpec("双床房", 2, "1.2m双床", 12, "288")}),
                new Spec("亚朵酒店(南昌东站店)", "青山湖区解放东路2000号", 28.6582, 115.9501, 4, "零食吧,健身房",
                        "南昌东站约10分钟车程。",
                        new RoomSpec[]{new RoomSpec("亚朵大床房", 2, "1.8m大床", 14, "429"), new RoomSpec("亚朵双床房", 2, "1.35m双床", 10, "459")}),
                new Spec("桔子水晶(南昌秋水广场店)", "红谷滩区秋水广场旁", 28.6876, 115.8571, 4, "江景,设计感",
                        "秋水广场夜景，周末溢价明显。",
                        new RoomSpec[]{new RoomSpec("水晶大床房", 2, "1.8m大床", 9, "459"), new RoomSpec("江景套房", 2, "2.0m大床", 4, "899")}),
                new Spec("南昌滕王阁美居酒店", "东湖区仿古街1号", 28.6835, 115.8764, 4, "近滕王阁,中式庭院",
                        "滕王阁景区步行可达。",
                        new RoomSpec[]{new RoomSpec("园景大床房", 2, "1.8m大床", 8, "468"), new RoomSpec("双床房", 2, "1.35m双床", 8, "498")}),
                new Spec("如家商旅(南昌火车站店)", "西湖区站前西路88号", 28.6610, 115.9028, 3, "24小时前台",
                        "火车站南广场，赶车方便。",
                        new RoomSpec[]{new RoomSpec("商务大床房", 2, "1.5m大床", 18, "198"), new RoomSpec("标准双床房", 2, "1.2m双床", 14, "218")}),
                new Spec("锦江之星(南昌西湖店)", "西湖区孺子路126号", 28.6684, 115.8916, 3, "停车场,早餐",
                        "西湖区生活圈，价格亲民。",
                        new RoomSpec[]{new RoomSpec("舒适大床房", 2, "1.5m大床", 16, "188"), new RoomSpec("舒适双床房", 2, "1.2m双床", 12, "208")}),
                new Spec("希尔顿欢朋(南昌青山湖店)", "青山湖区北京东路1266号", 28.6854, 115.9302, 4, "泳池,健身",
                        "青山湖景区附近国际品牌。",
                        new RoomSpec[]{new RoomSpec("欢朋大床房", 2, "1.8m大床", 12, "518"), new RoomSpec("欢朋双床房", 2, "1.35m双床", 10, "548")}),
                new Spec("宜必思(南昌大学前店)", "红谷滩区学府大道999号", 28.6618, 115.8046, 3, "近高校,餐厅",
                        "前湖南大片区，家长探访常用。",
                        new RoomSpec[]{new RoomSpec("标准大床房", 2, "1.5m大床", 20, "249"), new RoomSpec("标准双床房", 2, "1.2m双床", 16, "269")}),
                new Spec("丽枫酒店(南昌万象城店)", "红谷滩区万象城商业街", 28.6912, 115.8618, 4, "近商场,隔音好",
                        "万象城购物餐饮步行3分钟。",
                        new RoomSpec[]{new RoomSpec("丽枫大床房", 2, "1.8m大床", 10, "389"), new RoomSpec("丽枫双床房", 2, "1.35m双床", 8, "419")}),
                new Spec("智选假日(南昌铜锣湾店)", "青山湖区北京东路与上海路交叉口", 28.6821, 115.9188, 3, "近铜锣湾广场",
                        "商圈酒店，适合短途。",
                        new RoomSpec[]{new RoomSpec("高级大床房", 2, "1.5m大床", 14, "309"), new RoomSpec("高级双床房", 2, "1.2m双床", 10, "329")}),
                new Spec("南昌朝阳洲江景酒店", "西湖区朝阳洲中路", 28.6558, 115.8722, 3, "赣江景观",
                        "朝阳洲江景，夜跑绿道方便。",
                        new RoomSpec[]{new RoomSpec("江景大床房", 2, "1.8m大床", 8, "359"), new RoomSpec("江景双床房", 2, "1.35m双床", 8, "379")}),
                new Spec("南昌高新区科技园酒店", "高新区火炬大街888号", 28.6948, 115.9804, 3, "近园区,会议室",
                        "高新区出差入住。",
                        new RoomSpec[]{new RoomSpec("商务大床房", 2, "1.8m大床", 12, "329"), new RoomSpec("商务双床房", 2, "1.35m双床", 10, "349")}),
                new Spec("南昌昌北机场航泰酒店", "新建区昌北机场迎宾大道", 28.8646, 115.9003, 4, "接驳机场,隔音窗",
                        "昌北机场约10分钟，红眼航班首选。",
                        new RoomSpec[]{new RoomSpec("机场大床房", 2, "1.8m大床", 16, "399"), new RoomSpec("舒适双床房", 2, "1.35m双床", 8, "429")}),
                new Spec("南昌梅岭温泉度假酒店", "湾里区梅岭风景区", 28.7332, 115.6804, 4, "温泉,山地",
                        "周末度假，周五周六房价上浮。",
                        new RoomSpec[]{new RoomSpec("山景大床房", 2, "1.8m大床", 10, "568"), new RoomSpec("温泉套房", 2, "2.0m大床", 6, "988")}),
                new Spec("南昌湾里森林度假酒店", "湾里区岭秀路", 28.7154, 115.7312, 3, "森林氧吧",
                        "湾里休闲，适合亲子。",
                        new RoomSpec[]{new RoomSpec("森系大床房", 2, "1.8m大床", 8, "428"), new RoomSpec("亲子房", 3, "1.5m+1.2m", 6, "528")}),
                new Spec("南昌象湖湿地公园酒店", "南昌县象湖新城", 28.6288, 115.9012, 3, "湿地公园,骑行",
                        "象湖新城，环境安静。",
                        new RoomSpec[]{new RoomSpec("湖景大床房", 2, "1.8m大床", 9, "318"), new RoomSpec("湖景双床房", 2, "1.35m双床", 9, "338")}),
                new Spec("南昌前湖迎宾馆", "红谷滩区前湖大道", 28.6574, 115.8126, 5, "园林,会议",
                        "园林式接待酒店，安静。",
                        new RoomSpec[]{new RoomSpec("迎宾大床房", 2, "2.0m大床", 6, "888"), new RoomSpec("园林套房", 2, "2.0m大床", 3, "1588")}),
                new Spec("南昌老福山商务酒店", "西湖区老福山立交旁", 28.6638, 115.8991, 3, "近地铁",
                        "老城区交通枢纽。",
                        new RoomSpec[]{new RoomSpec("商务大床房", 2, "1.5m大床", 14, "228"), new RoomSpec("商务双床房", 2, "1.2m双床", 10, "248")}),
                new Spec("CitiGO欢阁(南昌红谷滩店)", "红谷滩区绿茵路", 28.6955, 115.8496, 3, "潮牌设计",
                        "红谷滩年轻人向品牌。",
                        new RoomSpec[]{new RoomSpec("潮牌大床房", 2, "1.8m大床", 11, "339"), new RoomSpec("潮牌双床房", 2, "1.35m双床", 9, "359")}),
                new Spec("南昌瑶湖快捷酒店", "青山湖区瑶湖高校园区", 28.6933, 116.0288, 2, "近高校",
                        "瑶湖高校园区探访住宿。",
                        new RoomSpec[]{new RoomSpec("经济大床房", 2, "1.5m大床", 18, "168"), new RoomSpec("经济双床房", 2, "1.2m双床", 14, "188")}),
                new Spec("南昌师大南路公寓式酒店", "青山湖区师大南路", 28.6759, 115.9244, 2, "厨房,长住",
                        "公寓式，适合考试周家长长住。",
                        new RoomSpec[]{new RoomSpec("一室公寓", 2, "1.5m大床", 10, "258"), new RoomSpec("双床公寓", 2, "1.2m双床", 8, "278")})
        );
        for (Spec spec : specs) {
            Hotel hotel = new Hotel();
            hotel.setName(spec.name());
            hotel.setCity("南昌");
            hotel.setAddress(spec.district());
            hotel.setStarRating(spec.star());
            hotel.setLatitude(BigDecimal.valueOf(spec.lat()).setScale(6, RoundingMode.HALF_UP));
            hotel.setLongitude(BigDecimal.valueOf(spec.lng()).setScale(6, RoundingMode.HALF_UP));
            hotel.setDescription(spec.desc());
            hotel.setAmenities(spec.amenity());
            hotel.setMinPrice(new BigDecimal(spec.rooms()[0].price()));
            hotelMapper.insert(hotel);
            BigDecimal min = null;
            for (RoomSpec room : spec.rooms()) {
                RoomType type = new RoomType();
                type.setHotelId(hotel.getId());
                type.setName(room.name());
                type.setOccupancy(room.occupancy());
                type.setBedDesc(room.bed());
                type.setTotalRooms(room.total());
                type.setBasePrice(new BigDecimal(room.price()));
                roomTypeMapper.insert(type);
                if (min == null || type.getBasePrice().compareTo(min) < 0) {
                    min = type.getBasePrice();
                }
            }
            hotel.setMinPrice(min);
            hotelMapper.updateById(hotel);
        }
    }

    private void seedInventory() {
        int days = properties.getInventory().getCalendarDays();
        List<RoomType> types = roomTypeMapper.selectList(null);
        LocalDate start = LocalDate.now();
        for (RoomType type : types) {
            for (int i = 0; i < days; i++) {
                LocalDate date = start.plusDays(i);
                Long exists = inventoryMapper.selectCount(new LambdaQueryWrapper<RoomInventory>()
                        .eq(RoomInventory::getRoomTypeId, type.getId())
                        .eq(RoomInventory::getStayDate, date));
                if (exists != null && exists > 0) {
                    continue;
                }
                BigDecimal price = type.getBasePrice();
                DayOfWeek week = date.getDayOfWeek();
                if (week == DayOfWeek.FRIDAY || week == DayOfWeek.SATURDAY) {
                    price = price.multiply(new BigDecimal("1.25")).setScale(0, RoundingMode.HALF_UP);
                }
                RoomInventory row = new RoomInventory();
                row.setRoomTypeId(type.getId());
                row.setStayDate(date);
                row.setTotalRooms(type.getTotalRooms());
                row.setAvailable(type.getTotalRooms());
                row.setPrice(price);
                row.setVersion(0);
                inventoryMapper.insert(row);
            }
            try {
                inventoryService.reloadRoomType(type.getId());
            } catch (Exception ex) {
                log.warn("redis warmup skipped for roomType {}: {}", type.getId(), ex.getMessage());
            }
        }
    }
}
