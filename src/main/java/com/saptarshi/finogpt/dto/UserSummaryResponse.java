package com.saptarshi.finogpt.dto;

import com.saptarshi.finogpt.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserSummaryResponse {

    private final Long id;
    private final String name;
    private final String email;
    private final String phone;

    public static UserSummaryResponse from(User user) {
        return UserSummaryResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
    }
}
