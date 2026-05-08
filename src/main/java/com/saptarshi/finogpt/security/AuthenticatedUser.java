package com.saptarshi.finogpt.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthenticatedUser {

    private final Long userId;
    private final String email;
    private final String name;
}
