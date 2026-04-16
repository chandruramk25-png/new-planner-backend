package com.newplanner.repository;

import com.newplanner.entity.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, String> {

    // Fetch all trips owned by a Firebase user UID
    List<Itinerary> findByUserFirebaseUidOrderByCreatedAtDesc(String firebaseUid);

    /**
     * Find any existing trip that OVERLAPS with the given date range.
     * Overlap formula: existingStart <= newEnd AND existingEnd >= newStart
     */
    @Query("SELECT i FROM Itinerary i WHERE i.user.firebaseUid = :uid " +
           "AND i.startDate <= :newEnd AND i.endDate >= :newStart")
    List<Itinerary> findOverlappingTrips(
            @Param("uid")     String uid,
            @Param("newStart") LocalDate newStart,
            @Param("newEnd")   LocalDate newEnd);
}
