package org.example.hotelreservation.web;

import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.common.ApiResult;
import org.example.hotelreservation.dto.HotelCardResponse;
import org.example.hotelreservation.dto.HotelDetailResponse;
import org.example.hotelreservation.dto.HotelSearchQuery;
import org.example.hotelreservation.dto.PageResponse;
import org.example.hotelreservation.service.HotelQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
/**
 * 酒店查询接口层：搜索与详情。
 */
public class HotelController {

    private final HotelQueryService hotelQueryService;

    @GetMapping("/search")
    public ApiResult<PageResponse<HotelCardResponse>> search(HotelSearchQuery query) {
        return ApiResult.ok(hotelQueryService.search(query));
    }

    @GetMapping("/{id}")
    public ApiResult<HotelDetailResponse> detail(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        return ApiResult.ok(hotelQueryService.detail(id, checkIn, checkOut));
    }
}
