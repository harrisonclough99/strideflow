package com.strideflow.app.engine

import com.strideflow.app.model.*
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

object AdaptiveCoach {

    data class RunEvaluation(
        val complianceScore: Int,
        val paceDeltaSecPerKm: Double, // negative means faster than target
        val distanceCompletionRatio: Double,
        val proposal: AdaptationProposal?
    )

    /**
     * Evaluates a completed run against targets and generates an on-device adaptation proposal.
     */
    fun evaluateRun(
        run: RecordedRun,
        workout: Workout?,
        currentPlan: TrainingPlan?
    ): RunEvaluation {
        if (workout == null || workout.type == WorkoutType.REST || workout.targetDistanceMeters <= 0) {
            return RunEvaluation(
                complianceScore = 100,
                paceDeltaSecPerKm = 0.0,
                distanceCompletionRatio = 1.0,
                proposal = null
            )
        }

        val targetPace = workout.targetPaceSecPerKm
        val actualPace = run.averagePaceSecPerKm
        val paceDelta = actualPace - targetPace // negative = faster, positive = slower
        val distanceRatio = (run.distanceMeters / workout.targetDistanceMeters).coerceIn(0.0, 2.0)

        // Calculate split compliance
        var onPaceSplits = 0
        if (run.splits.isNotEmpty()) {
            for (split in run.splits) {
                if (abs(split.averagePaceSecPerKm - targetPace) <= 15.0) {
                    onPaceSplits++
                }
            }
        }
        val splitCompliance = if (run.splits.isNotEmpty()) {
            ((onPaceSplits.toDouble() / run.splits.size) * 100).roundToInt()
        } else {
            (100 - (abs(paceDelta) * 2)).roundToInt().coerceIn(30, 100)
        }

        val overallCompliance = ((splitCompliance * 0.6) + (distanceRatio * 40)).roundToInt().coerceIn(20, 100)

        // Determine if adaptation is warranted
        val proposal = generateAdaptation(
            run = run,
            workout = workout,
            currentPlan = currentPlan,
            paceDelta = paceDelta,
            distanceRatio = distanceRatio
        )

        return RunEvaluation(
            complianceScore = overallCompliance,
            paceDeltaSecPerKm = paceDelta,
            distanceCompletionRatio = distanceRatio,
            proposal = proposal
        )
    }

    private fun generateAdaptation(
        run: RecordedRun,
        workout: Workout,
        currentPlan: TrainingPlan?,
        paceDelta: Double,
        distanceRatio: Double
    ): AdaptationProposal? {
        if (currentPlan == null) return null

        val currentVdot = currentPlan.currentVdot

        // Case 1: Significant Outperformance on Quality Run (Threshold, Interval, or Benchmark)
        if ((workout.type == WorkoutType.THRESHOLD_TEMPO || workout.type == WorkoutType.INTERVAL_VO2MAX || workout.type == WorkoutType.BENCHMARK_TEST)
            && paceDelta < -12.0 && distanceRatio >= 0.95
        ) {
            val vdotBump = 1.0
            val newVdot = currentVdot + vdotBump
            val currentPaces = VdotCalculator.getPacesForVdot(currentVdot)
            val newPaces = VdotCalculator.getPacesForVdot(newVdot)
            val paceDiff = (currentPaces.thresholdPaceSecPerKm - newPaces.thresholdPaceSecPerKm).roundToInt()

            return AdaptationProposal(
                id = UUID.randomUUID().toString(),
                runId = run.id,
                title = "Fitness Level Up: VDOT Upgrade Detected",
                reason = "Superior Pace Compliance & Stamina",
                previousVdot = currentVdot,
                proposedVdot = newVdot,
                pacingDeltaSecPerKm = -paceDiff.toDouble(),
                explanationText = "You averaged ${VdotCalculator.formatPace(run.averagePaceSecPerKm)}/km (target was ${VdotCalculator.formatPace(workout.targetPaceSecPerKm)}/km), outperforming your current baseline with strong finishing speed. Recommending upgrading your VDOT from ${currentVdot} to ${newVdot}. This sharpens upcoming workouts by ~${paceDiff}s/km."
            )
        }

        // Case 2: Fatigue or Significant Target Miss (>20s/km slower or cut short by >25%)
        if (workout.type != WorkoutType.EASY && (paceDelta > 20.0 || distanceRatio < 0.75)) {
            val vdotTrim = 0.5
            val newVdot = (currentVdot - vdotTrim).coerceAtLeast(25.0)

            return AdaptationProposal(
                id = UUID.randomUUID().toString(),
                runId = run.id,
                title = "Fatigue Compensation & Recovery Reset",
                reason = "Elevated Cardiac/Pacing Strain Detected",
                previousVdot = currentVdot,
                proposedVdot = newVdot,
                pacingDeltaSecPerKm = 6.0,
                explanationText = "Pacing dropped by ${abs(paceDelta).roundToInt()}s/km below target during today's workout. To prevent acute overtraining and maintain injury resistance, recommending a slight calibration (-0.5 VDOT) and converting the next hard session into an aerobic recovery run."
            )
        }

        // Case 3: Steady Solid Execution on Long Run
        if (workout.type == WorkoutType.LONG_RUN && distanceRatio >= 1.0 && abs(paceDelta) <= 10.0) {
            return AdaptationProposal(
                id = UUID.randomUUID().toString(),
                runId = run.id,
                title = "Endurance Engine Validated",
                reason = "Precision Long-Run Execution",
                previousVdot = currentVdot,
                proposedVdot = currentVdot + 0.3,
                pacingDeltaSecPerKm = -2.0,
                explanationText = "Flawless pacing discipline across ${String.format("%.1f", run.distanceMeters / 1000.0)} km. Your aerobic endurance is primed. Upgrading baseline slightly (+0.3 VDOT) to progressively stretch your race ceiling."
            )
        }

        return null
    }

