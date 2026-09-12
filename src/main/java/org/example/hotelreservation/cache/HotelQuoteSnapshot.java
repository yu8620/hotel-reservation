package org.example.hotelreservation.cache;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** L3: short-TTL stay quote for one hotel + date range + rooms. */
@Data
@Builder
public class HotelQuoteSnapshot {
    private Long hotelId;
    private BigDecimal minStayPrice;
    private Integer minRemain;
    private List<String> availableRoomTypes;
}