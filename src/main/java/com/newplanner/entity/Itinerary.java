package com.newplanner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "itineraries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user; // The creator of the trip

    @Column(nullable = false)
    private String destination;

    private Double destinationLat;
    private Double destinationLng;

    @Column(nullable = false)
    private Integer numberOfDays;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    private Double budget; // User-defined trip budget amount

    @Column(nullable = false)
    private String groupType; // Solo, Couple, Family, Friends

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    private List<ItineraryDay> days;

    @OneToOne(mappedBy = "itinerary", cascade = CascadeType.ALL)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ExpenseTracker expenseTracker;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
