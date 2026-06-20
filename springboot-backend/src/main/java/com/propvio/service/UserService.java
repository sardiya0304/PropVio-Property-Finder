package com.propvio.service;

import com.propvio.model.User;
import com.propvio.model.UserInteraction;
import com.propvio.repository.UserInteractionRepository;
import com.propvio.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final UserInteractionRepository interactionRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public Optional<User> findById(Long id) {
        return userRepo.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        return userRepo.findByEmail(email);
    }

    public boolean emailExists(String email) {
        return userRepo.existsByEmail(email);
    }

    @Transactional
    public User register(String name, String email, String rawPassword) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setIsEmailVerified(true); // auto-verify; no SMTP required for demo
        userRepo.save(user);
        return user;
    }

    @Transactional
    public Optional<User> verifyEmail(String rawToken) {
        String hashedToken = sha256(rawToken);
        return userRepo.findByEmailVerificationToken(hashedToken)
            .filter(u -> u.getVerificationTokenExpiry() != null &&
                         u.getVerificationTokenExpiry().isAfter(LocalDateTime.now()))
            .map(u -> {
                u.setIsEmailVerified(true);
                u.setEmailVerificationToken(null);
                u.setVerificationTokenExpiry(null);
                userRepo.save(u);
                emailService.sendWelcomeEmail(u.getEmail(), u.getName());
                return u;
            });
    }

    public boolean checkPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    @Transactional
    public void initiatePasswordReset(String email) {
        User user = userRepo.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Email not found"));
        String rawToken = generateSecureToken();
        user.setResetToken(sha256(rawToken));
        user.setResetTokenExpire(LocalDateTime.now().plusMinutes(10));
        userRepo.save(user);
        emailService.sendPasswordResetEmail(email, rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String hashed = sha256(rawToken);
        User user = userRepo.findByResetToken(hashed)
            .filter(u -> u.getResetTokenExpire() != null &&
                         u.getResetTokenExpire().isAfter(LocalDateTime.now()))
            .orElseThrow(() -> new RuntimeException("Invalid or expired token"));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpire(null);
        userRepo.save(user);
    }

    // Records interaction for AI recommendation engine
    @Transactional
    public void recordInteraction(Long userId, Long propertyId, String location,
                                  String actionType, Double price, String propertyType) {
        userRepo.findById(userId).ifPresent(user -> {
            // Keep only last 50 interactions per user
            List<UserInteraction> recent = interactionRepo
                .findByUserIdOrderByTimestampDesc(userId, PageRequest.of(0, 50));
            if (recent.size() >= 50) {
                interactionRepo.delete(recent.get(recent.size() - 1));
            }
            interactionRepo.save(new UserInteraction(
                user, propertyId, location, actionType, price, propertyType
            ));
        });
    }

    // Auto-unsuspend cron — replaces autoUnsuspend.js
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void autoUnsuspend() {
        List<User> suspended = userRepo.findByStatusAndSuspendedUntilBefore(
            "suspended", LocalDateTime.now()
        );
        for (User u : suspended) {
            u.setStatus("active");
            u.setSuspendedUntil(null);
            userRepo.save(u);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
