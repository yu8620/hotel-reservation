package org.example.hotelreservation.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileResponse {
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String bio;
    private boolean blogger;
    private long followerCount;
    private long followingCount;
    private boolean followedByMe;
    private boolean mutualFollow;
}
