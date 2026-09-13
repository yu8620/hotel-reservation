package org.example.hotelreservation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.cache.CalendarSnapshot;
import org.example.hotelreservation.cache.HotelCacheKeys;
import org.example.hotelreservation.cache.HotelQuoteSnapshot;
import org.example.hotelreservation.cache.HotelReadCache;
import org.example.hotelreservation.cache.HotelStaticSnapshot;
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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Query orchestration: read-cache → ES/DB/inventory backfill → write-cache.
 * Inventory mutation stays in OrderService + InventoryService.
 */
@Service
@RequiredArgsConstructor
public class HotelQueryService {

    private final HotelMapper hotelMapper;
    private final RoomTypeMapper roomTypeMapper;
    private final InventoryService inventoryService;
    private final HotelEsSearchService esSearchService;
    private final HotelIndexService hotelIndexService;
    private final HotelReadCache hotelReadCache;

    public PageResponse<HotelCardResponse> search(HotelSearchQuery query) {
        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int size = query.getSize() == null || query.getSize() < 1 ? 10 : Math.min(query.getSize(), 50);
        int rooms = query.getRooms() == null ? 1 : query.getRooms();
        LocalDate checkIn = query.getCheckIn();
        LocalDate checkOut = query.getCheckOut();
        boolean geo = query.getLatitude() != null && query.getLongitude() != null;

        if (!geo) {
            String searchKey = HotelCacheKeys.search(
                    query.getCity(), checkIn, checkOut, rooms,
                    query.getStar(), query.getKeyword(), query.getMinPrice(), query.getMaxPrice(),
                    page, size);
            Optional<PageResponse<HotelCardResponse>> cached = hotelReadCache.getSearch(searchKey);
            if (cached.isPresent()) {
                PageResponse<HotelCardResponse> hit = cached.get();
                if (hit.getRecords() != null) {
                    hit.getRecords().forEach(card -> card.setSearchSource("CACHE"));
                }
                return hit;
            }
            PageResponse<HotelCardResponse> fresh = searchUncached(query, page, size, rooms, checkIn, checkOut);
            hotelReadCache.putSearch(searchKey, fresh);
            return fresh;
        }
        return searchUncached(query, page, size, rooms, checkIn, checkOut);
    }

