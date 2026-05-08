package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.IngestionJob;
import com.saptarshi.finogpt.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    Page<IngestionJob> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<IngestionJob> findByJobIdAndUserId(UUID jobId, Long userId);

    @Modifying
    @Query(value = "UPDATE ingestion_jobs " +
            "SET processed_count = processed_count + 1, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE job_id = :jobId", nativeQuery = true)
    void incrementProcessed(@Param("jobId") UUID jobId);

    @Modifying
    @Query(value = "UPDATE ingestion_jobs " +
            "SET failed_count = failed_count + 1, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE job_id = :jobId", nativeQuery = true)
    void incrementFailed(@Param("jobId") UUID jobId);

    @Modifying
    @Query(value = "UPDATE ingestion_jobs " +
            "SET status = :status, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE job_id = :jobId", nativeQuery = true)
    void updateStatus(@Param("jobId") UUID jobId,
                      @Param("status") String status);

    @Modifying
    @Query(value = "UPDATE ingestion_jobs " +
            "SET total_records = :totalRecords, " +
            "completion_signal_received = TRUE, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE job_id = :jobId", nativeQuery = true)
    void updateTotalRecords(@Param("jobId") UUID jobId,
                            @Param("totalRecords") Integer totalRecords);

    @Modifying
    @Query(value = "UPDATE ingestion_jobs " +
            "SET error_message = :errorMessage, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE job_id = :jobId", nativeQuery = true)
    void updateErrorMessage(@Param("jobId") UUID jobId,
                            @Param("errorMessage") String errorMessage);

    @Query("SELECT u " +
            "FROM User u " +
            "JOIN IngestionJob j ON u.id = j.userId " +
            "WHERE j.jobId = :jobId")
    Optional<User> findUserByJobId(@Param("jobId") UUID jobId);

    void deleteByUserId(Long userId);
}
