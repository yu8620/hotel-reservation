package org.example.hotelreservation.util;

import org.example.hotelreservation.common.BizException;
import org.example.hotelreservation.common.ResultCode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class StayDates {

    private StayDates() {
    }

    /**
     * 酒店业惯例：占用 [checkIn, checkOut) 的夜。
     * 20 号入住、23 号离店 = 扣 20/21/22 三晚，不是四个日历日。
     */
    public static List<LocalDate> nights(LocalDate checkIn, LocalDate checkOut) {
        validate(checkIn, checkOut);
        return occupiedNights(checkIn, checkOut);
    }

    /** 关单/取消回补时不再校验「不能早于今天」，否则过了入住日就无法释放库存。 */
    public static List<LocalDate> occupiedNights(LocalDate checkIn, LocalDate checkOut) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            dates.add(d);
        }
        return dates;
    }

    public static void validate(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "入住和离店日期不能为空");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BizException(ResultCode.BAD_REQUEST, "离店日期必须晚于入住日期");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw new BizException(ResultCode.BAD_REQUEST, "入住日期不能早于今天");
        }
        if (checkIn.plusDays(30).isBefore(checkOut)) {
            throw new BizException(ResultCode.BAD_REQUEST, "单次预订最多 30 晚");
        }
    }
}
