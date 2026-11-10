package com.pawnmaster.api.modules.user;

import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.modules.user.dto.AuthResponse;
import com.pawnmaster.api.modules.user.dto.LoginRequest;
import com.pawnmaster.api.modules.user.dto.UserResponse;
import com.pawnmaster.api.security.JwtAuthenticationFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public BaseResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        if (response != null && response.getAccessToken() != null) {
            setAuthCookieIfPresent(response.getAccessToken(), 86400);
        }
        return BaseResponse.ok("Đăng nhập thành công", response);
    }

    @PostMapping("/logout")
    public BaseResponse<Void> logout() {
        setAuthCookieIfPresent("", 0);
        SecurityContextHolder.clearContext();
        return BaseResponse.ok("Đăng xuất thành công", null);
    }

    @GetMapping("/me")
    public BaseResponse<UserResponse> getCurrentUser(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "";
        UserResponse response = authService.getCurrentUser(username);
        return BaseResponse.ok(response);
    }

    private void setAuthCookieIfPresent(String tokenValue, int maxAgeSeconds) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null && attrs.getResponse() != null) {
            HttpServletResponse httpResponse = attrs.getResponse();
            Cookie cookie = new Cookie(JwtAuthenticationFilter.JWT_COOKIE_NAME, tokenValue);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(maxAgeSeconds);
            httpResponse.addCookie(cookie);
        }
    }
}

