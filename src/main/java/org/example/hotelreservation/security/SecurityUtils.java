package org.example.hotelreservation.security;

import org.example.hotelreservation.common.BizException;
import org.example.hotelreservation.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long currentUserId() {
        return currentUser().userId();
    }
}