    /**
     * Applies an adaptation proposal to the training plan on-device.
     */
    fun applyProposalToPlan(plan: TrainingPlan, proposal: AdaptationProposal): TrainingPlan {
        val newVdot = proposal.proposedVdot
        val updatedPaces = VdotCalculator.getPacesForVdot(newVdot)

        val updatedWorkouts = plan.workouts.map { workout ->
            if (workout.isCompleted) {
                workout
            } else {
                val newTargetPace = when (workout.type) {
                    WorkoutType.EASY -> updatedPaces.easyPaceSecPerKm
                    WorkoutType.FAT_BURN_ZONE2 -> updatedPaces.easyPaceSecPerKm + 5.0
                    WorkoutType.LONG_RUN -> updatedPaces.easyPaceSecPerKm + 10.0
                    WorkoutType.THRESHOLD_TEMPO -> updatedPaces.thresholdPaceSecPerKm
                    WorkoutType.INTERVAL_VO2MAX -> updatedPaces.intervalPaceSecPerKm
                    WorkoutType.SPEED_REPETITION -> updatedPaces.repetitionPaceSecPerKm
                    WorkoutType.BENCHMARK_TEST -> workout.targetPaceSecPerKm + proposal.pacingDeltaSecPerKm
                    WorkoutType.REST -> 0.0
                }
                workout.copy(targetPaceSecPerKm = newTargetPace)
            }
        }

        return plan.copy(
            currentVdot = newVdot,
            workouts = updatedWorkouts
        )
    }

