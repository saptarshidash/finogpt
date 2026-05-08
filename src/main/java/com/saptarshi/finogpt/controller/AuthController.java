package com.saptarshi.finogpt.controller;

import com.saptarshi.finogpt.dto.ApiResponse;
import com.saptarshi.finogpt.dto.AuthResponse;
import com.saptarshi.finogpt.dto.LoginRequest;
import com.saptarshi.finogpt.dto.SignupRequest;
import com.saptarshi.finogpt.dto.UserSummaryResponse;
import com.saptarshi.finogpt.entity.User;
import com.saptarshi.finogpt.repository.UserRepository;
import com.saptarshi.finogpt.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d\\d\\$.*$");

    private final UserRepository users;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@RequestBody SignupRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body(error("Name is required"));
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body(error("Email is required"));
        }

        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return ResponseEntity
                    .badRequest()
                    .body(error("Password must be at least 6 characters"));
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (users.findByEmail(normalizedEmail).isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(error("Email already exists"));
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(normalizedEmail);
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        users.save(user);
        log.info("User with email {} has been signed up", user.getEmail());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(success("Signup successful", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(error("Email is required"));
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(error("Password is required"));
        }

        User user = users.findByEmail(request.getEmail().trim().toLowerCase())
                .orElse(null);

        if (user == null || !matchesPassword(request.getPassword(), user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("Invalid email or password"));
        }

        return ResponseEntity.ok(success("Login successful", user));
    }

    private boolean matchesPassword(String rawPassword, User user) {
        String storedPassword = user.getPasswordHash();
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }

        if (isBcryptHash(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        if (!storedPassword.equals(rawPassword)) {
            return false;
        }

        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        users.save(user);
        log.info("Migrated legacy plain-text password to bcrypt for user {}", user.getEmail());
        return true;
    }

    private boolean isBcryptHash(String value) {
        return BCRYPT_PATTERN.matcher(value).matches();
    }

    private ApiResponse<AuthResponse> success(String message, User user) {
        return ApiResponse.<AuthResponse>builder()
                .success(true)
                .message(message)
                .data(AuthResponse.builder()
                        .token(jwtUtil.generate(user))
                        .user(UserSummaryResponse.from(user))
                        .build())
                .build();
    }

    private ApiResponse<AuthResponse> error(String message) {
        return ApiResponse.<AuthResponse>builder()
                .success(false)
                .message(message)
                .data(null)
                .build();
    }
}
