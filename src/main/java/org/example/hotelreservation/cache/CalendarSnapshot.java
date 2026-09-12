package org.example.hotelreservation.cache;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** L4: night prices/availability for one room type interval. */
@Data
@Builder
public class CalendarSnapshot {
    private Long roomTypeId;
    private List<Night> nights;

    @Data
    @Builder
    public static class Night {
        private LocalDate stayDate;
        private BigDecimal price;
        private Integer available;
    }
}