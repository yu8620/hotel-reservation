package org.example.hotelreservation.cache;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Read-path cache keys only. No Redis I/O, no business logic.
 */
public final class HotelCacheKeys {

    public static final String PREFIX = "hr:";

    private HotelCacheKeys() {
    }

    public static String search(String city, LocalDate checkIn, LocalDate checkOut, int rooms,
                                Integer star, String keyword, BigDecimal minPrice, BigDecimal maxPrice,
                                int page, int size) {
        return PREFIX + "search:v1:"
                + n(city) + ":"
                + d(checkIn) + ":"
                + d(checkOut) + ":"
                + rooms + ":"
                + n(star) + ":"
                + n(keyword) + ":"
                + n(minPrice) + ":"
                + n(maxPrice) + ":"
                + page + ":"
                + size;
    }

    public static String hotelStatic(Long hotelId) {
        return PREFIX + "hotel:static:v1:" + hotelId;
    }

    public static String quote(Long hotelId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        return PREFIX + "quote:v1:" + hotelId + ":" + d(checkIn) + ":" + d(checkOut) + ":" + rooms;
    }

    public static String calendar(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        return PREFIX + "cal:v1:" + roomTypeId + ":" + d(checkIn) + ":" + d(checkOut);
    }


    public static String lockSearch(String searchKey) {
        return PREFIX + "lock:search:v1:" + Integer.toHexString(searchKey.hashCode());
    }

    public static String lockStatic(Long hotelId) {
        return PREFIX + "lock:static:v1:" + hotelId;
    }

    public static String lockCalendar(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        return PREFIX + "lock:cal:v1:" + roomTypeId + ":" + d(checkIn) + ":" + d(checkOut);
    }
    private static String d(LocalDate date) {
        return date == null ? "n" : date.toString();
    }

    private static String n(Object value) {
        if (value == null) {
            return "n";
        }
        String text = Objects.toString(value).trim();
        if (text.isEmpty()) {
            return "n";
        }
        return text.replace(':', '_').replace(' ', '_');
    }
}