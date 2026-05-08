package com.saptarshi.finogpt.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityServiceTest {

    @Test
    void normalizesMerchantNames() {
        EntityService entityService = new EntityService(null);

        String normalized = entityService.normalize("Amazon Pay India Pvt Ltd");

        assertEquals("amazon pay", normalized);
    }
}
