package com.newplanner.service;

import com.newplanner.dto.ItineraryRequest;
import com.newplanner.entity.Itinerary;
import com.newplanner.entity.ItineraryDay;
import com.newplanner.entity.Activity;
import com.newplanner.entity.ExpenseTracker;
import com.newplanner.repository.ItineraryRepository;
import com.newplanner.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * OTM-First Deterministic Pipeline
 * ─────────────────────────────────────────────────────────────────────────────
 * Phase 1 : OTM Fetch + Interest Filter + Place Selection (no AI)
 * Phase 2 : ORS Nearest-Neighbor Route Optimization + Transit Times (no AI)
 * Phase 3 : Per-Place Individual Weather Fetch (most critical)
 * Phase 4 : Single AI Format Call — assigns times/descriptions/themes only
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationPipelineService {

    private final AiFallbackService aiFallbackService;
    private final ItineraryRepository itineraryRepository;
    private final UserRepository userRepository;
    private final WeatherEnrichmentService weatherService;
    private final OrsRoutingService orsService;
    private final OpenTripMapService openTripMapService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─── Interest Key → OTM kinds mapping (matches frontend Step2Preferences keys) ─
    // Curious (1) or Interested (2) = kinds INCLUDED in OTM request
    // Not Interested (0)            = kinds NOT included in OTM request at all
    private static final Map<String, String[]> INTEREST_OTM_MAP = Map.of(
        "historyculture", new String[]{"historic", "cultural"},
        "nature",         new String[]{"natural"},
        "entertainment",  new String[]{"amusements"},
        "food",           new String[]{"catering"},
        "sports",         new String[]{"sport"},
        "shopping",       new String[]{"shops"},
        "adventure",      new String[]{"natural", "amusements", "sport"},
        "relaxing",       new String[]{"natural", "interesting_places"}
    );

    @Async
    public CompletableFuture<Itinerary> orchestrateTripGeneration(ItineraryRequest request) {
        try {
            int totalPlaces = request.getDurationDays() * 4; // 4 activities per day
            log.info("=== OTM-First Pipeline START: {} | {} days | {} places needed ===",
                    request.getDestination(), request.getDurationDays(), totalPlaces);

            // =================================================================
            // PHASE 1: OTM FETCH → INTEREST FILTER → SELECTION
            // =================================================================
            log.info("Phase 1: Fetching OTM places...");

            // Build dynamic OTM kinds from active interests (score >= 1)
            String activeKinds = buildActiveKinds(request);
            log.info("Phase 1: OTM kinds param = [{}]", activeKinds);

            // Auto-scale radius: base 30km + 10km per day
            int radius = 30000 + (request.getDurationDays() * 10000);
            List<Activity> candidatePlaces = new ArrayList<>();
            
            try {
                JsonNode otmNode = openTripMapService.fetchBasePlaces(
                        request.getLat(), request.getLng(), String.valueOf(radius), activeKinds);

                candidatePlaces = extractAndFilterOtmPlaces(otmNode, request);

                // If not enough places, widen radius and re-fetch (up to 80km)
                while (candidatePlaces.size() < totalPlaces && radius < 80000) {
                    radius += 15000;
                    log.info("Phase 1: Only {} places found, widening radius to {}m", candidatePlaces.size(), radius);
                    otmNode = openTripMapService.fetchBasePlaces(
                            request.getLat(), request.getLng(), String.valueOf(radius), activeKinds);
                    candidatePlaces = extractAndFilterOtmPlaces(otmNode, request);
                }
            } catch (Exception e) {
                log.warn("CRITICAL OTM NETWORK FAILURE (e.g. DNS block or keys exhausted): {}. Pipeline triggering emergency AI Place Generator.", e.getMessage());
            }

            // Absolutely ensure we have the required number of places (invoking AI hallucination if OTM failed or under-delivered)
            if (candidatePlaces.size() < totalPlaces) {
                log.warn("OTM returned {}/{} places. Using AI fallback to generate the rest...", candidatePlaces.size(), totalPlaces);
                List<Activity> fallbackPlaces = aiFallbackService.generateFallbackPlaces(
                        request.getDestination(), request.getLat(), request.getLng(), totalPlaces - candidatePlaces.size(), activeKinds);
                candidatePlaces.addAll(fallbackPlaces);
            }

            // Cap to exactly totalPlaces
            if (candidatePlaces.size() > totalPlaces) {
                candidatePlaces = candidatePlaces.subList(0, totalPlaces);
            }
            log.info("Phase 1 complete: {} places selected.", candidatePlaces.size());

            // =================================================================
            // PHASE 2: ORS ROUTE OPTIMIZATION + TRANSIT TIMES (Matrix API)
            // =================================================================
            log.info("Phase 2: ORS route optimization for {} places...", candidatePlaces.size());

            List<Activity> orderedPlaces = nearestNeighborSort(candidatePlaces);

            // Build coordinate list for ORS Matrix API (single call for all durations)
            List<double[]> coordList = new ArrayList<>();
            for (Activity a : orderedPlaces) coordList.add(new double[]{a.getLatitude(), a.getLongitude()});

            // ONE ORS batch call → all N-1 transit durations resolved at once
            try {
                String[] transitLabels = orsService.calculateTransitMatrix(coordList);
                for (int i = 0; i < orderedPlaces.size(); i++) {
                    orderedPlaces.get(i).setNextTransitDurationStr(transitLabels[i]);
                    orderedPlaces.get(i).setRouteGeometry(null); 
                    log.info("  Transit [{}→next]: {}", orderedPlaces.get(i).getPlaceName(), transitLabels[i]);
                }
                log.info("Phase 2 complete: {} transit durations resolved in 1 ORS Matrix call.", orderedPlaces.size() - 1);
            } catch (Exception e) {
                log.warn("CRITICAL ORS NETWORK FAILURE. Using rapid 20-min fallback transits: {}", e.getMessage());
                for (int i = 0; i < orderedPlaces.size(); i++) {
                    orderedPlaces.get(i).setNextTransitDurationStr("20 mins");
                    orderedPlaces.get(i).setRouteGeometry(null);
                }
            }

            // =================================================================
            // PHASE 3: PER-PLACE INDIVIDUAL WEATHER FETCH (most critical)
            // =================================================================
            log.info("Phase 3: Fetching per-place weather for every activity...");

            LocalDate tripStartDate = LocalDate.parse(request.getStartDate().split("T")[0]);
            int placesPerDay = 4;

            for (int i = 0; i < orderedPlaces.size(); i++) {
                Activity act = orderedPlaces.get(i);
                int dayIndex = i / placesPerDay;
                LocalDate activityDate = tripStartDate.plusDays(dayIndex);

                // Use predictive weather for the specific place + day
                try {
                    Thread.sleep(200); // Light pacing for weather API
                    String[] weather = weatherService.derivePredictiveWeather(
                            act.getLatitude(), act.getLongitude(),
                            activityDate, "10:00"); // Use morning time as weather anchor
                    act.setWeatherCondition(weather[0]);
                    act.setCriticalWeatherAlert(Boolean.parseBoolean(weather[1]));
                    log.info("  Weather for {} (Day {}, {}): {} | Critical: {}",
                            act.getPlaceName(), dayIndex + 1, activityDate, weather[0], weather[1]);
                } catch (Exception e) {
                    log.warn("WEATHER API FAILURE for {}. Assigning safe Clear default.", act.getPlaceName());
                    act.setWeatherCondition("Clear");
                    act.setCriticalWeatherAlert(false);
                }
            }
            log.info("Phase 3 complete: per-place weather fetched for all {} activities.", orderedPlaces.size());

            // =================================================================
            // PHASE 4: SINGLE AI FORMAT CALL — assign times/themes only
            // =================================================================
            log.info("Phase 4: Single AI formatting call...");

            String contextMatrix = buildContextMatrix(orderedPlaces, tripStartDate, placesPerDay);
            String timeBounds = String.format("%02d:00 to %02d:00",
                    request.getStartTime() != null ? request.getStartTime() : 8,
                    request.getEndTime()   != null ? request.getEndTime()   : 18);

            String aiSys = "You are a travel itinerary formatter. You receive a fixed ordered list of real places " +
                    "with their OTM data, transit durations, and weather. Your ONLY job is to produce a final day-wise " +
                    "JSON itinerary. Rules: (1) Do NOT add, remove, or reorder any places. (2) Assign realistic " +
                    "startTime and endTime within the given daily window, accounting for transit gaps between places. " +
                    "(3) Write a 2-sentence description for each place using its OTM kinds and rate for context. " +
                    "(4) Give each day a short creative theme. " +
                    "Return ONLY raw JSON: {\"days\":[{\"dayNumber\":1,\"theme\":\"string\",\"activities\":[" +
                    "{\"placeName\":\"string\",\"startTime\":\"09:00\",\"endTime\":\"10:30\",\"description\":\"string\"}]}]}";

            String aiPrompt = "Format the following " + request.getDurationDays() + "-day itinerary for "
                    + request.getDestination() + ".\n"
                    + "Daily window: " + timeBounds + " | Group: " + request.getGroupType()
                    + " | Budget: ₹" + (request.getBudget() != null ? String.format("%.0f", request.getBudget()) : "50000") + "\n"
                    + "IMPORTANT: All " + orderedPlaces.size() + " places below MUST appear in the output. "
                    + "Spread them across exactly " + request.getDurationDays() + " days (4 places/day). "
                    + "Factor transit time into scheduling — do not overlap activities.\n\n"
                    + contextMatrix;

            String rawAiJson = aiFallbackService.callAi(aiSys, aiPrompt);
            log.info("Phase 4 AI raw output length: {} chars", rawAiJson.length());

            String cleanedAiJson = rawAiJson.replaceAll("(?s)```json", "").replaceAll("```", "").trim();

            // =================================================================
            // MAP JSON → JPA ENTITIES (real OTM data fills coords / rate / kinds)
            // =================================================================
            Itinerary itinerary = new Itinerary();
            itinerary.setDestination(request.getDestination());
            itinerary.setDestinationLat(request.getLat());
            itinerary.setDestinationLng(request.getLng());
            itinerary.setNumberOfDays(request.getDurationDays());
            itinerary.setBudget(request.getBudget() != null ? request.getBudget() : 50000.0);
            itinerary.setGroupType(request.getGroupType() != null ? request.getGroupType() : "Couple");
            itinerary.setStartDate(tripStartDate);
            itinerary.setEndDate(LocalDate.parse(request.getEndDate().split("T")[0]));
            itinerary.setDays(new ArrayList<>());

            JsonNode aiRoot = objectMapper.readTree(cleanedAiJson);
            JsonNode daysNode = aiRoot.has("days") ? aiRoot.get("days") : aiRoot;

            // Build a lookup map: normalised name → ordered Activity (with real OTM data)
            java.util.Map<String, Activity> dataMap = new java.util.LinkedHashMap<>();
            for (Activity a : orderedPlaces) {
                dataMap.put(normalizeName(a.getPlaceName()), a);
            }

            // Track which orderedPlaces index to use as a positional fallback
            int fallbackPointer = 0;

            if (daysNode != null && daysNode.isArray()) {
                for (JsonNode dayNode : daysNode) {
                    ItineraryDay day = new ItineraryDay();
                    day.setItinerary(itinerary);
                    day.setDayNumber(dayNode.has("dayNumber") ? dayNode.get("dayNumber").asInt() : 1);
                    day.setDate(tripStartDate.plusDays(day.getDayNumber() - 1));
                    day.setTheme(dayNode.has("theme") ? dayNode.get("theme").asText() : "Exploration Day");
                    day.setActivities(new ArrayList<>());

                    JsonNode actArray = dayNode.get("activities");
                    if (actArray != null && actArray.isArray()) {
                        for (JsonNode aNode : actArray) {
                            String aiName = aNode.has("placeName") ? aNode.get("placeName").asText() : "";

                            // Match AI-assigned name back to real OTM data
                            Activity realData = findBestMatch(aiName, dataMap);

                            // Positional fallback: if AI hallucinates a name, use next unused real place
                            if (realData == null && fallbackPointer < orderedPlaces.size()) {
                                realData = orderedPlaces.get(fallbackPointer);
                                log.warn("AI name '{}' not matched — using positional fallback: {}", aiName, realData.getPlaceName());
                            }
                            if (realData == null) continue;

                            // Remove matched entry so it can't be assigned twice
                            dataMap.remove(normalizeName(realData.getPlaceName()));
                            fallbackPointer++;

                            Activity finalAct = new Activity();
                            finalAct.setDay(day);
                            finalAct.setPlaceName(realData.getPlaceName());      // Real OTM name
                            finalAct.setLatitude(realData.getLatitude());        // Real OTM lat
                            finalAct.setLongitude(realData.getLongitude());      // Real OTM lng
                            finalAct.setOtmRate(realData.getOtmRate());          // OTM rating
                            finalAct.setOtmKinds(realData.getOtmKinds());        // OTM categories
                            finalAct.setWeatherCondition(realData.getWeatherCondition());
                            finalAct.setCriticalWeatherAlert(realData.isCriticalWeatherAlert());
                            finalAct.setNextTransitDurationStr(realData.getNextTransitDurationStr());
                            finalAct.setRouteGeometry(realData.getRouteGeometry());
                            finalAct.setStartTime(aNode.has("startTime") ? aNode.get("startTime").asText() : "09:00");
                            finalAct.setEndTime(aNode.has("endTime")     ? aNode.get("endTime").asText()   : "10:30");
                            finalAct.setDescription(aNode.has("description") ? aNode.get("description").asText() : "A curated stop at this location.");
                            finalAct.setFoodBlock(false);

                            day.getActivities().add(finalAct);
                        }
                    }
                    itinerary.getDays().add(day);
                }
            }

            // Safety: if any orderedPlaces were not placed by AI (e.g. AI truncated output),
            // append them to the last day to prevent data loss
            if (!dataMap.isEmpty()) {
                log.warn("AI did not schedule {} places — appending to last day as safety.", dataMap.size());
                ItineraryDay lastDay = itinerary.getDays().isEmpty() ? null
                        : itinerary.getDays().get(itinerary.getDays().size() - 1);
                if (lastDay != null) {
                    for (Activity leftover : dataMap.values()) {
                        Activity finalAct = new Activity();
                        finalAct.setDay(lastDay);
                        finalAct.setPlaceName(leftover.getPlaceName());
                        finalAct.setLatitude(leftover.getLatitude());
                        finalAct.setLongitude(leftover.getLongitude());
                        finalAct.setOtmRate(leftover.getOtmRate());
                        finalAct.setOtmKinds(leftover.getOtmKinds());
                        finalAct.setWeatherCondition(leftover.getWeatherCondition());
                        finalAct.setCriticalWeatherAlert(leftover.isCriticalWeatherAlert());
                        finalAct.setNextTransitDurationStr(leftover.getNextTransitDurationStr());
                        finalAct.setRouteGeometry(leftover.getRouteGeometry());
                        finalAct.setStartTime("18:00");
                        finalAct.setEndTime("19:00");
                        finalAct.setDescription("A notable location worth visiting.");
                        lastDay.getActivities().add(finalAct);
                    }
                }
            }

            // Sync User + Expense Ledger
            if (request.getFirebaseUid() != null && !request.getFirebaseUid().isBlank()) {
                com.newplanner.entity.User user = userRepository.findById(request.getFirebaseUid()).orElse(null);
                if (user == null) {
                    user = new com.newplanner.entity.User();
                    user.setFirebaseUid(request.getFirebaseUid());
                    user.setName("Voyager");
                    user.setEmail(request.getFirebaseUid() + "@placeholder.com");
                    userRepository.save(user);
                }
                itinerary.setUser(user);
            }
            ExpenseTracker tracker = new ExpenseTracker();
            tracker.setItinerary(itinerary);
            // Link the specific user-provided budget from the request to the financial ledger
            tracker.setBaseBudgetLimit(request.getBudget() != null ? request.getBudget() : 50000.0);
            tracker.setMemberNames(new ArrayList<>());
            itinerary.setExpenseTracker(tracker);

            Itinerary saved = itineraryRepository.save(itinerary);
            log.info("=== OTM-First Pipeline COMPLETE. Saved ID: {} | Days: {} | Total Activities: {} ===",
                    saved.getId(), saved.getDays().size(),
                    saved.getDays().stream().mapToInt(d -> d.getActivities().size()).sum());
            return CompletableFuture.completedFuture(saved);

        } catch (Exception e) {
            log.error("OTM-First Pipeline FAILED: ", e);
            throw new RuntimeException("OTM-First Pipeline Failed: " + e.getMessage(), e);
        }
    }

    // ─── OTM Extraction + Interest Filtering ─────────────────────────────────

    private List<Activity> extractAndFilterOtmPlaces(JsonNode otmNode, ItineraryRequest request) {
        List<Activity> result = new ArrayList<>();
        if (otmNode == null || !otmNode.has("features") || !otmNode.get("features").isArray()) {
            return result;
        }

        java.util.Set<String> seenNames = new java.util.HashSet<>();

        // Build blocked kinds (score == 0 = Not Interested → omit)
        java.util.Set<String> blockedKinds = new java.util.HashSet<>();
        // Build priority kinds (score == 2 = Interested → must include, sort to top)
        java.util.Set<String> priorityKinds = new java.util.HashSet<>();

        if (request.getInterests() != null) {
            for (Map.Entry<String, Integer> entry : request.getInterests().entrySet()) {
                String[] mapped = INTEREST_OTM_MAP.get(entry.getKey().toLowerCase());
                if (mapped == null) continue;
                int score = entry.getValue() == null ? 1 : entry.getValue();
                if (score == 0) {
                    for (String k : mapped) blockedKinds.add(k);
                } else if (score == 2) {
                    for (String k : mapped) priorityKinds.add(k);
                }
                // score == 1 (Curious) = include normally, no action needed
            }
        }
        log.info("Phase 1 interest filter — blocked: {} | priority: {}", blockedKinds, priorityKinds);

        for (JsonNode feature : otmNode.get("features")) {
            JsonNode props = feature.path("properties");
            String name = props.path("name").asText("").trim();
            if (name.isEmpty() || isCorporateOrMundane(name)) continue;

            String kinds = props.path("kinds").asText("").toLowerCase();

            // Skip blocked kinds (score == 0)
            boolean blocked = false;
            for (String bk : blockedKinds) {
                if (kinds.contains(bk)) { blocked = true; break; }
            }
            if (blocked) continue;

            // Deduplication
            String normName = normalizeName(name);
            if (seenNames.contains(normName) || normName.length() < 3) continue;
            seenNames.add(normName);

            double lng = feature.path("geometry").path("coordinates").path(0).asDouble();
            double lat = feature.path("geometry").path("coordinates").path(1).asDouble();
            if (lat == 0.0 && lng == 0.0) continue;

            double rate = props.path("rate").asDouble(0.0);

            // Check if this place matches a priority kind (score == 2)
            boolean isPriority = false;
            for (String pk : priorityKinds) {
                if (kinds.contains(pk)) { isPriority = true; break; }
            }

            Activity act = new Activity();
            act.setPlaceName(name);
            act.setLatitude(lat);
            act.setLongitude(lng);
            act.setOtmRate(isPriority ? rate + 100.0 : rate); // Boost priority places to top
            act.setOtmKinds(kinds.length() > 100 ? kinds.substring(0, 100) : kinds);
            result.add(act);
        }

        // Sort by effective score descending (priority places first, then by OTM rate)
        result.sort((a, b) -> Double.compare(
                b.getOtmRate() != null ? b.getOtmRate() : 0.0,
                a.getOtmRate() != null ? a.getOtmRate() : 0.0));

        // Restore real OTM rate after sorting (remove the +100 boost)
        result.forEach(a -> {
            if (a.getOtmRate() != null && a.getOtmRate() > 10.0) {
                a.setOtmRate(a.getOtmRate() - 100.0);
            }
        });
        return result;
    }

    // ─── Build dynamic OTM kinds string from user's active interests ───────────────────
    // Collects kinds for score >= 1 (Curious or Interested), deduplicates, joins with comma.
    // Result: "historic,cultural,natural,catering" — sent directly to the OTM kinds= param.

    private String buildActiveKinds(ItineraryRequest request) {
        if (request.getInterests() == null || request.getInterests().isEmpty()) {
            return "interesting_places"; // safe fallback
        }
        java.util.Set<String> kindsSet = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : request.getInterests().entrySet()) {
            int score = entry.getValue() == null ? 1 : entry.getValue();
            if (score >= 1) { // Curious or Interested → include
                String[] mapped = INTEREST_OTM_MAP.get(entry.getKey().toLowerCase().replace(" ", ""));
                if (mapped != null) {
                    for (String k : mapped) kindsSet.add(k);
                }
            }
        }
        return kindsSet.isEmpty() ? "interesting_places" : String.join(",", kindsSet);
    }

    // ─── Nearest-Neighbor TSP (deterministic, no AI) ──────────────────────────────

    private List<Activity> nearestNeighborSort(List<Activity> places) {
        if (places.isEmpty()) return places;
        List<Activity> remaining = new ArrayList<>(places);
        List<Activity> ordered = new ArrayList<>();

        Activity current = remaining.remove(0);
        ordered.add(current);

        while (!remaining.isEmpty()) {
            Activity nearest = null;
            double minDist = Double.MAX_VALUE;
            for (Activity candidate : remaining) {
                double d = haversineDist(current.getLatitude(), current.getLongitude(),
                                         candidate.getLatitude(), candidate.getLongitude());
                if (d < minDist) { minDist = d; nearest = candidate; }
            }
            remaining.remove(nearest);
            ordered.add(nearest);
            current = nearest;
        }
        return ordered;
    }

    // ─── Context matrix for AI format call ────────────────────────────────────

    private String buildContextMatrix(List<Activity> places, LocalDate startDate, int perDay) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < places.size(); i++) {
            Activity a = places.get(i);
            int dayNum = (i / perDay) + 1;
            LocalDate date = startDate.plusDays(dayNum - 1);
            String transitToNext = (i < places.size() - 1) ? a.getNextTransitDurationStr() : "—";
            sb.append(String.format("Day%d | %s | lat:%.5f | lng:%.5f | rate:%.1f | kinds:%s | transit_next:%s | weather:%s(%s)\n",
                    dayNum,
                    a.getPlaceName(),
                    a.getLatitude(),
                    a.getLongitude(),
                    a.getOtmRate() != null ? a.getOtmRate() : 0.0,
                    a.getOtmKinds() != null ? a.getOtmKinds() : "—",
                    transitToNext,
                    a.getWeatherCondition() != null ? a.getWeatherCondition() : "Clear",
                    a.isCriticalWeatherAlert() ? "ALERT" : "ok"
            ));
        }
        return sb.toString();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private Activity findBestMatch(String aiName, java.util.Map<String, Activity> dataMap) {
        if (aiName == null || aiName.isBlank()) return null;
        String key = normalizeName(aiName);
        if (dataMap.containsKey(key)) return dataMap.get(key);
        // Substring match for minor AI name variations
        for (Map.Entry<String, Activity> e : dataMap.entrySet()) {
            if (key.length() > 5 && e.getKey().contains(key)) return e.getValue();
            if (e.getKey().length() > 5 && key.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    private double haversineDist(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private boolean isCorporateOrMundane(String name) {
        if (name == null) return true;
        String lower = name.toLowerCase();
        return lower.contains("bank") || lower.contains("atm") || lower.contains("hospital") ||
               lower.contains("clinic") || lower.contains("school") || lower.contains("college") ||
               lower.contains("university") || lower.contains("institute") || lower.contains("gym") ||
               lower.contains("grocery") || lower.contains("supermarket") || lower.contains("petrol") ||
               lower.contains("gas station") || lower.contains("police") || lower.contains("post office") ||
               lower.contains("bus stop") || lower.contains("typewriting") || lower.contains("tuition") ||
               lower.contains("pvt") || lower.contains("ltd") || lower.contains("corporation") ||
               lower.contains("hotel") || lower.contains("lodge") || lower.contains("restaurant") ||
               lower.contains("bakery") || lower.contains("store") || lower.contains("shop") ||
               lower.contains("mart") || lower.contains("parlor") || lower.contains("parlour") ||
               lower.contains("beer") || lower.contains("wine") || lower.contains("liquor") ||
               lower.contains("ration") || lower.contains("indian oil") || lower.contains("bharat petroleum") ||
               lower.contains("farm ") || lower.contains("office") || lower.contains("agency") ||
               lower.contains("convent") || lower.contains("collective farm");
    }
}
