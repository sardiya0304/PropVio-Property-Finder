package com.propvio.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "appointments", indexes = {
    @Index(columnList = "user_id, date"),
    @Index(columnList = "property_id, date"),
    @Index(columnList = "status")
})
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long propertyId;

    private Long userId; // null for guest bookings

    // Guest info (flattened — no separate table needed)
    private String guestName;
    private String guestEmail;
    private String guestPhone;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String time;

    private String status = "pending"; // pending | confirmed | cancelled | completed

    @Column(length = 2000)
    private String meetingLink;

    private String meetingPlatform = "other"; // zoom | google-meet | teams | other

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String cancelReason;

    private Boolean reminderSent = false;

    // Feedback (flattened)
    private Integer feedbackRating; // 1–5
    private String feedbackComment;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
