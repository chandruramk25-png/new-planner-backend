package com.newplanner.repository;

import com.newplanner.entity.ExpenseTracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseTrackerRepository extends JpaRepository<ExpenseTracker, String> {
}
