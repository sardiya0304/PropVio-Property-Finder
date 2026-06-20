package com.propvio.repository;

import com.propvio.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
