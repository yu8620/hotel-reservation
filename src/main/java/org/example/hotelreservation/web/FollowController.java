package org.example.hotelreservation.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.common.ApiResult;
import org.example.hotelreservation.dto.FollowActionRequest;
import org.example.hotelreservation.dto.UserProfileResponse;
import org.example.hotelreservation.security.SecurityUtils;
import org.example.hotelreservation.service.FollowService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
/**
 * 好友关注 / 互关 / 博主列表。
 */
public class FollowController {

    private final FollowService followService;

    @PostMapping("/follow")
    public ApiResult<Void> follow(@Valid @RequestBody FollowActionRequest request) {
        followService.follow(request.getFolloweeId());
        return ApiResult.ok();
    }

    @DeleteMapping("/follow/{followeeId}")
    public ApiResult<Void> unfollow(@PathVariable Long followeeId) {
        followService.unfollow(followeeId);
        return ApiResult.ok();
    }

    @GetMapping("/users/{userId}")
    public ApiResult<UserProfileResponse> profile(@PathVariable Long userId) {
        return ApiResult.ok(followService.profile(userId));
    }

    @GetMapping("/users/{userId}/following")
    public ApiResult<List<UserProfileResponse>> following(@PathVariable Long userId) {
        return ApiResult.ok(followService.following(userId));
    }

    @GetMapping("/users/{userId}/followers")
    public ApiResult<List<UserProfileResponse>> followers(@PathVariable Long userId) {
        return ApiResult.ok(followService.followers(userId));
    }

    @GetMapping("/users/{userId}/mutual")
    public ApiResult<List<UserProfileResponse>> mutual(@PathVariable Long userId) {
        return ApiResult.ok(followService.mutualFriends(userId));
    }

    @GetMapping("/me/following")
    public ApiResult<List<UserProfileResponse>> myFollowing() {
        return ApiResult.ok(followService.following(SecurityUtils.currentUserId()));
    }

    @GetMapping("/me/followers")
    public ApiResult<List<UserProfileResponse>> myFollowers() {
        return ApiResult.ok(followService.followers(SecurityUtils.currentUserId()));
    }

    @GetMapping("/me/mutual")
    public ApiResult<List<UserProfileResponse>> myMutual() {
        return ApiResult.ok(followService.mutualFriends(SecurityUtils.currentUserId()));
    }

    @GetMapping("/bloggers")
    public ApiResult<List<UserProfileResponse>> bloggers() {
        return ApiResult.ok(followService.listBloggers());
    }
}
