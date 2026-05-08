package com.saptarshi.finogpt.service;

import com.saptarshi.finogpt.config.AppProperties;
import com.saptarshi.finogpt.dto.IngestionResponse;
import com.saptarshi.finogpt.entity.IngestionJob;
import com.saptarshi.finogpt.helper.MultipartInputStreamFileResource;
import com.saptarshi.finogpt.repository.IngestionJobRepository;
import com.saptarshi.finogpt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class IngestionService {

    private final RestTemplate restTemplate;
    private final IngestionJobRepository repository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;
    private final IngestionReconciliationService reconciliationService;

    public IngestionResponse startIngestion(MultipartFile file,
                                            Long userId,
                                            String mobile) {
        validateUploadRequest(file, userId, mobile);

        UUID jobId = parseJobId(callFastApi(file, mobile));

        IngestionJob job = new IngestionJob();
        job.setJobId(jobId);
        job.setUserId(userId);
        job.setMobileNumber(mobile);
        job.setStatus("PROCESSING");

        repository.save(job);

        return new IngestionResponse(
                jobId.toString(),
                "PROCESSING",
                "File accepted. Processing started."
        );
    }

    private String callFastApi(MultipartFile file, String mobile) {
        String url = appProperties.getIngestion().getFastApiBaseUrl()
                + appProperties.getIngestion().getParsePath();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("mobile_number", mobile);

        try {
            body.add("file", new MultipartInputStreamFileResource(
                    file.getInputStream(),
                    file.getOriginalFilename()
            ));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }

        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate
                .postForEntity(url, requestEntity, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to call FastAPI");
        }

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || responseBody.get("job_id") == null) {
            throw new RuntimeException("FastAPI response missing job_id");
        }

        return (String) responseBody.get("job_id");
    }

    @Transactional
    public void incrementProcessed(String jobId) {
        UUID parsedJobId = tryParseJobId(jobId);
        if (parsedJobId == null || !repository.existsById(parsedJobId)) {
            return;
        }
        repository.incrementProcessed(parsedJobId);
        checkAndMarkCompleted(parsedJobId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementFailed(String jobId, String errorMessage) {
        UUID parsedJobId = tryParseJobId(jobId);
        if (parsedJobId == null || !repository.existsById(parsedJobId)) {
            return;
        }
        repository.incrementFailed(parsedJobId);
        if (errorMessage != null && !errorMessage.isBlank()) {
            repository.updateErrorMessage(parsedJobId, errorMessage);
        }
        checkAndMarkCompleted(parsedJobId);
    }

    @Transactional
    public void markCompleted(String jobId) {
        repository.updateStatus(parseJobId(jobId), "COMPLETED");
    }

    @Transactional
    public void updateTotalRecords(String jobId, Integer totalRecords) {

        repository.updateTotalRecords(parseJobId(jobId), totalRecords);
    }

    void checkAndMarkCompleted(String jobId) {
        UUID parsedJobId = parseJobId(jobId);
        checkAndMarkCompleted(parsedJobId);
    }

    void checkAndMarkCompleted(UUID jobId) {

        IngestionJob job = repository.findById(jobId)
                .orElseThrow();

        if (!job.isCompletionSignalReceived()) {
            return;
        }

        Integer totalRecords = job.getTotalRecords();
        if (totalRecords == null) {
            return;
        }

        int done = job.getProcessedCount() + job.getFailedCount();

        if (done >= totalRecords) {
            String status = job.getFailedCount() > 0
                    ? "PARTIAL_SUCCESS"
                    : "COMPLETED";

            try {
                reconciliationService.reconcileJob(jobId, job.getUserId());
            } catch (IllegalStateException ex) {
                status = "PARTIAL_SUCCESS";
                repository.updateErrorMessage(jobId, ex.getMessage());
            }

            repository.updateStatus(jobId, status);
        }
    }

    private void validateUploadRequest(MultipartFile file, Long userId, String mobile) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is required");
        }

        if (userId == null || !userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Valid userId is required");
        }

        if (mobile == null || mobile.isBlank()) {
            throw new IllegalArgumentException("Mobile number is required");
        }
    }

    private UUID parseJobId(String jobId) {
        UUID parsedJobId = tryParseJobId(jobId);
        if (parsedJobId == null) {
            throw new IllegalArgumentException("Invalid job_id: " + jobId);
        }
        return parsedJobId;
    }

    private UUID tryParseJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(jobId.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
