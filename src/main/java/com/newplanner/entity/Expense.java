package com.newplanner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracker_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ExpenseTracker tracker;

    @Column(nullable = false)
    private String description; // e.g., "Dinner at Eiffel Tower"

    @Column(nullable = false)
    private Double totalAmount;

    @Column(nullable = false)
    private String payerName; // Who fully paid the bill

    // We store the complex split math as JSON for flexibility,
    // e.g., {"John": 50.0, "Sarah": 20.0, "Me": 30.0}
    // This perfectly handles "Split Equally" OR "Custom Amounts"
    @Column(columnDefinition = "JSON", nullable = false)
    private String splitDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
