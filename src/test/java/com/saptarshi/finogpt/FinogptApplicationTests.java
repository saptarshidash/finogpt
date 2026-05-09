package com.saptarshi.finogpt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Context test depends on external infrastructure beans not available in unit test runtime")
@SpringBootTest
class FinogptApplicationTests {

    @Test
    void contextLoads() {
    }

}
