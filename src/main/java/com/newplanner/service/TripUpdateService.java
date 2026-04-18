package com.newplanner.service;

import com.newplanner.entity.Activity;
import com.newplanner.entity.ItineraryDay;
import com.newplanner.repository.ActivityRepository;
import com.newplanner.repository.ItineraryDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripUpdateService {

    private final ItineraryDayRepository dayRepository;
    private final ActivityRepository activityRepository;
    private final OrsRoutingService orsService;
    private final WeatherEnrichmentService weatherService;

    @Transactional
    public ItineraryDay syncWeatherForDay(String dayId) {
        ItineraryDay day = dayRepository.findById(dayId)
                .orElseThrow(() -> new RuntimeException("Day Component Not Found."));

        java.time.LocalDate tripStart = null;
        if (day.getItinerary() != null && day.getItinerary().getStartDate() != null) {
            try {
                tripStart = day.getItinerary().getStartDate();
            } catch (Exception e) {}
        }

        if (tripStart != null) {
            int dayOffset = day.getDayNumber() != null ? day.getDayNumber() - 1 : 0;
            java.time.LocalDate currentDayDate = tripStart.plusDays(dayOffset);

            for (Activity activity : day.getActivities()) {
                if (activity.getLatitude() != null && activity.getLongitude() != null) {
                    try {
                        Thread.sleep(500); // Strict API rate limit buffer
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    String[] weatherMatrix = weatherService.derivePredictiveWeather(
                            activity.getLatitude(),
                            activity.getLongitude(),
                            currentDayDate,
                            activity.getStartTime()
                    );
                    activity.setWeatherCondition(weatherMatrix[0]);
                    activity.setCriticalWeatherAlert(Boolean.parseBoolean(weatherMatrix[1]));
                }
            }
        }
        return dayRepository.save(day);
    }

    @Transactional
    public ItineraryDay updateDayActivities(String dayId, List<Activity> updatedOrderedActivities) {
        ItineraryDay day = dayRepository.findById(dayId)
                .orElseThrow(() -> new RuntimeException("Day Component Not Found."));

        // 1. Gather the absolute chronological time slots natively present in the current database bounds.
        // This mathematically ensures that when a user swaps Item Z to position Item A, the rigid itinerary clock structure holds.
        List<String[]> timeSlots = day.getActivities().stream()
                .sorted(Comparator.comparing(Activity::getStartTime))
                .map(act -> new String[]{act.getStartTime(), act.getEndTime()})
                .collect(Collectors.toList());

        // 2. Clear out the old activities exactly through JPA lifecycle cascade.
        activityRepository.deleteAll(day.getActivities());
        day.getActivities().clear();

        // 3. Inject the newly reordered UI states into the clean container.
        List<Activity> remappedMatrix = new ArrayList<>();
        
        for (int i = 0; i < updatedOrderedActivities.size(); i++) {
            Activity incoming = updatedOrderedActivities.get(i);
            Activity clone = new Activity();
            clone.setDay(day);
            clone.setPlaceName(incoming.getPlaceName());
            clone.setDescription(incoming.getDescription());
            clone.setLatitude(incoming.getLatitude());
            clone.setLongitude(incoming.getLongitude());
            clone.setWeatherCondition(incoming.getWeatherCondition());
            clone.setCriticalWeatherAlert(incoming.isCriticalWeatherAlert());
            clone.setFoodBlock(incoming.isFoodBlock());

            // Mathematical Chronological Clock Binding
            if (i < timeSlots.size()) {
                clone.setStartTime(timeSlots.get(i)[0]);
                clone.setEndTime(timeSlots.get(i)[1]);
            } else {
                clone.setStartTime(incoming.getStartTime());
                clone.setEndTime(incoming.getEndTime());
            }

            remappedMatrix.add(clone);
        }

        // 4. Algorithmically recalibrate the Transitive ORS API links sequentially.
        for (int i = 0; i < remappedMatrix.size() - 1; i++) {
            Activity current = remappedMatrix.get(i);
            Activity next = remappedMatrix.get(i + 1);

            if (current.getLatitude() != null && next.getLatitude() != null) {
                OrsRoutingService.RoutingResult routingData = orsService.calculateTransitDuration(
                        current.getLatitude(), current.getLongitude(),
                        next.getLatitude(), next.getLongitude()
                );
                current.setNextTransitDurationStr(routingData.durationStr);
                current.setRouteGeometry(routingData.geometryStr);
            }
        }

        // Add to mapped day
        day.getActivities().addAll(remappedMatrix);

        return dayRepository.save(day);
    }
}
