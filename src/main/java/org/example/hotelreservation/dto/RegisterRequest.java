package org.example.hotelreservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 4, max = 32)
    private String username;
    @NotBlank
    @Size(min = 6, max = 32)
    private String password;
    private String phone;
}
