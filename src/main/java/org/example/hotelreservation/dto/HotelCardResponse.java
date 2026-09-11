package org.example.hotelreservation.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class HotelCardResponse {
    private Long id;
    private String name;
    private String city;
    private String address;
    private Integer starRating;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String amenities;
    private BigDecimal minStayPrice;
    private Integer minRemain;
    private String searchSource;
    private List<String> availableRoomTypes;
}
