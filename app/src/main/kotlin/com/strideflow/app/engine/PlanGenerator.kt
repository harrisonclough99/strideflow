package com.strideflow.app.engine

import com.strideflow.app.model.*
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToLong

object PlanGenerator {

    /**
     * Generates a complete periodized training plan on-device.
     */
    fun generatePlan(
        goal: RunningGoal,
        startingVdot: Double,
        weeklyBaselineKm: Double,
        totalWeeks: Int = 8
    ): TrainingPlan {
        val planId = UUID.randomUUID().toString()
        val paces = VdotCalculator.getPacesForVdot(startingVdot)
        val workouts = mutableListOf<Workout>()

        val daysPerWeek = goal.daysPerWeek.coerceIn(3, 6)
        var currentWeeklyKm = max(weeklyBaselineKm, 18.0)

        for (week in 1..totalWeeks) {
            val isTaperWeek = week == totalWeeks
            val isPeakWeek = week == totalWeeks - 1
            val isCutbackWeek = (week % 4 == 0) && !isPeakWeek

            // Volume progression
            val weekMileage = when {
                isTaperWeek -> currentWeeklyKm * 0.60
                isCutbackWeek -> currentWeeklyKm * 0.75
                else -> currentWeeklyKm * (1.0 + (week * 0.04).coerceAtMost(0.35))
            }

            // Distribute workouts across days of the week (0 = Mon, 2 = Wed, 4 = Fri, 6 = Sun)
            val weekSchedule = generateWeekWorkouts(
                week = week,
                totalWeeks = totalWeeks,
                goal = goal,
                weekMileageKm = weekMileage,
                daysPerWeek = daysPerWeek,
                paces = paces
            )
            workouts.addAll(weekSchedule)
        }

        return TrainingPlan(
            id = planId,
            title = "${goal.goalType.displayName} Mastery Plan",
            goal = goal,
            startingVdot = startingVdot,
            currentVdot = startingVdot,
            totalWeeks = totalWeeks,
            currentWeek = 1,
            workouts = workouts
        )
    }

