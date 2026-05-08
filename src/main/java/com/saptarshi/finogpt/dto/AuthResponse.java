package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private final String token;
    private final UserSummaryResponse user;
}
