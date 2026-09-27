package org.example.hotelreservation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommentResponse {
    private Long id;
    private Long noteId;
    private Long userId;
    private String username;
    private String nickname;
    private String content;
    private LocalDateTime createdAt;
}
