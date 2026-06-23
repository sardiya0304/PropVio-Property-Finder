package com.propvio.repository;

import com.propvio.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailVerificationToken(String token);

    Optional<User> findByResetToken(String token);

    // For auto-unsuspend scheduled job
    List<User> findByStatusAndSuspendedUntilBefore(String status, LocalDateTime now);

    // Admin: filtered paginated list
    @Query("""
        SELECT u FROM User u
        WHERE (:status IS NULL OR u.status = :status)
          AND (:search IS NULL OR u.name LIKE CONCAT('%', :search, '%')
                               OR u.email LIKE CONCAT('%', :search, '%'))
        """)
    Page<User> findWithFilters(
        @Param("status") String status,
        @Param("search") String search,
        Pageable pageable
    );

    long countByStatus(String status);

    // Admin: bulk suspend
    @Modifying
    @Query("""
        UPDATE User u SET u.status = 'suspended',
          u.suspendedUntil = :suspendedUntil,
          u.suspendReason = :reason,
          u.suspendedAt = :now,
          u.suspendedBy = :by
        WHERE u.id IN :ids
        """)
    int bulkSuspend(
        @Param("ids") List<Long> ids,
        @Param("suspendedUntil") LocalDateTime suspendedUntil,
        @Param("reason") String reason,
        @Param("now") LocalDateTime now,
        @Param("by") String by
    );

    // Admin: bulk ban
    @Modifying
    @Query("""
        UPDATE User u SET u.status = 'banned',
          u.banReason = :reason,
          u.bannedAt = :now,
          u.bannedBy = :by
        WHERE u.id IN :ids
        """)
    int bulkBan(
        @Param("ids") List<Long> ids,
        @Param("reason") String reason,
        @Param("now") LocalDateTime now,
        @Param("by") String by
    );

    // Admin: top users by property count (native — no JPA relation between User and Property)
    @Query(value = """
        SELECT u.id, u.name, u.email, COUNT(p.id) AS propertyCount
        FROM users u JOIN properties p ON p.posted_by = u.id
        GROUP BY u.id, u.name, u.email
        ORDER BY propertyCount DESC
        LIMIT 10
        """, nativeQuery = true)
    List<Object[]> findTopUsersByPropertyCount();

    // Admin: new user registrations grouped by date (last 30 days)
    @Query(value = """
        SELECT DATE(created_at) AS date, COUNT(id) AS count
        FROM users
        WHERE created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
        GROUP BY DATE(created_at)
        ORDER BY date ASC
        """, nativeQuery = true)
    List<Object[]> countNewUsersLast30Days();
}
