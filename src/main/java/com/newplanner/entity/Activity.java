package com.newplanner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "activities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private ItineraryDay day;

    @Column(nullable = false)
    private String placeName;

    @Column(nullable = false)
    private String startTime; // Strict time constraints applied

    @Column(nullable = false)
    private String endTime;

    private Double latitude;
    private Double longitude;

    @Column(columnDefinition = "TEXT")
    private String description;

    // OTM source data — displayed as badges in the UI
    private Double otmRate;   // OTM place rating (0.0 – 10.0)
    private String otmKinds;  // OTM category tags (e.g. "historic,architecture")

    // Per-place weather — fetched individually for each activity
    private String weatherCondition;
    private boolean isCriticalWeatherAlert;
    
    // UI rendering flags and pathing data
    private boolean isFoodBlock;
    private String nextTransitDurationStr;

    @Column(columnDefinition = "TEXT")
    private String routeGeometry;
}
