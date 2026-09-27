package org.example.hotelreservation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NoteCardResponse {
    private Long id;
    private Long hotelId;
    private String hotelName;
    private Long authorId;
    private String authorName;
    private String authorNickname;
    private String title;
    private String content;
    private String coverUrl;
    private Integer authorScore;
    private int likeCount;
    private int commentCount;
    private Double avgReaderScore;
    private int ratingCount;
    private boolean likedByMe;
    private Integer myScore;
    private LocalDateTime createdAt;
}
