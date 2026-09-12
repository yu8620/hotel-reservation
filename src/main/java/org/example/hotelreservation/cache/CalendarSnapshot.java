package org.example.hotelreservation.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** L4: night prices/availability for one room type interval. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarSnapshot {
    private Long roomTypeId;
    private List<Night> nights;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Night {
        private LocalDate stayDate;
        private BigDecimal price;
        private Integer available;
    }
}