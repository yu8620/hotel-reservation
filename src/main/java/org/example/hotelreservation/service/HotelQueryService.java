package org.example.hotelreservation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.common.BizException;
import org.example.hotelreservation.common.ResultCode;
import org.example.hotelreservation.dto.HotelCardResponse;
import org.example.hotelreservation.dto.HotelDetailResponse;
import org.example.hotelreservation.dto.HotelSearchQuery;
import org.example.hotelreservation.dto.PageResponse;
import org.example.hotelreservation.entity.Hotel;
import org.example.hotelreservation.entity.RoomInventory;
import org.example.hotelreservation.entity.RoomType;
import org.example.hotelreservation.inventory.InventoryService;
import org.example.hotelreservation.mapper.HotelMapper;
import org.example.hotelreservation.mapper.RoomTypeMapper;
import org.example.hotelreservation.search.HotelEsSearchService;
import org.example.hotelreservation.search.HotelIndexService;
import org.example.hotelreservation.util.StayDates;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HotelQueryService {

    private final HotelMapper hotelMapper;
    private final RoomTypeMapper roomTypeMapper;
    private final InventoryService inventoryService;
    private final HotelEsSearchService esSearchService;
    private final HotelIndexService hotelIndexService;

    public PageResponse<HotelCardResponse> search(HotelSearchQuery query) {
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int size = query.getSize() == null || query.getSize() < 1 ? 10 : Math.min(query.getSize(), 50);
        int rooms = query.getRooms() == null ? 1 : query.getRooms();
        LocalDate checkIn = query.getCheckIn();
        LocalDate checkOut = query.getCheckOut();
        List<LocalDate> nights = (checkIn != null && checkOut != null) ? StayDates.nights(checkIn, checkOut) : List.of();

        String source = "elasticsearch";
        List<Long> ids = esSearchService.searchHotelIds(query, 80);
        if (ids == null) {
            source = "mysql";
            ids = mysqlCandidateIds(query);
        }

        if (ids.isEmpty()) {
            return PageResponse.<HotelCardResponse>builder().total(0).page(page).size(size).records(List.of()).build();
        }
        List<Hotel> hotels = hotelMapper.selectByIds(ids);
        Map<Long, Hotel> hotelMap = hotels.stream().collect(Collectors.toMap(Hotel::getId, h -> h));
        List<RoomType> roomTypes = roomTypeMapper.selectList(new LambdaQueryWrapper<RoomType>().in(RoomType::getHotelId, ids));
        Map<Long, List<RoomType>> roomsByHotel = roomTypes.stream().collect(Collectors.groupingBy(RoomType::getHotelId));

        List<HotelCardResponse> cards = new ArrayList<>();
        for (Long id : ids) {
            Hotel hotel = hotelMap.get(id);
            if (hotel == null) {
                continue;
            }
            List<RoomType> types = roomsByHotel.getOrDefault(id, List.of());
            BigDecimal minStay = null;
            int minRemain = 0;
            List<String> availableNames = new ArrayList<>();
            for (RoomType type : types) {
                int remain = nights.isEmpty() ? type.getTotalRooms() : inventoryService.minAvailable(type.getId(), nights);
                if (remain < rooms) {
                    continue;
                }
                availableNames.add(type.getName());
                BigDecimal stayPrice = stayPrice(type, nights);
                if (minStay == null || stayPrice.compareTo(minStay) < 0) {
                    minStay = stayPrice;
                    minRemain = remain;
                }
            }
            if (!nights.isEmpty() && availableNames.isEmpty()) {
                continue;
            }
            cards.add(HotelCardResponse.builder()
                    .id(hotel.getId())
                    .name(hotel.getName())
                    .city(hotel.getCity())
                    .address(hotel.getAddress())
                    .starRating(hotel.getStarRating())
                    .latitude(hotel.getLatitude())
                    .longitude(hotel.getLongitude())
                    .amenities(hotel.getAmenities())
                    .minStayPrice(minStay == null ? hotel.getMinPrice() : minStay)
                    .minRemain(minRemain)
                    .searchSource(source)
                    .availableRoomTypes(availableNames)
                    .build());
        }
        cards.sort(Comparator.comparing(HotelCardResponse::getMinStayPrice, Comparator.nullsLast(BigDecimal::compareTo)));
        long total = cards.size();
        int from = Math.min((page - 1) * size, cards.size());
        int to = Math.min(from + size, cards.size());
        return PageResponse.<HotelCardResponse>builder()
                .total(total)
                .page(page)
                .size(size)
                .records(cards.subList(from, to))
                .build();
    }

    public HotelDetailResponse detail(Long hotelId, LocalDate checkIn, LocalDate checkOut) {
        Hotel hotel = hotelMapper.selectById(hotelId);
        if (hotel == null) {
            throw new BizException(ResultCode.NOT_FOUND, "酒店不存在");
        }
        List<LocalDate> nights;
        if (checkIn != null && checkOut != null) {
            nights = StayDates.nights(checkIn, checkOut);
        } else {
            nights = StayDates.nights(LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        }
        List<RoomType> types = roomTypeMapper.selectList(new LambdaQueryWrapper<RoomType>().eq(RoomType::getHotelId, hotelId));
        List<HotelDetailResponse.RoomTypeView> views = new ArrayList<>();
        for (RoomType type : types) {
            List<RoomInventory> rows = inventoryService.loadRows(type.getId(), nights);
            List<HotelDetailResponse.NightPrice> calendar = rows.stream()
                    .map(r -> HotelDetailResponse.NightPrice.builder()
                            .stayDate(r.getStayDate())
                            .price(r.getPrice())
                            .available(r.getAvailable())
                            .build())
                    .toList();
            int minRemain = calendar.stream().mapToInt(HotelDetailResponse.NightPrice::getAvailable).min().orElse(0);
            views.add(HotelDetailResponse.RoomTypeView.builder()
                    .id(type.getId())
                    .name(type.getName())
                    .occupancy(type.getOccupancy())
                    .bedDesc(type.getBedDesc())
                    .totalRooms(type.getTotalRooms())
                    .basePrice(type.getBasePrice())
                    .minRemain(minRemain)
                    .calendar(calendar)
                    .build());
        }
        return HotelDetailResponse.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .city(hotel.getCity())
                .address(hotel.getAddress())
                .starRating(hotel.getStarRating())
                .latitude(hotel.getLatitude())
                .longitude(hotel.getLongitude())
                .description(hotel.getDescription())
                .amenities(hotel.getAmenities())
                .roomTypes(views)
                .build();
    }

    public int rebuildIndex() {
        List<Hotel> hotels = hotelMapper.selectList(null);
        hotelIndexService.rebuild(hotels);
        return hotels.size();
    }

    private List<Long> mysqlCandidateIds(HotelSearchQuery query) {
        LambdaQueryWrapper<Hotel> wrapper = new LambdaQueryWrapper<Hotel>()
                .eq(StringUtils.hasText(query.getCity()), Hotel::getCity, query.getCity())
                .eq(query.getStar() != null, Hotel::getStarRating, query.getStar())
                .ge(query.getMinPrice() != null, Hotel::getMinPrice, query.getMinPrice())
                .le(query.getMaxPrice() != null, Hotel::getMinPrice, query.getMaxPrice())
                .like(StringUtils.hasText(query.getKeyword()), Hotel::getName, query.getKeyword())
                .orderByAsc(Hotel::getMinPrice)
                .last("LIMIT 80");
        return hotelMapper.selectList(wrapper).stream().map(Hotel::getId).toList();
    }

    private BigDecimal stayPrice(RoomType type, List<LocalDate> nights) {
        if (nights.isEmpty()) {
            return type.getBasePrice();
        }
        return inventoryService.loadRows(type.getId(), nights).stream()
                .map(RoomInventory::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
