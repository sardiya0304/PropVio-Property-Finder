package com.propvio.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "users", indexes = {
    @Index(columnList = "email", unique = true),
    @Index(columnList = "status, created_at")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    // Password reset
    private String resetToken;
    private LocalDateTime resetTokenExpire;

    // Email verification
    private Boolean isEmailVerified = false;

    @Column(length = 512)
    private String emailVerificationToken;
    private LocalDateTime verificationTokenExpiry;

    // Account status
    @Column(nullable = false)
    private String status = "active"; // active | suspended | banned

    private LocalDateTime suspendedUntil;

    @Column(columnDefinition = "TEXT")
    private String banReason;

    @Column(columnDefinition = "TEXT")
    private String suspendReason;

    private LocalDateTime bannedAt;
    private LocalDateTime suspendedAt;
    private String bannedBy;
    private String suspendedBy;
    private LocalDateTime lastActive;

    // AI Recommendation: user interaction history (separate table)
    @ToString.Exclude
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UserInteraction> interactions = new ArrayList<>();

    // Saved properties (stored as property IDs)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_saved_properties", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "property_id")
    private List<Long> savedProperties = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Virtual field so frontend MongoDB code (_id) keeps working with MySQL integer id
    public String get_id() { return id != null ? id.toString() : null; }
}
