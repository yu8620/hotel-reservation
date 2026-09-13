package org.example.hotelreservation.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** L2: date-independent hotel + room-type basics. missing=true is a null-object (anti-penetration). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelStaticSnapshot {
    /** When true, hotel does not exist; stored with short TTL so repeats skip MySQL. */
    private boolean missing;
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomTypeStatic {
        private Long id;
        private String name;
        private Integer occupancy;
        private String bedDesc;
        private Integer totalRooms;
        private BigDecimal basePrice;
    }
}