package com.saptarshi.finogpt.listener;

import com.saptarshi.finogpt.dto.TransactionEvent;
import com.saptarshi.finogpt.service.TransactionProcessingService;
import com.saptarshi.finogpt.service.TransactionProcessingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionConsumer {

    private final TransactionProcessingService processingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.ingestion.kafka-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message, Acknowledgment ack) {

        try {
            TransactionEvent event =
                    objectMapper.readValue(message, TransactionEvent.class);

            processingService.process(event);

            ack.acknowledge();

        } catch (JsonProcessingException ex) {
            log.warn("Dropping malformed Kafka message: {}", message, ex);
            ack.acknowledge();
        } catch (TransactionProcessingException ex) {
            if (!ex.isRetryable()) {
                log.warn("Dropping non-retryable transaction event: {}", ex.getMessage());
                ack.acknowledge();
                return;
            }

            throw ex;
        } catch (Exception ex) {
            log.error("Kafka message processing failed", ex);
            throw new RuntimeException("Failed to process message", ex);
        }
    }
}
