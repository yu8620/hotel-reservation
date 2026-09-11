package org.example.hotelreservation.inventory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class InventoryKeys {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private InventoryKeys() {
    }

    public static String available(Long roomTypeId, LocalDate stayDate) {
        return "inv:" + roomTypeId + ":" + stayDate.format(DAY);
    }
}
