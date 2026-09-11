package org.example.hotelreservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateOrderRequest {
    @NotNull
    private Long roomTypeId;
    @NotNull
    private LocalDate checkIn;
    @NotNull
    private LocalDate checkOut;
    @Min(1)
    @Max(5)
    private Integer rooms = 1;
    @NotBlank
    private String requestId;
}