    private PageResponse<HotelCardResponse> searchUncached(HotelSearchQuery query, int page, int size, int rooms,
                                                           LocalDate checkIn, LocalDate checkOut) {
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

        Map<Long, HotelQuoteSnapshot> quoteHits = nights.isEmpty()
                ? Map.of()
                : hotelReadCache.mgetQuotes(ids, checkIn, checkOut, rooms);

        List<HotelCardResponse> cards = new ArrayList<>();
        for (Long id : ids) {
            Hotel hotel = hotelMap.get(id);
            if (hotel == null) {
                continue;
            }
            HotelQuoteSnapshot quote = quoteHits.get(id);
            if (quote == null && !nights.isEmpty()) {
                quote = buildQuote(id, roomsByHotel.getOrDefault(id, List.of()), nights, rooms);
                hotelReadCache.putQuote(id, checkIn, checkOut, rooms, quote);
            }

            if (!nights.isEmpty()) {
                if (quote == null || quote.getAvailableRoomTypes() == null || quote.getAvailableRoomTypes().isEmpty()) {
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
                        .minStayPrice(quote.getMinStayPrice() == null ? hotel.getMinPrice() : quote.getMinStayPrice())
                        .minRemain(quote.getMinRemain() == null ? 0 : quote.getMinRemain())
                        .searchSource(source)
                        .availableRoomTypes(quote.getAvailableRoomTypes())
                        .build());
            } else {
                cards.add(HotelCardResponse.builder()
                        .id(hotel.getId())
                        .name(hotel.getName())
                        .city(hotel.getCity())
                        .address(hotel.getAddress())
                        .starRating(hotel.getStarRating())
                        .latitude(hotel.getLatitude())
                        .longitude(hotel.getLongitude())
                        .amenities(hotel.getAmenities())
                        .minStayPrice(hotel.getMinPrice())
                        .minRemain(0)
                        .searchSource(source)
                        .availableRoomTypes(List.of())
                        .build());
            }
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
        LocalDate effectiveIn = checkIn;
        LocalDate effectiveOut = checkOut;
        if (effectiveIn == null || effectiveOut == null) {
            effectiveIn = LocalDate.now().plusDays(1);
            effectiveOut = LocalDate.now().plusDays(3);
        }
        List<LocalDate> nights = StayDates.nights(effectiveIn, effectiveOut);

        HotelStaticSnapshot staticSnap = hotelReadCache.getStatic(hotelId).orElse(null);
        if (staticSnap != null && staticSnap.isMissing()) {
            throw new BizException(ResultCode.NOT_FOUND, "酒店不存在");
        }
        if (staticSnap == null) {
            try {
                staticSnap = loadStatic(hotelId);
                hotelReadCache.putStatic(staticSnap);
            } catch (BizException ex) {
                if (ex.getResultCode() == ResultCode.NOT_FOUND) {
                    hotelReadCache.putStaticMissing(hotelId);
                }
                throw ex;
            }
        }

        List<HotelDetailResponse.RoomTypeView> views = new ArrayList<>();
        List<HotelStaticSnapshot.RoomTypeStatic> types = staticSnap.getRoomTypes() == null
                ? List.of() : staticSnap.getRoomTypes();
        for (HotelStaticSnapshot.RoomTypeStatic type : types) {
            CalendarSnapshot cal = hotelReadCache.getCalendar(type.getId(), effectiveIn, effectiveOut).orElse(null);
            if (cal == null) {
                cal = loadCalendar(type.getId(), nights);
                hotelReadCache.putCalendar(cal, effectiveIn, effectiveOut);
            }
            List<HotelDetailResponse.NightPrice> calendar = cal.getNights() == null ? List.of() : cal.getNights().stream()
                    .map(n -> HotelDetailResponse.NightPrice.builder()
                            .stayDate(n.getStayDate())
                            .price(n.getPrice())
                            .available(n.getAvailable())
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
                .id(staticSnap.getId())
                .name(staticSnap.getName())
                .city(staticSnap.getCity())
                .address(staticSnap.getAddress())
                .starRating(staticSnap.getStarRating())
                .latitude(staticSnap.getLatitude())
                .longitude(staticSnap.getLongitude())
                .description(staticSnap.getDescription())
                .amenities(staticSnap.getAmenities())
                .roomTypes(views)
                .build();
    }

    public int rebuildIndex() {
        List<Hotel> hotels = hotelMapper.selectList(null);
        hotelIndexService.rebuild(hotels);
        return hotels.size();
    }

    private HotelQuoteSnapshot buildQuote(Long hotelId, List<RoomType> types, List<LocalDate> nights, int rooms) {
        BigDecimal minStay = null;
        int minRemain = 0;
        List<String> availableNames = new ArrayList<>();
        for (RoomType type : types) {
            int remain = inventoryService.minAvailable(type.getId(), nights);
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
        return HotelQuoteSnapshot.builder()
                .hotelId(hotelId)
                .minStayPrice(minStay)
                .minRemain(minRemain)
                .availableRoomTypes(availableNames)
                .build();
    }

    private HotelStaticSnapshot loadStatic(Long hotelId) {
        Hotel hotel = hotelMapper.selectById(hotelId);
        if (hotel == null) {
            throw new BizException(ResultCode.NOT_FOUND, "酒店不存在");
        }
        List<RoomType> types = roomTypeMapper.selectList(new LambdaQueryWrapper<RoomType>().eq(RoomType::getHotelId, hotelId));
        List<HotelStaticSnapshot.RoomTypeStatic> roomTypes = types.stream()
                .map(t -> HotelStaticSnapshot.RoomTypeStatic.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .occupancy(t.getOccupancy())
                        .bedDesc(t.getBedDesc())
                        .totalRooms(t.getTotalRooms())
                        .basePrice(t.getBasePrice())
                        .build())
                .toList();
        return HotelStaticSnapshot.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .city(hotel.getCity())
                .address(hotel.getAddress())
                .starRating(hotel.getStarRating())
                .latitude(hotel.getLatitude())
                .longitude(hotel.getLongitude())
                .description(hotel.getDescription())
                .amenities(hotel.getAmenities())
                .roomTypes(roomTypes)
                .build();
    }

    private CalendarSnapshot loadCalendar(Long roomTypeId, List<LocalDate> nights) {
        List<RoomInventory> rows = inventoryService.loadRows(roomTypeId, nights);
        List<CalendarSnapshot.Night> nightViews = rows.stream()
                .map(r -> CalendarSnapshot.Night.builder()
                        .stayDate(r.getStayDate())
                        .price(r.getPrice())
                        .available(r.getAvailable())
                        .build())
                .toList();
        return CalendarSnapshot.builder().roomTypeId(roomTypeId).nights(nightViews).build();
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