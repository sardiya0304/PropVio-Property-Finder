package com.propvio.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "properties", indexes = {
    @Index(columnList = "status"),
    @Index(columnList = "posted_by"),
    @Index(columnList = "status, created_at"),
    @Index(columnList = "price, beds, type, location")
})
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private Double price;

    // Images stored in a child table (replaces MongoDB array)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "property_images", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "image_url", length = 2000)
    private List<String> image = new ArrayList<>();

    @Column(nullable = false)
    private Integer beds;

    @Column(nullable = false)
    private Integer baths;

    @Column(nullable = false)
    private Double sqft;

    @Column(nullable = false)
    private String type;         // flat | house | plot | villa

    @Column(nullable = false)
    private String availability; // buy | rent

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "property_amenities", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "amenity")
    private List<String> amenities = new ArrayList<>();

    @Column(nullable = false)
    private String phone;

    @Column(length = 2000)
    private String googleMapLink = "";

    // Listing workflow
    @Column(nullable = false)
    private String status = "active"; // pending | active | rejected | expired

    @Column(name = "posted_by")
    private Long postedBy; // FK to users.id (null for admin entries)

    @Column(columnDefinition = "TEXT")
    private String rejectionReason = "";

    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Virtual field so frontend MongoDB code (_id) keeps working with MySQL integer id
    public String get_id() { return id != null ? id.toString() : null; }
}
