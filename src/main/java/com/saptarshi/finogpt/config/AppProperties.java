package com.saptarshi.finogpt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private final Ingestion ingestion = new Ingestion();
    private final Llm llm = new Llm();
    private final Security security = new Security();

    @Getter
    @Setter
    public static class Ingestion {
        private String kafkaTopic = "phonepe_transactions";
        private String fastApiBaseUrl = "http://127.0.0.1:8000";
        private String parsePath = "/parse-async";
    }

    @Getter
    @Setter
    public static class Llm {
        private String projectId;
        private String location = "global";
        private String model = "gemini-3.1-pro-preview";
        private boolean vertexAiEnabled = true;
    }

    @Getter
    @Setter
    public static class Security {
        private final Jwt jwt = new Jwt();

        @Getter
        @Setter
        public static class Jwt {
            private String secret;
            private long expirationMillis = 86_400_000L;
        }
    }
}
