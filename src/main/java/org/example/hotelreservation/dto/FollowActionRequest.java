package org.example.hotelreservation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FollowActionRequest {
    @NotNull
    private Long followeeId;
}
