package com.propvio.repository;

import com.propvio.model.AdminActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AdminActivityLogRepository extends JpaRepository<AdminActivityLog, Long> {

    @Query("""
        SELECT l FROM AdminActivityLog l
        WHERE (:action IS NULL OR l.action = :action)
          AND (:targetType IS NULL OR l.targetType = :targetType)
          AND (:adminEmail IS NULL OR l.adminEmail = :adminEmail)
          AND (:startDate IS NULL OR l.createdAt >= :startDate)
          AND (:endDate IS NULL OR l.createdAt <= :endDate)
        """)
    Page<AdminActivityLog> findWithFilters(
        @Param("action") String action,
        @Param("targetType") String targetType,
        @Param("adminEmail") String adminEmail,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
}
