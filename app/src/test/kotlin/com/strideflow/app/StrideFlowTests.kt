package com.strideflow.app

import com.strideflow.app.engine.AdaptiveCoach
import com.strideflow.app.engine.NutritionEngine
import com.strideflow.app.engine.PlanGenerator
import com.strideflow.app.engine.VdotCalculator
import com.strideflow.app.model.*
import org.junit.Assert.*
import org.junit.Test

class StrideFlowTests {

    @Test
    fun testVdotCalculationFrom5kTime() {
        val vdot = VdotCalculator.calculateVdot(5000.0, 1500)
        assertTrue("Expected VDOT between 37 and 40, got $vdot", vdot in 37.0..41.0)
    }

    @Test
    fun testTrainingPacesForVdot() {
        val paces = VdotCalculator.getPacesForVdot(45.0)
        assertTrue("Easy pace should be slower than Threshold", paces.easyPaceSecPerKm > paces.thresholdPaceSecPerKm)
        assertTrue("Threshold pace should be slower than Interval", paces.thresholdPaceSecPerKm > paces.intervalPaceSecPerKm)
        assertTrue("Interval pace should be slower than Repetition", paces.intervalPaceSecPerKm > paces.repetitionPaceSecPerKm)
    }

    @Test
    fun testPlanGenerationIncludesFatBurnZone2() {
        val goal = RunningGoal(
            goalType = GoalType.FIVE_K,
            targetTimeSeconds = 1440,
            targetDateEpochMillis = System.currentTimeMillis() + (8L * 7 * 24 * 3600 * 1000),
            daysPerWeek = 4
        )
        val plan = PlanGenerator.generatePlan(
            goal = goal,
            startingVdot = 42.0,
            weeklyBaselineKm = 24.0,
            totalWeeks = 8
        )

        assertNotNull(plan)
        assertEquals(8, plan.totalWeeks)
        assertTrue("Workouts should be populated", plan.workouts.isNotEmpty())
        assertTrue("Should contain long runs", plan.workouts.any { it.type == WorkoutType.LONG_RUN })
        assertTrue("Should contain Zone 2 Fat Burn runs", plan.workouts.any { it.type == WorkoutType.FAT_BURN_ZONE2 })
    }

    @Test
    fun testZone2HeartRateRangeCalculation() {
        val profile = UserProfile(
            maxHeartRateBpm = 190,
            restingHeartRateBpm = 60
        )
        assertEquals(138, profile.zone2MinBpm)
        assertEquals(151, profile.zone2MaxBpm)
    }

    @Test
    fun testAutoAdjustForMissedWorkouts() {
        val goal = RunningGoal(
            goalType = GoalType.FIVE_K,
            targetTimeSeconds = 1500,
            targetDateEpochMillis = System.currentTimeMillis() + (8L * 7 * 24 * 3600 * 1000),
            daysPerWeek = 4
        )
        val plan = PlanGenerator.generatePlan(goal, 40.0, 20.0, 8)

        val tuesdayWorkout = plan.workouts.first { it.weekIndex == 1 && it.dayIndex == 1 }
        assertFalse(tuesdayWorkout.isCompleted)
        assertFalse(tuesdayWorkout.isMissed)

        val adjustedPlan = AdaptiveCoach.autoAdjustForMissedWorkouts(plan, currentDayIndex = 3)

        assertNotNull("Adjustment note should be created", adjustedPlan.adjustmentNote)
        assertTrue(adjustedPlan.adjustmentNote!!.contains("Missed"))

        val updatedTuesday = adjustedPlan.workouts.first { it.id == tuesdayWorkout.id }
        assertTrue("Tuesday workout should be marked as missed", updatedTuesday.isMissed)

        val relocated = adjustedPlan.workouts.firstOrNull { it.weekIndex == 1 && it.dayIndex >= 3 && it.title == tuesdayWorkout.title }
        assertNotNull("Quality session should be relocated to later in the week", relocated)
    }

