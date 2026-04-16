package com.newplanner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "expense_trackers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseTracker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false, unique = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Itinerary itinerary;

    // The trip creator sets an overall context budget limit based on their inputs
    private Double baseBudgetLimit;

    // Currency for this tracker: "USD" or "INR"
    @Column(columnDefinition = "VARCHAR(10) DEFAULT 'USD'")
    private String currency = "USD";

    // Names of people added manually by the trip creator (e.g., "John", "Sarah")
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "expense_tracker_members", joinColumns = @JoinColumn(name = "tracker_id"))
    @Column(name = "member_name")
    private List<String> memberNames;

    @OneToMany(mappedBy = "tracker", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Expense> expenses;
}
