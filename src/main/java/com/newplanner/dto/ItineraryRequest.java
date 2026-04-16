package com.newplanner.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.util.Map;

@Data
public class ItineraryRequest {

    @NotBlank(message = "Destination cannot be blank")
    private String destination;

    @NotNull(message = "Latitude is required")
    private Double lat;

    @NotNull(message = "Longitude is required")
    private Double lng;

    @NotBlank(message = "Start date is required")
    private String startDate;

    @NotBlank(message = "End date is required")
    private String endDate;

    @Min(value = 1, message = "Duration must be at least 1")
    @Max(value = 10, message = "Duration cannot exceed 10 days")
    private Integer durationDays;

    @Min(0) @Max(23)
    private Integer startTime;

    @Min(0) @Max(23)
    private Integer endTime;

    @NotBlank
    private String groupType;

    @NotNull(message = "Budget amount is required")
    @DecimalMin(value = "1.0", message = "Budget must be greater than 0")
    @DecimalMax(value = "10000000.0", message = "Budget cannot exceed 10,000,000")
    private Double budget;

    @NotNull
    private Map<String, Integer> interests;

    // Firebase UID of the logged-in user — links trip to the user's dashboard
    private String firebaseUid;

    // Optional user text payload
    private String customInstructions;
}
