package com.saptarshi.finogpt.service;


import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.saptarshi.finogpt.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LLMService {

    private final AppProperties appProperties;

    public String generate(String prompt) {

        log.info("LLM Prompt:\n{}", prompt);

        String projectId = appProperties.getLlm().getProjectId();
        String location = appProperties.getLlm().getLocation();

        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("LLM project ID is not configured");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("LLM location is not configured");
        }

        try {
            Client client = Client.builder()
                    .vertexAI(true)
                    .project(projectId)
                    .location(location)
                    .build();

            GenerateContentResponse response =
                    client.models.generateContent(appProperties.getLlm().getModel(), prompt, null);

            String responseText = response.text();
            log.info("LLM Response:\n{}", responseText);
            return responseText;
        } catch (Exception e) {
            log.error("Error while calling LLM", e);
            throw e;
        }
    }
}
