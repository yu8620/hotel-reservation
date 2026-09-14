package org.example.hotelreservation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.common.BizException;
import org.example.hotelreservation.common.ResultCode;
import org.example.hotelreservation.dto.LoginRequest;
import org.example.hotelreservation.dto.LoginResponse;
import org.example.hotelreservation.dto.RegisterRequest;
import org.example.hotelreservation.entity.User;
import org.example.hotelreservation.enums.UserRole;
import org.example.hotelreservation.mapper.UserMapper;
import org.example.hotelreservation.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/**
 * 认证应用服务：注册、登录与 JWT 签发。
 */
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse register(RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(UserRole.USER);
        userMapper.insert(user);
        return tokenOf(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return tokenOf(user);
    }

    private LoginResponse tokenOf(User user) {
        String token = jwtService.createToken(user.getId(), user.getUsername(), user.getRole().name());
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }
}
