package com.saptarshi.finogpt.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BreakdownItemResponse {

    private final Long id;
    private final String name;
    private final BigDecimal totalAmount;
    private final Long txnCount;
}
