package org.example.hotelreservation.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HotelSearchQuery {
    private String city = "南昌";
    private String keyword;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkIn;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkOut;
    private Integer rooms = 1;
    private Integer star;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Double latitude;
    private Double longitude;
    private Double radiusKm;
    private Integer page = 1;
    private Integer size = 10;
}
