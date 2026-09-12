package org.example.hotelreservation.cache;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** L2: date-independent hotel + room-type basics. */
@Data
@Builder
public class HotelStaticSnapshot {
    private Long id;
    private String name;
    private String city;
    private String address;
    private Integer starRating;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String description;
    private String amenities;
    private List<RoomTypeStatic> roomTypes;

    @Data
    @Builder
    public static class RoomTypeStatic {
        private Long id;
        private String name;
        private Integer occupancy;
        private String bedDesc;
        private Integer totalRooms;
        private BigDecimal basePrice;
    }
}