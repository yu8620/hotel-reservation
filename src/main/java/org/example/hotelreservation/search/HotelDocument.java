package org.example.hotelreservation.search;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class HotelDocument {
    private Long hotelId;
    private String name;
    private String city;
    private Integer starRating;
    private BigDecimal minPrice;
    private String address;
    private String amenities;
    private GeoPoint location;

    @Data
    public static class GeoPoint {
        private double lat;
        private double lon;
    }
}
