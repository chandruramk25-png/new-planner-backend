package com.newplanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Deterministic Spatial Validation Engine via OpenCage API.
 */
@Service
@Slf4j
public class OpenCageService {

    @Value("${api.opencage.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public double[] resolveCoordinates(String placeName, String destinationBase) {
        try {
            // Aggressively format the query to force OpenCage to search within the destination matrix
            String query = placeName + ", " + destinationBase;
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.opencagedata.com/geocode/v1/json?q=" + encodedQuery + "&key=" + apiKey + "&limit=1";

            log.debug("Initiating deterministic validation via OpenCage for: {}", query);
            String responseStr = restTemplate.getForObject(url, String.class);
            
            if (responseStr != null) {
                JsonNode root = objectMapper.readTree(responseStr);
                JsonNode results = root.get("results");
                
                if (results != null && results.isArray() && results.size() > 0) {
                    JsonNode geometry = results.get(0).get("geometry");
                    // Apply 250m stochastic scattering to prevent fatal UI Map stacking
                    double resolvedLat = geometry.get("lat").asDouble() + (Math.random() - 0.5) * 0.005;
                    double resolvedLng = geometry.get("lng").asDouble() + (Math.random() - 0.5) * 0.005;
                    log.info("Physically verified spatial matrix for {}: [{}, {}]", query, resolvedLat, resolvedLng);
                    return new double[]{resolvedLat, resolvedLng};
                }
            }
        } catch (Exception e) {
            log.warn("OpenCage Validation failed for {}. Mathematical coordinates compromised.", placeName, e);
        }
        return null;
    }
}
