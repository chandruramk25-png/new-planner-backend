package com.newplanner.repository;

import com.newplanner.entity.ItineraryDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, String> {
}
