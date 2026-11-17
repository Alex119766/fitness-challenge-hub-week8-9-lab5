package com.example.FitnessChallengeApp_Test.repository;

import com.example.FitnessChallengeApp_Test.model.WorkoutLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for WorkoutLog entities.
 * Implements derived query methods and custom @Query JPQL methods
 * supporting Pageable pagination and dynamic sorting.
 */
@Repository
public interface WorkoutLogRepository extends JpaRepository<WorkoutLog, Long> {

    // 1. Derived Query: Case-insensitive partial athlete name search with pagination
    Page<WorkoutLog> findByAthleteNameContainingIgnoreCase(String athleteName, Pageable pageable);

    // 2. Derived Query: Filter workout logs burning at least minCalories kcal
    Page<WorkoutLog> findByCaloriesBurnedGreaterThanEqual(int minCalories, Pageable pageable);

    // 3. Custom JPQL @Query: Rank workout logs by highest calorie burn descending
    @Query("SELECT w FROM WorkoutLog w ORDER BY w.caloriesBurned DESC")
    Page<WorkoutLog> findHighestCalorieLogs(Pageable pageable);

    // 4. Custom JPQL @Query with positional parameters (?1, ?2):
    // Finds workout logs matching intensity level (case-insensitive) and lasting at least minDuration minutes
    @Query("SELECT w FROM WorkoutLog w WHERE LOWER(w.intensityLevel) = LOWER(?1) AND w.durationMinutes >= ?2 ORDER BY w.workoutDate DESC")
    Page<WorkoutLog> findLogsByIntensityAndMinDuration(String intensity, int minDuration, Pageable pageable);
}
