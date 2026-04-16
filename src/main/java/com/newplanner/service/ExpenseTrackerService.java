package com.newplanner.service;

import com.newplanner.entity.Expense;
import com.newplanner.entity.ExpenseTracker;
import com.newplanner.entity.Itinerary;
import com.newplanner.repository.ExpenseRepository;
import com.newplanner.repository.ExpenseTrackerRepository;
import com.newplanner.repository.ItineraryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseTrackerService {

    private final ExpenseTrackerRepository trackerRepository;
    private final ExpenseRepository expenseRepository;
    private final ItineraryRepository itineraryRepository;

    public ExpenseTracker getTrackerByTripId(String tripId) {
        Itinerary itinerary = itineraryRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));

        ExpenseTracker tracker = itinerary.getExpenseTracker();
        if (tracker == null) {
            throw new RuntimeException("No expense tracker found for trip: " + tripId);
        }
        return tracker;
    }

    public ExpenseTracker updateMembers(String tripId, List<String> memberNames) {
        ExpenseTracker tracker = getTrackerByTripId(tripId);
        tracker.setMemberNames(memberNames);
        return trackerRepository.save(tracker);
    }

    public ExpenseTracker updateCurrency(String tripId, String currency) {
        ExpenseTracker tracker = getTrackerByTripId(tripId);
        if (tracker.getExpenses() != null && !tracker.getExpenses().isEmpty()) {
            throw new IllegalStateException("Currency cannot be modified after financial vectors have been committed to the ledger.");
        }
        tracker.setCurrency(currency);
        return trackerRepository.save(tracker);
    }

    public ExpenseTracker updateBudgetLimit(String tripId, Double newLimit) {
        ExpenseTracker tracker = getTrackerByTripId(tripId);
        tracker.setBaseBudgetLimit(newLimit);
        return trackerRepository.save(tracker);
    }

    public Expense addExpense(String tripId, Expense expense) {
        if (expense.getTotalAmount() == null || expense.getTotalAmount() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be strictly greater than zero.");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode splits = mapper.readTree(expense.getSplitDetails());
            double sum = 0.0;
            var iterator = splits.elements();
            while (iterator.hasNext()) {
                sum += iterator.next().asDouble(0.0);
            }
            if (Math.abs(sum - expense.getTotalAmount()) > 0.01) {
                throw new IllegalArgumentException(String.format("Split math error: matrix sum (%.2f) does not equal total (%.2f).", sum, expense.getTotalAmount()));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid split details payload.", e);
        }

        ExpenseTracker tracker = getTrackerByTripId(tripId);
        expense.setTracker(tracker);
        return expenseRepository.save(expense);
    }

    public void removeExpense(String expenseId) {
        expenseRepository.deleteById(expenseId);
    }
}
