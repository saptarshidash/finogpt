package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserSettingsResponse {

    private final String name;
    private final String email;
    private final String phone;
}
