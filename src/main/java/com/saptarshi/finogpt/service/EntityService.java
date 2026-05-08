package com.saptarshi.finogpt.service;


import com.saptarshi.finogpt.entity.EntityTxn;
import com.saptarshi.finogpt.repository.EntityTxnRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntityService {

    private final EntityTxnRepository entityRepository;

    @Transactional
    public EntityTxn getOrCreate(String rawName) {

        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Entity name cannot be null");
        }

        String normalized = normalize(rawName);

     
        return entityRepository.findByNormalizedName(normalized)
                .orElseGet(() -> createNew(rawName, normalized));
    }

    private EntityTxn createNew(String rawName, String normalized) {

        return entityRepository.findByNormalizedName(normalized)
                .orElseGet(() -> {
                    EntityTxn entity = new EntityTxn();
                    entity.setName(rawName.trim());
                    entity.setNormalizedName(normalized);

                    log.info("Creating new entity: {} -> {}", rawName, normalized);

                    return entityRepository.save(entity);
                });
    }
    

    public String normalize(String name) {

        String normalized = name.toLowerCase();
        
        normalized = normalized.replaceAll("[^a-z0-9 ]", " ");
        
        normalized = normalized.replaceAll(
                "\\b(pvt ltd|private limited|ltd|limited|services|technologies|tech|solutions|india)\\b",
                ""
        );
        
        normalized = normalized.replaceAll("\\s+", " ").trim();

        return normalized;
    }
}

// =============================
// DONE 🚀 ENTITY SERVICE READY
// =============================
