package org.example.hotelreservation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.common.BizException;
import org.example.hotelreservation.common.ResultCode;
import org.example.hotelreservation.dto.UserProfileResponse;
import org.example.hotelreservation.entity.User;
import org.example.hotelreservation.entity.UserFollow;
import org.example.hotelreservation.mapper.UserFollowMapper;
import org.example.hotelreservation.mapper.UserMapper;
import org.example.hotelreservation.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
/**
 * 关注 / 互关：参考黑马点评好友关注。
 */
public class FollowService {

    private final UserFollowMapper followMapper;
    private final UserMapper userMapper;

    @Transactional
    public void follow(Long followeeId) {
        Long me = SecurityUtils.currentUserId();
        if (Objects.equals(me, followeeId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能关注自己");
        }
        User target = userMapper.selectById(followeeId);
        if (target == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        Long exists = followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, me)
                .eq(UserFollow::getFolloweeId, followeeId));
        if (exists != null && exists > 0) {
            return;
        }
        UserFollow row = new UserFollow();
        row.setFollowerId(me);
        row.setFolloweeId(followeeId);
        row.setCreatedAt(LocalDateTime.now());
        followMapper.insert(row);
    }

    @Transactional
    public void unfollow(Long followeeId) {
        Long me = SecurityUtils.currentUserId();
        followMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, me)
                .eq(UserFollow::getFolloweeId, followeeId));
    }

    public List<UserProfileResponse> following(Long userId) {
        List<UserFollow> rows = followMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId)
                .orderByDesc(UserFollow::getCreatedAt));
        List<UserProfileResponse> list = new ArrayList<>();
        for (UserFollow row : rows) {
            list.add(profile(row.getFolloweeId()));
        }
        return list;
    }

    public List<UserProfileResponse> followers(Long userId) {
        List<UserFollow> rows = followMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, userId)
                .orderByDesc(UserFollow::getCreatedAt));
        List<UserProfileResponse> list = new ArrayList<>();
        for (UserFollow row : rows) {
            list.add(profile(row.getFollowerId()));
        }
        return list;
    }

    public List<UserProfileResponse> mutualFriends(Long userId) {
        List<UserFollow> following = followMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId));
        List<UserProfileResponse> mutual = new ArrayList<>();
        for (UserFollow f : following) {
            Long back = followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId, f.getFolloweeId())
                    .eq(UserFollow::getFolloweeId, userId));
            if (back != null && back > 0) {
                mutual.add(profile(f.getFolloweeId()));
            }
        }
        return mutual;
    }

    public UserProfileResponse profile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        long followerCount = followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, userId));
        long followingCount = followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId));
        boolean followedByMe = false;
        boolean mutual = false;
        try {
            Long me = SecurityUtils.currentUserId();
            followedByMe = followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId, me)
                    .eq(UserFollow::getFolloweeId, userId)) > 0;
            if (followedByMe) {
                mutual = followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
                        .eq(UserFollow::getFolloweeId, me)) > 0;
            }
        } catch (BizException ignored) {
            // 未登录：只读公开资料
        }
        return UserProfileResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname() != null ? user.getNickname() : user.getUsername())
                .avatar(user.getAvatar())
                .bio(user.getBio())
                .blogger(user.getIsBlogger() != null && user.getIsBlogger() == 1)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .followedByMe(followedByMe)
                .mutualFollow(mutual)
                .build();
    }

    public List<UserProfileResponse> listBloggers() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getIsBlogger, 1)
                .orderByAsc(User::getId));
        List<UserProfileResponse> list = new ArrayList<>();
        for (User u : users) {
            list.add(profile(u.getId()));
        }
        return list;
    }
}
