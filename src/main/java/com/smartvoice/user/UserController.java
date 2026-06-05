package com.smartvoice.user;

import com.smartvoice.common.config.JwtTokenProvider;
import com.smartvoice.user.dto.AuthResponse;
import com.smartvoice.user.dto.LoginRequest;
import com.smartvoice.user.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/auth/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        return ResponseEntity.ok(buildAuthResponse(user));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request);
        return ResponseEntity.ok(buildAuthResponse(user));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
        }
        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userService.getById(userId);
        return ResponseEntity.ok(buildAuthResponse(user));
    }

    @GetMapping("/users/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication auth) {
        User user = userService.getById(auth.getPrincipal().toString());
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "englishLevel", user.getEnglishLevel().name()
        ));
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        return AuthResponse.builder()
                .accessToken(accessToken).refreshToken(refreshToken)
                .tokenType("Bearer").expiresIn(86400)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId()).username(user.getUsername())
                        .email(user.getEmail()).englishLevel(user.getEnglishLevel().name())
                        .build())
                .build();
    }
}
