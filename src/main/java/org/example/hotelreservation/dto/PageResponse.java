package org.example.hotelreservation.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageResponse<T> {
    private long total;
    private int page;
    private int size;
    private List<T> records;
}