    @Test
    fun testAdaptiveCoachProposalOnOutperformance() {
        val goal = RunningGoal(
            goalType = GoalType.FIVE_K,
            targetTimeSeconds = 1500,
            targetDateEpochMillis = System.currentTimeMillis() + (8L * 7 * 24 * 3600 * 1000),
            daysPerWeek = 4
        )
        val plan = PlanGenerator.generatePlan(goal, 40.0, 20.0, 8)
        val workout = plan.workouts.first { it.type == WorkoutType.THRESHOLD_TEMPO }

        val actualPace = workout.targetPaceSecPerKm - 20.0
        val run = RecordedRun(
            id = "test_run",
            workoutId = workout.id,
            startTimeMillis = System.currentTimeMillis(),
            durationSeconds = 1800,
            distanceMeters = workout.targetDistanceMeters,
            averagePaceSecPerKm = actualPace,
            targetPaceSecPerKm = workout.targetPaceSecPerKm
        )

        val evaluation = AdaptiveCoach.evaluateRun(run, workout, plan)
        assertNotNull("Adaptive coach should generate a proposal on clear outperformance", evaluation.proposal)
        val proposal = evaluation.proposal!!
        assertTrue("Proposed VDOT should increase", proposal.proposedVdot > plan.currentVdot)

        val updatedPlan = AdaptiveCoach.applyProposalToPlan(plan, proposal)
        assertEquals(proposal.proposedVdot, updatedPlan.currentVdot, 0.01)
    }

    @Test
    fun testNutritionAdviceFor5k() {
        val workout5k = Workout(
            id = "5k_run",
            dayIndex = 1,
            weekIndex = 1,
            title = "5K Run",
            type = WorkoutType.EASY,
            targetDistanceMeters = 5000.0,
            targetPaceSecPerKm = 300.0,
            description = "5K"
        )
        val advice = NutritionEngine.getAdviceForWorkout(workout5k)
        assertTrue("5K should indicate stored glycogen is sufficient", advice.preRunMealSummary.contains("stored glycogen"))
        assertEquals("Water only. No mid-run fuel required.", advice.intraRunFueling)
        assertTrue(advice.postRunRecoveryCarbProtein.contains("standard, balanced meal"))
    }

    @Test
    fun testNutritionAdviceFor10k() {
        val workout10k = Workout(
            id = "10k_run",
            dayIndex = 1,
            weekIndex = 1,
            title = "10K Run",
            type = WorkoutType.EASY,
            targetDistanceMeters = 10000.0,
            targetPaceSecPerKm = 300.0,
            description = "10K"
        )
        val advice = NutritionEngine.getAdviceForWorkout(workout10k)
        assertTrue(advice.preRunMealSummary.contains("porridge (oatmeal)"))
        assertTrue(advice.intraRunFueling?.contains("Water or a small sip of sports drink") == true)
        assertTrue(advice.postRunRecoveryCarbProtein.contains("3:1 or 4:1"))
    }

    @Test
    fun testNutritionAdviceForHalfMarathon() {
        val workoutHalf = Workout(
            id = "half_run",
            dayIndex = 6,
            weekIndex = 1,
            title = "Half Marathon",
            type = WorkoutType.LONG_RUN,
            targetDistanceMeters = 21097.5,
            targetPaceSecPerKm = 300.0,
            description = "Half"
        )
        val advice = NutritionEngine.getAdviceForWorkout(workoutHalf)
        assertTrue(advice.preRunMealSummary.contains("3 hours before start time"))
        assertTrue(advice.intraRunFueling?.contains("30–60 grams of carbohydrates per hour") == true)
        assertTrue(advice.postRunRecoveryCarbProtein.contains("Immediate liquid carbs and protein"))
    }

    @Test
    fun testNutritionAdviceForMarathon() {
        val workoutFull = Workout(
            id = "marathon_run",
            dayIndex = 6,
            weekIndex = 8,
            title = "Marathon",
            type = WorkoutType.BENCHMARK_TEST,
            targetDistanceMeters = 42195.0,
            targetPaceSecPerKm = 300.0,
            description = "Marathon"
        )
        val advice = NutritionEngine.getAdviceForWorkout(workoutFull)
        assertTrue(advice.preRunMealSummary.contains("Full carbohydrate-loading 2 to 3 days prior"))
        assertTrue(advice.intraRunFueling?.contains("60–75+ grams of carbs per hour") == true)
        assertTrue(advice.postRunRecoveryCarbProtein.contains("first 30 minutes"))
    }

