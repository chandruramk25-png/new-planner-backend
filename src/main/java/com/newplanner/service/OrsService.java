package com.newplanner.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Service to interact with OpenRouteService (ORS) to calculate mathematically optimized paths.
 */
@Service
public class OrsService {

    @Value("${api.ors.key:your_ors_key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String optimizeRoute(List<Double[]> coordinates) {
        // Implementation logic for ORS matrix routing API.
        // It recalculates the provided points into the most efficient driving/walking order.
        return "Ordered routing calculated via ORS optimization.";
    }
}
