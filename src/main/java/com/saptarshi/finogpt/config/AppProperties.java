package com.saptarshi.finogpt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private final Ingestion ingestion = new Ingestion();
    private final Llm llm = new Llm();
    private final AppHelp appHelp = new AppHelp();
    private final Security security = new Security();
    private final Cors cors = new Cors();

    public Ingestion getIngestion() {
        return ingestion;
    }

    public Llm getLlm() {
        return llm;
    }

    public AppHelp getAppHelp() {
        return appHelp;
    }

    public Security getSecurity() {
        return security;
    }

    public Cors getCors() {
        return cors;
    }

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
    public static class AppHelp {
        private String knowledgeBasePath = "docs/app-help-knowledge-base.md";
        private String embeddingModel = "text-embedding-004";
        private int outputDimensionality = 768;
        private int topK = 3;
        private double minSimilarity = 0.55d;
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

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOriginPatterns = List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"
        );
    }
}
