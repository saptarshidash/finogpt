package com.saptarshi.finogpt.security;

import com.saptarshi.finogpt.config.AppProperties;
import com.saptarshi.finogpt.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtUtilTest {

    @Test
    void generatesTokenWithUserIdClaim() {
        AppProperties properties = new AppProperties();
        properties.getSecurity().getJwt().setSecret("01234567890123456789012345678901");
        JwtUtil jwtUtil = new JwtUtil(properties);

        User user = new User();
        user.setId(42L);
        user.setEmail("user@example.com");
        user.setName("User");

        String token = jwtUtil.generate(user);

        assertEquals(42L, jwtUtil.validate(token));
    }
}
