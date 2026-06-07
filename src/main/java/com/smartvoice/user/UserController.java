package com.smartvoice.user;

import com.smartvoice.common.config.JwtTokenProvider;
import com.smartvoice.user.dto.AuthResponse;
import com.smartvoice.user.dto.LoginRequest;
import com.smartvoice.user.dto.RegisterRequest;
import com.smartvoice.user.dto.UpdateProfileRequest;
import com.smartvoice.user.dto.UserProfileResponse;
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
    public ResponseEntity<UserProfileResponse> getCurrentUser(Authentication auth) {
        User user = userService.getById(auth.getPrincipal().toString());
        return ResponseEntity.ok(UserProfileResponse.from(user));
    }

    @PutMapping("/users/me")
    public ResponseEntity<UserProfileResponse> updateCurrentUser(
            Authentication auth,
            @Valid @RequestBody UpdateProfileRequest request) {
        User user = userService.updateProfile(auth.getPrincipal().toString(), request);
        return ResponseEntity.ok(UserProfileResponse.from(user));
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
