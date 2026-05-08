package com.saptarshi.finogpt.security;

import com.saptarshi.finogpt.config.AppProperties;
import com.saptarshi.finogpt.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtUtil(AppProperties appProperties) {
        String secret = appProperties.getSecurity().getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret is not configured");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }

        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMillis = appProperties.getSecurity().getJwt().getExpirationMillis();
    }

    public String generate(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .claim("userId", user.getId())
                .claim("name", user.getName())
                .signWith(signingKey)
                .compact();
    }

    public Long validate(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    public AuthenticatedUser parse(String token) {
        Claims claims = parseClaims(token);
        Long userId = claims.get("userId", Long.class);
        if (userId == null) {
            throw new IllegalStateException("JWT missing userId claim");
        }

        return new AuthenticatedUser(
                userId,
                claims.getSubject(),
                claims.get("name", String.class)
        );
    }

    private Claims parseClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (claims.get("userId") == null) {
            throw new IllegalStateException("JWT missing userId claim");
        }

        return claims;
    }
}
