package org.example.hotelreservation.util;

import org.example.hotelreservation.common.BizException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StayDatesTest {

    @Test
    void threeNightsOccupiesThreeDatesNotCheckoutDay() {
        LocalDate in = LocalDate.now().plusDays(10);
        LocalDate out = in.plusDays(3);
        List<LocalDate> nights = StayDates.nights(in, out);
        assertEquals(3, nights.size());
        assertEquals(in, nights.getFirst());
        assertEquals(out.minusDays(1), nights.getLast());
    }

    @Test
    void rejectCheckoutNotAfterCheckIn() {
        LocalDate in = LocalDate.now().plusDays(2);
        assertThrows(BizException.class, () -> StayDates.nights(in, in));
    }

    @Test
    void occupiedNightsAllowsPastCheckInForRestock() {
        LocalDate in = LocalDate.now().minusDays(2);
        LocalDate out = LocalDate.now();
        assertEquals(2, StayDates.occupiedNights(in, out).size());
    }
}
