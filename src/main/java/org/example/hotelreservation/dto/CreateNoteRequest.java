package org.example.hotelreservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateNoteRequest {
    @NotNull
    private Long hotelId;
    @NotBlank
    @Size(max = 128)
    private String title;
    @NotBlank
    @Size(max = 8000)
    private String content;
    @Size(max = 255)
    private String coverUrl;
    @Min(1)
    @Max(5)
    private Integer authorScore;
}