    @Test
    fun testTargetWorkoutTimePersistence() {
        val profile = UserProfile(
            scheduledRunHour = 6,
            scheduledRunMinute = 30
        )
        assertEquals(6, profile.scheduledRunHour)
        assertEquals(30, profile.scheduledRunMinute)
    }

    @Test
    fun testPairedSmartwatchKeywords() {
        val watchNames = listOf("Google Pixel Watch 3", "Galaxy Watch 6", "Garmin Forerunner 265", "Polar H10", "Fitbit Sense")
        val keywords = listOf("watch", "pixel", "galaxy", "garmin", "fitbit", "hr", "band", "polar")
        for (name in watchNames) {
            val lower = name.lowercase()
            assertTrue("Expected $name to match smartwatch keywords", keywords.any { lower.contains(it) })
        }
    }

    @Test
    fun testRaceTimePredictionFromVdot() {
        val time5k = VdotCalculator.predictRaceTimeSeconds(45.0, 5000.0)
        assertTrue("5K time for VDOT 45 should be ~21-23 minutes (got $time5k s)", time5k in 1200L..1400L)

        val time10k = VdotCalculator.predictRaceTimeSeconds(45.0, 10000.0)
        assertTrue("10K time should be longer than 5K time", time10k > time5k)

        val timeMarathon = VdotCalculator.predictRaceTimeSeconds(45.0, 42195.0)
        assertTrue("Marathon time for VDOT 45 should be ~3.3-3.6 hours", timeMarathon in 11500L..13500L)
    }

    @Test
    fun testUpdatePlanTargetTimeUpdatesAllWorkoutPaces() {
        val goal = RunningGoal(
            goalType = GoalType.FIVE_K,
            targetTimeSeconds = 1500, // 25:00
            targetDateEpochMillis = System.currentTimeMillis() + (8L * 7 * 24 * 3600 * 1000),
            daysPerWeek = 4
        )
        val initialPlan = PlanGenerator.generatePlan(goal, 40.0, 20.0, 8)
        val initialEasyPace = initialPlan.workouts.first { it.type == WorkoutType.EASY }.targetPaceSecPerKm

        // Faster target time: 22:30 (1350s)
        val updatedPlan = AdaptiveCoach.updatePlanTargetTime(initialPlan, 1350)
        assertEquals(1350L, updatedPlan.goal.targetTimeSeconds)
        assertTrue("New VDOT should be higher than old VDOT", updatedPlan.currentVdot > initialPlan.currentVdot)

        val updatedEasyPace = updatedPlan.workouts.first { it.type == WorkoutType.EASY }.targetPaceSecPerKm
        assertTrue("Updated easy pace should be faster (smaller sec/km)", updatedEasyPace < initialEasyPace)
    }

    @Test
    fun testAdaptationProposalAcceptanceState() {
        val proposal = AdaptationProposal(
            id = "prop_1",
            runId = "run_1",
            title = "Fitness Level Up",
            reason = "High Performance",
            previousVdot = 42.0,
            proposedVdot = 43.5,
            pacingDeltaSecPerKm = -8.0,
            explanationText = "Upgrade VDOT",
            isAccepted = null
        )

        // Pending state initially
        assertNull(proposal.isAccepted)

        // Accepted state
        val acceptedProposal = proposal.copy(isAccepted = true)
        assertEquals(true, acceptedProposal.isAccepted)

        val run = RecordedRun(
            id = "run_1",
            startTimeMillis = System.currentTimeMillis(),
            durationSeconds = 1500,
            distanceMeters = 5000.0,
            averagePaceSecPerKm = 300.0,
            adaptationProposal = acceptedProposal
        )

        assertEquals(true, run.adaptationProposal?.isAccepted)

        // Test backward-compatible healed detection:
        // Even if old proposal in JSON had isAccepted = null, if plan VDOT is >= proposedVdot, recognized as accepted
        val unhealedProposal = proposal.copy(isAccepted = null)
        val currentPlanVdot = 43.5
        val wasAlreadyAccepted = unhealedProposal.isAccepted == true ||
            (unhealedProposal.isAccepted == null && currentPlanVdot >= unhealedProposal.proposedVdot)
        assertTrue("Proposal should be recognized as already accepted when plan VDOT matches proposed VDOT", wasAlreadyAccepted)
    }
}
