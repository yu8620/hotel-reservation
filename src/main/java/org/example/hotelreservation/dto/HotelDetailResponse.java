package org.example.hotelreservation.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class HotelDetailResponse {
    private Long id;
    private String name;
    private String city;
    private String address;
    private Integer starRating;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String description;
    private String amenities;
    private List<RoomTypeView> roomTypes;

    @Data
    @Builder
    public static class RoomTypeView {
        private Long id;
        private String name;
        private Integer occupancy;
        private String bedDesc;
        private Integer totalRooms;
        private BigDecimal basePrice;
        private Integer minRemain;
        private List<NightPrice> calendar;
    }

    @Data
    @Builder
    public static class NightPrice {
        private LocalDate stayDate;
        private BigDecimal price;
        private Integer available;
    }
}
