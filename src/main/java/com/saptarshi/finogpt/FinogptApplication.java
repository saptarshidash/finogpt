package com.saptarshi.finogpt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
@ConfigurationPropertiesScan
public class FinogptApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinogptApplication.class, args);
    }

}
