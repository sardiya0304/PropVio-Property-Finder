package com.propvio.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "user_interactions", indexes = {
    @Index(columnList = "user_id, timestamp")
})
public class UserInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private Long propertyId;
    private String location;
    private String actionType;   // view | save | contact | search
    private Double priceSeen;
    private String propertyType; // flat | house | plot | villa

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public UserInteraction(User user, Long propertyId, String location,
                           String actionType, Double priceSeen, String propertyType) {
        this.user = user;
        this.propertyId = propertyId;
        this.location = location;
        this.actionType = actionType;
        this.priceSeen = priceSeen;
        this.propertyType = propertyType;
    }
}
