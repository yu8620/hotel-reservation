package org.example.hotelreservation.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.common.ApiResult;
import org.example.hotelreservation.dto.LoginRequest;
import org.example.hotelreservation.dto.LoginResponse;
import org.example.hotelreservation.dto.RegisterRequest;
import org.example.hotelreservation.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResult<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResult.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.ok(authService.login(request));
    }
}
