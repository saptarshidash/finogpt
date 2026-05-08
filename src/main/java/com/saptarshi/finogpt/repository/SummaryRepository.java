package com.saptarshi.finogpt.repository;

import com.saptarshi.finogpt.entity.Transaction;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
public interface SummaryRepository extends JpaRepository<Transaction, Long> {
    
    @Modifying
    @Query(value = "INSERT INTO daily_summary (user_id, date, total_debit, total_credit, txn_count) " +
            "VALUES (:userId, :date, " +
            "CASE WHEN :type = 'DEBIT' THEN :amount ELSE 0 END, " +
            "CASE WHEN :type = 'CREDIT' THEN :amount ELSE 0 END, " +
            "1) " +
            "ON CONFLICT (user_id, date) " +
            "DO UPDATE SET " +
            "total_debit = daily_summary.total_debit + CASE WHEN :type = 'DEBIT' THEN :amount ELSE 0 END, " +
            "total_credit = daily_summary.total_credit + CASE WHEN :type = 'CREDIT' THEN :amount ELSE 0 END, " +
            "txn_count = daily_summary.txn_count + 1", nativeQuery = true)
    void upsertDaily(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("amount") BigDecimal amount,
            @Param("type") String type
    );

    // =============================
    // MONTHLY SUMMARY UPSERT
    // =============================
    @Modifying
    @Query(value = "INSERT INTO monthly_summary (user_id, year, month, total_spend, total_credit, txn_count) " +
            "VALUES (:userId, :year, :month, " +
            "CASE WHEN :type = 'DEBIT' THEN :amount ELSE 0 END, " +
            "CASE WHEN :type = 'CREDIT' THEN :amount ELSE 0 END, " +
            "1) " +
            "ON CONFLICT (user_id, year, month) " +
            "DO UPDATE SET " +
            "total_spend = monthly_summary.total_spend + CASE WHEN :type = 'DEBIT' THEN :amount ELSE 0 END, " +
            "total_credit = monthly_summary.total_credit + CASE WHEN :type = 'CREDIT' THEN :amount ELSE 0 END, " +
            "txn_count = monthly_summary.txn_count + 1", nativeQuery = true)
    void upsertMonthly(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("amount") BigDecimal amount,
            @Param("type") String type
    );


    @Modifying
    @Query(value = "INSERT INTO category_summary (user_id, year, month, category_id, total_amount, txn_count) " +
            "VALUES (:userId, :year, :month, :categoryId, :amount, 1) " +
            "ON CONFLICT (user_id, year, month, category_id) " +
            "DO UPDATE SET " +
            "total_amount = category_summary.total_amount + :amount, " +
            "txn_count = category_summary.txn_count + 1", nativeQuery = true)
    void upsertCategory(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("categoryId") Long categoryId,
            @Param("amount") BigDecimal amount
    );


    @Modifying
    @Query(value = "INSERT INTO entity_summary (user_id, year, month, entity_id, total_amount, txn_count) " +
            "VALUES (:userId, :year, :month, :entityId, :amount, 1) " +
            "ON CONFLICT (user_id, year, month, entity_id) " +
            "DO UPDATE SET " +
            "total_amount = entity_summary.total_amount + :amount, " +
            "txn_count = entity_summary.txn_count + 1", nativeQuery = true)
    void upsertEntity(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("entityId") Long entityId,
            @Param("amount") BigDecimal amount
    );

    @Modifying
    @Query(value = "DELETE FROM daily_summary " +
            "WHERE user_id = :userId " +
            "AND date BETWEEN :startDate AND :endDate", nativeQuery = true)
    void deleteDailyRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Modifying
    @Query(value = "DELETE FROM monthly_summary " +
            "WHERE user_id = :userId AND year = :year AND month = :month", nativeQuery = true)
    void deleteMonthly(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month
    );

    @Modifying
    @Query(value = "DELETE FROM category_summary " +
            "WHERE user_id = :userId AND year = :year AND month = :month", nativeQuery = true)
    void deleteCategory(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month
    );

    @Modifying
    @Query(value = "DELETE FROM entity_summary " +
            "WHERE user_id = :userId AND year = :year AND month = :month", nativeQuery = true)
    void deleteEntity(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month
    );

    @Modifying
    @Query(value = "INSERT INTO daily_summary (user_id, date, total_debit, total_credit, txn_count) " +
            "SELECT t.user_id, t.txn_date, " +
            "COALESCE(SUM(CASE WHEN t.txn_type = 'DEBIT' THEN t.amount ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN t.txn_type = 'CREDIT' THEN t.amount ELSE 0 END), 0), " +
            "COUNT(*) " +
            "FROM transactions t " +
            "WHERE t.user_id = :userId " +
            "AND t.txn_date BETWEEN :startDate AND :endDate " +
            "GROUP BY t.user_id, t.txn_date", nativeQuery = true)
    void rebuildDailyRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Modifying
    @Query(value = "INSERT INTO monthly_summary (user_id, year, month, total_spend, total_credit, txn_count) " +
            "SELECT t.user_id, :year, :month, " +
            "COALESCE(SUM(CASE WHEN t.txn_type = 'DEBIT' THEN t.amount ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN t.txn_type = 'CREDIT' THEN t.amount ELSE 0 END), 0), " +
            "COUNT(*) " +
            "FROM transactions t " +
            "WHERE t.user_id = :userId " +
            "AND t.txn_date BETWEEN :startDate AND :endDate " +
            "GROUP BY t.user_id", nativeQuery = true)
    void rebuildMonthly(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Modifying
    @Query(value = "INSERT INTO category_summary (user_id, year, month, category_id, total_amount, txn_count) " +
            "SELECT t.user_id, :year, :month, COALESCE(t.user_category_id, t.category_id), " +
            "SUM(t.amount), COUNT(*) " +
            "FROM transactions t " +
            "WHERE t.user_id = :userId " +
            "AND t.txn_type = 'DEBIT' " +
            "AND COALESCE(t.user_category_id, t.category_id) IS NOT NULL " +
            "AND t.txn_date BETWEEN :startDate AND :endDate " +
            "GROUP BY t.user_id, COALESCE(t.user_category_id, t.category_id)", nativeQuery = true)
    void rebuildCategory(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Modifying
    @Query(value = "INSERT INTO entity_summary (user_id, year, month, entity_id, total_amount, txn_count) " +
            "SELECT t.user_id, :year, :month, t.entity_id, " +
            "SUM(t.amount), COUNT(*) " +
            "FROM transactions t " +
            "WHERE t.user_id = :userId " +
            "AND t.txn_type = 'DEBIT' " +
            "AND t.entity_id IS NOT NULL " +
            "AND t.txn_date BETWEEN :startDate AND :endDate " +
            "GROUP BY t.user_id, t.entity_id", nativeQuery = true)
    void rebuildEntity(
            @Param("userId") Long userId,
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
