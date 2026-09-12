package org.example.hotelreservation.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** L3: short-TTL stay quote for one hotel + date range + rooms. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelQuoteSnapshot {
    private Long hotelId;
    private BigDecimal minStayPrice;
    private Integer minRemain;
    private List<String> availableRoomTypes;
}