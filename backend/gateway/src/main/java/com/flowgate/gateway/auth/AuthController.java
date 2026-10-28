package com.flowgate.gateway.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String REFRESH_COOKIE = "fg_refresh";

    public record LoginRequest(String username, String password) {}

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;

    public AuthController(
            UserRepository users,
            PasswordEncoder passwords,
            JwtService jwt,
            RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
    }

    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody LoginRequest request, HttpServletResponse response) {
        if (request.username() == null || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username and password required");
        }
        UserEntity user =
                users.findByUsername(request.username())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad credentials"));
        if (!passwords.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad credentials");
        }
        String jti = refreshTokens.issue(user.getUsername());
        addRefreshCookie(response, jti);
        return tokens(user.getUsername(), user.getRole());
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        String jti = readRefreshCookie(request);
        if (jti == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no refresh cookie");
        }
        RefreshTokenService.Rotated rotated = refreshTokens.rotate(jti);
        UserEntity user =
                users.findByUsername(rotated.username())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user gone"));
        addRefreshCookie(response, rotated.newJti());
        return tokens(user.getUsername(), user.getRole());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
        refreshTokens.revokeCookie(readRefreshCookie(request));
        ResponseCookie clear =
                ResponseCookie.from(REFRESH_COOKIE, "")
                        .httpOnly(true)
                        .path("/api/v1/auth")
                        .maxAge(0)
                        .sameSite("Lax")
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clear.toString());
        return Map.of("status", "logged-out");
    }

    private Map<String, Object> tokens(String username, String role) {
        return Map.of(
                "accessToken", jwt.create(username, role),
                "tokenType", "Bearer",
                "expiresInMs", jwt.expirationMs(),
                "role", role);
    }

    private void addRefreshCookie(HttpServletResponse response, String jti) {
        ResponseCookie cookie =
                ResponseCookie.from(REFRESH_COOKIE, jti)
                        .httpOnly(true)
                        .path("/api/v1/auth")
                        .maxAge(Duration.ofDays(7))
                        .sameSite("Lax")
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