    private fun generateWeekWorkouts(
        week: Int,
        totalWeeks: Int,
        goal: RunningGoal,
        weekMileageKm: Double,
        daysPerWeek: Int,
        paces: VdotCalculator.TrainingPaces
    ): List<Workout> {
        val workouts = mutableListOf<Workout>()
        val isFinalRaceWeek = week == totalWeeks

        // Long run takes ~30-35% of weekly volume
        val longRunDistKm = (weekMileageKm * 0.32).coerceAtLeast(6.0)
        // Quality workout (Tempo or Interval)
        val qualityDistKm = (weekMileageKm * 0.25).coerceAtLeast(5.0)
        // Remaining volume split between easy runs
        val remainingDistKm = max(0.0, weekMileageKm - longRunDistKm - qualityDistKm)
        val numEasyDays = (daysPerWeek - 2).coerceAtLeast(1)
        val easyDistPerDayKm = (remainingDistKm / numEasyDays).coerceAtLeast(4.0)

        // Day 0 (Monday) - Rest or Easy Run
        if (daysPerWeek >= 5) {
            workouts.add(
                createWorkout(
                    day = 0, week = week,
                    title = "Aerobic Recovery",
                    type = WorkoutType.EASY,
                    distKm = easyDistPerDayKm * 0.8,
                    pace = paces.easyPaceSecPerKm,
                    desc = "Gentle shakeout run to promote blood flow and active recovery."
                )
            )
        } else {
            workouts.add(createRestDay(0, week))
        }

        // Day 1 (Tuesday) - Quality Workout (Intervals or Tempo)
        if (isFinalRaceWeek) {
            workouts.add(
                createWorkout(
                    day = 1, week = week,
                    title = "Pre-Race Activation",
                    type = WorkoutType.SPEED_REPETITION,
                    distKm = 4.0,
                    pace = paces.thresholdPaceSecPerKm,
                    desc = "15 min easy warmup, 4x100m strides with walk recovery, 10 min easy jog."
                )
            )
        } else if (week % 2 == 1) {
            // Threshold / Tempo day
            val tempoMeters = (qualityDistKm * 0.6 * 1000).coerceIn(3000.0, 8000.0)
            workouts.add(
                Workout(
                    id = UUID.randomUUID().toString(),
                    dayIndex = 1,
                    weekIndex = week,
                    title = "Lactate Threshold Tempo",
                    type = WorkoutType.THRESHOLD_TEMPO,
                    targetDistanceMeters = qualityDistKm * 1000,
                    targetPaceSecPerKm = paces.thresholdPaceSecPerKm,
                    intervals = listOf(
                        WorkoutInterval("Warmup Jog", distanceMeters = 1500.0, targetPaceSecPerKm = paces.easyPaceSecPerKm, isRecovery = true),
                        WorkoutInterval("Threshold Block", distanceMeters = tempoMeters, targetPaceSecPerKm = paces.thresholdPaceSecPerKm),
                        WorkoutInterval("Cooldown Jog", distanceMeters = 1500.0, targetPaceSecPerKm = paces.easyPaceSecPerKm, isRecovery = true)
                    ),
                    description = "Comfortably hard continuous effort to raise your lactate clearance capacity."
                )
            )
        } else {
            // VO2max Intervals day
            workouts.add(
                Workout(
                    id = UUID.randomUUID().toString(),
                    dayIndex = 1,
                    weekIndex = week,
                    title = "VO2max Interval Repeats",
                    type = WorkoutType.INTERVAL_VO2MAX,
                    targetDistanceMeters = qualityDistKm * 1000,
                    targetPaceSecPerKm = paces.intervalPaceSecPerKm,
                    intervals = listOf(
                        WorkoutInterval("Warmup", distanceMeters = 1500.0, targetPaceSecPerKm = paces.easyPaceSecPerKm, isRecovery = true),
                        WorkoutInterval("Repeat 1 (800m)", distanceMeters = 800.0, targetPaceSecPerKm = paces.intervalPaceSecPerKm),
                        WorkoutInterval("Jog Recovery", durationSeconds = 90, targetPaceSecPerKm = paces.easyPaceSecPerKm, isRecovery = true),
                        WorkoutInterval("Repeat 2 (800m)", distanceMeters = 800.0, targetPaceSecPerKm = paces.intervalPaceSecPerKm),
                        WorkoutInterval("Jog Recovery", durationSeconds = 90, targetPaceSecPerKm = paces.easyPaceSecPerKm, isRecovery = true),
                        WorkoutInterval("Repeat 3 (800m)", distanceMeters = 800.0, targetPaceSecPerKm = paces.intervalPaceSecPerKm),
                        WorkoutInterval("Cooldown", distanceMeters = 1500.0, targetPaceSecPerKm = paces.easyPaceSecPerKm, isRecovery = true)
                    ),
                    description = "High-aerobic power repeats to sharpen your oxygen uptake ceiling."
                )
            )
        }

        // Day 2 (Wednesday) - Rest / Mid-week recovery
        workouts.add(createRestDay(2, week))

        // Day 3 (Thursday) - Mid-week Zone 2 Fat Burn or Base Run
        if (week % 2 == 1) {
            workouts.add(
                createWorkout(
                    day = 3, week = week,
                    title = "Zone 2 Fat Burn Run",
                    type = WorkoutType.FAT_BURN_ZONE2,
                    distKm = easyDistPerDayKm,
                    pace = paces.easyPaceSecPerKm + 5.0,
                    desc = "Maintain strictly Zone 2 heart rate to optimize mitochondrial density and fat oxidation."
                )
            )
        } else {
            workouts.add(
                createWorkout(
                    day = 3, week = week,
                    title = "Aerobic Base Mileage",
                    type = WorkoutType.EASY,
                    distKm = easyDistPerDayKm,
                    pace = paces.easyPaceSecPerKm,
                    desc = "Relaxed conversational pace to build mitochondrial density."
                )
            )
        }

        // Day 4 (Friday) - Easy or Rest
        if (daysPerWeek >= 4) {
            workouts.add(
                createWorkout(
                    day = 4, week = week,
                    title = "Easy Strides Run",
                    type = WorkoutType.EASY,
                    distKm = easyDistPerDayKm * 0.9,
                    pace = paces.easyPaceSecPerKm,
                    desc = "Easy run finishing with 4x20 second light strides for running economy."
                )
            )
        } else {
            workouts.add(createRestDay(4, week))
        }

        // Day 5 (Saturday) - Rest before Long Run
        workouts.add(createRestDay(5, week))

        // Day 6 (Sunday) - Long Run or Goal Race
        if (isFinalRaceWeek) {
            workouts.add(
                Workout(
                    id = UUID.randomUUID().toString(),
                    dayIndex = 6,
                    weekIndex = week,
                    title = "GOAL EVENT: ${goal.goalType.displayName}",
                    type = WorkoutType.BENCHMARK_TEST,
                    targetDistanceMeters = goal.goalType.defaultDistanceMeters,
                    targetPaceSecPerKm = (goal.targetTimeSeconds / (goal.goalType.defaultDistanceMeters / 1000.0)),
                    description = "Race day! Execute your pacing strategy and achieve your target time."
                )
            )
        } else {
            workouts.add(
                createWorkout(
                    day = 6, week = week,
                    title = "Endurance Long Run",
                    type = WorkoutType.LONG_RUN,
                    distKm = longRunDistKm,
                    pace = paces.easyPaceSecPerKm + 10.0,
                    desc = "Steady, controlled time-on-feet to train lipid metabolism and mental fortitude."
                )
            )
        }

        return workouts
    }

    private fun createWorkout(
        day: Int,
        week: Int,
        title: String,
        type: WorkoutType,
        distKm: Double,
        pace: Double,
        desc: String
    ): Workout {
        val distMeters = (distKm * 1000.0).roundToLong().toDouble()
        return Workout(
            id = UUID.randomUUID().toString(),
            dayIndex = day,
            weekIndex = week,
            title = title,
            type = type,
            targetDistanceMeters = distMeters,
            targetPaceSecPerKm = pace,
            description = desc
        )
    }

    private fun createRestDay(day: Int, week: Int): Workout {
        return Workout(
            id = UUID.randomUUID().toString(),
            dayIndex = day,
            weekIndex = week,
            title = "Rest & Mobility",
            type = WorkoutType.REST,
            targetDistanceMeters = 0.0,
            targetPaceSecPerKm = 0.0,
            description = "Active recovery, gentle stretching, and hydration. Let muscles rebuild."
        )
    }
}