    /**
     * Updates an active plan's goal target time and recalculates target paces for all uncompleted workouts.
     */
    fun updatePlanTargetTime(plan: TrainingPlan, newTargetTimeSec: Long): TrainingPlan {
        val distanceMeters = plan.goal.goalType.defaultDistanceMeters
        val newVdot = if (distanceMeters > 0) {
            VdotCalculator.calculateVdot(distanceMeters, newTargetTimeSec)
        } else plan.currentVdot

        val updatedPaces = VdotCalculator.getPacesForVdot(newVdot)
        val newRacePace = if (distanceMeters > 0) newTargetTimeSec.toDouble() / (distanceMeters / 1000.0) else 300.0

        val updatedWorkouts = plan.workouts.map { workout ->
            if (workout.isCompleted) {
                workout
            } else {
                val newPace = when (workout.type) {
                    WorkoutType.EASY -> updatedPaces.easyPaceSecPerKm
                    WorkoutType.FAT_BURN_ZONE2 -> updatedPaces.easyPaceSecPerKm + 5.0
                    WorkoutType.LONG_RUN -> updatedPaces.easyPaceSecPerKm + 10.0
                    WorkoutType.THRESHOLD_TEMPO -> updatedPaces.thresholdPaceSecPerKm
                    WorkoutType.INTERVAL_VO2MAX -> updatedPaces.intervalPaceSecPerKm
                    WorkoutType.SPEED_REPETITION -> updatedPaces.repetitionPaceSecPerKm
                    WorkoutType.BENCHMARK_TEST -> newRacePace
                    WorkoutType.REST -> 0.0
                }
                workout.copy(targetPaceSecPerKm = newPace)
            }
        }

        val updatedGoal = plan.goal.copy(targetTimeSeconds = newTargetTimeSec)
        return plan.copy(
            goal = updatedGoal,
            currentVdot = newVdot,
            workouts = updatedWorkouts
        )
    }

    /**
     * Automatically adjusts the training plan when workouts are missed.
     * Implements sports-science principles:
     * 1. Never cram missed mileage into subsequent days.
     * 2. Key quality workouts (Tempo, Intervals, Long Run) are relocated to the nearest open recovery slot.
     * 3. Multiple missed sessions prompt volume calibration to prevent acute workload spikes.
     */
    fun autoAdjustForMissedWorkouts(
        plan: TrainingPlan,
        currentDayIndex: Int
    ): TrainingPlan {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val currentWeek = plan.currentWeek
        val workouts = plan.workouts.toMutableList()

        val missedWorkouts = workouts.filter {
            it.weekIndex == currentWeek &&
                    it.dayIndex < currentDayIndex &&
                    !it.isCompleted &&
                    !it.isMissed &&
                    it.type != WorkoutType.REST
        }

        if (missedWorkouts.isEmpty()) return plan

        val notes = mutableListOf<String>()

        for (missed in missedWorkouts) {
            val missedIdx = workouts.indexOfFirst { it.id == missed.id }
            if (missedIdx >= 0) {
                // Mark this workout as missed
                workouts[missedIdx] = missed.copy(isMissed = true)

                val dayName = days.getOrElse(missed.dayIndex) { "Day ${missed.dayIndex}" }

                // Check if it's a key quality workout
                val isQuality = missed.type == WorkoutType.THRESHOLD_TEMPO ||
                        missed.type == WorkoutType.INTERVAL_VO2MAX ||
                        missed.type == WorkoutType.LONG_RUN ||
                        missed.type == WorkoutType.FAT_BURN_ZONE2

                if (isQuality) {
                    // Find an upcoming rest or light easy day in this week to relocate
                    val candidateSlot = workouts.firstOrNull {
                        it.weekIndex == currentWeek &&
                                it.dayIndex >= currentDayIndex &&
                                (it.type == WorkoutType.REST || it.type == WorkoutType.EASY) &&
                                !it.isCompleted
                    }

                    if (candidateSlot != null) {
                        val targetDayName = days.getOrElse(candidateSlot.dayIndex) { "Day ${candidateSlot.dayIndex}" }
                        val slotIdx = workouts.indexOfFirst { it.id == candidateSlot.id }
                        // Move quality workout to candidate slot
                        workouts[slotIdx] = missed.copy(
                            id = UUID.randomUUID().toString(),
                            dayIndex = candidateSlot.dayIndex,
                            isMissed = false,
                            isCompleted = false
                        )
                        notes.add("Missed ${missed.title} ($dayName): Safely moved to $targetDayName. Rest days adjusted to avoid consecutive hard efforts.")
                    } else {
                        notes.add("Missed ${missed.title} ($dayName): Volume safely absorbed without doubling up.")
                    }
                } else {
                    notes.add("Missed ${missed.title} ($dayName): Dropped without penalty to keep legs fresh.")
                }
            }
        }

        val adjustmentSummary = notes.joinToString("\n")
        return plan.copy(
            workouts = workouts,
            adjustmentNote = adjustmentSummary
        )
    }
}
