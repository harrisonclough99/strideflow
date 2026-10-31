package com.strideflow.app.model

import kotlinx.serialization.Serializable

@Serializable
enum class UnitSystem(val distanceLabel: String, val paceLabel: String, val conversionFactorKmToUnit: Double) {
    METRIC("km", "min/km", 1.0),
    IMPERIAL("mi", "min/mi", 0.621371)
}

@Serializable
enum class DietaryPreference(val displayName: String) {
    OMNIVORE("Omnivore"),
    VEGETARIAN("Vegetarian"),
    VEGAN("Plant-Based / Vegan"),
    GLUTEN_FREE("Gluten-Free"),
    LOW_FODMAP("Low-FODMAP / Sensitive Stomach")
}

@Serializable
enum class GoalType(val displayName: String, val defaultDistanceMeters: Double) {
    FIVE_K("5K Race", 5000.0),
    TEN_K("10K Race", 10000.0),
    HALF_MARATHON("Half Marathon (21.1 km)", 21097.5),
    MARATHON("Marathon (42.2 km)", 42195.0),
    BASE_BUILDING("Aerobic Base Building", 8000.0)
}

@Serializable
enum class WorkoutType(val displayName: String, val badgeColorHex: String) {
    EASY("Easy Run", "#4CAF50"),
    LONG_RUN("Long Run", "#2196F3"),
    THRESHOLD_TEMPO("Threshold / Tempo", "#FF9800"),
    INTERVAL_VO2MAX("VO2max Intervals", "#E91E63"),
    SPEED_REPETITION("Speed Repetitions", "#9C27B0"),
    FAT_BURN_ZONE2("Zone 2 Fat Burn", "#FF9100"),
    BENCHMARK_TEST("Fitness Benchmark", "#00E5FF"),
    REST("Rest / Recovery", "#607D8B")
}

@Serializable
data class WorkoutInterval(
    val name: String,
    val distanceMeters: Double? = null,
    val durationSeconds: Long? = null,
    val targetPaceSecPerKm: Double,
    val isRecovery: Boolean = false
)

@Serializable
data class Workout(
    val id: String,
    val dayIndex: Int, // 0 to 6 (Monday to Sunday)
    val weekIndex: Int, // 1 to totalWeeks
    val title: String,
    val type: WorkoutType,
    val targetDistanceMeters: Double,
    val targetPaceSecPerKm: Double,
    val intervals: List<WorkoutInterval> = emptyList(),
    val description: String,
    val isCompleted: Boolean = false,
    val isMissed: Boolean = false,
    val actualRunId: String? = null,
    val scheduledDateEpochMillis: Long? = null,
    val targetHeartRateMin: Int? = null,
    val targetHeartRateMax: Int? = null
)

@Serializable
data class RunningGoal(
    val goalType: GoalType,
    val targetTimeSeconds: Long, // e.g. 1500 for 25:00 5k
    val targetDateEpochMillis: Long,
    val daysPerWeek: Int = 4
)

@Serializable
data class UserProfile(
    val runnerName: String = "Runner",
    val vdot: Double = 42.0, // Jack Daniels VDOT score
    val weightKg: Double = 70.0,
    val age: Int = 30,
    val maxHeartRateBpm: Int = 190,
    val restingHeartRateBpm: Int = 60,
    val weeklyMileageBaselineKm: Double = 25.0,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val dietaryPreference: DietaryPreference = DietaryPreference.OMNIVORE,
    val audioCuesEnabled: Boolean = true,
    val cadenceTargetBpm: Int = 175,
    val isAutoPauseEnabled: Boolean = true,
    val autoPauseThresholdSeconds: Int = 10,
    val countdownDurationSeconds: Int = 3,
    val isNutritionNotificationsEnabled: Boolean = true,
    val isRunReminderNotificationsEnabled: Boolean = true,
    val scheduledRunHour: Int = 7,
    val scheduledRunMinute: Int = 0,
    val pairedBleDeviceAddress: String? = null,
    val pairedBleDeviceName: String? = null,
    val activePlanId: String? = null
) {
    // Zone 2 HR calculation using Karvonen formula
    val zone2MinBpm: Int get() = ((maxHeartRateBpm - restingHeartRateBpm) * 0.60 + restingHeartRateBpm).toInt()
    val zone2MaxBpm: Int get() = ((maxHeartRateBpm - restingHeartRateBpm) * 0.70 + restingHeartRateBpm).toInt()
}

@Serializable
data class TrainingPlan(
    val id: String,
    val title: String,
    val goal: RunningGoal,
    val startingVdot: Double,
    val currentVdot: Double,
    val totalWeeks: Int,
    val currentWeek: Int = 1,
    val workouts: List<Workout> = emptyList(),
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val adjustmentNote: String? = null
)

@Serializable
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val timeMillis: Long = System.currentTimeMillis(),
    val speedMps: Float = 0f,
    val cadence: Int = 0,
    val heartRateBpm: Int = 0
)

@Serializable
data class KmSplit(
    val splitNumber: Int,
    val durationSeconds: Long,
    val averagePaceSecPerKm: Double,
    val distanceMeters: Double,
    val averageHeartRateBpm: Int = 0
)

@Serializable
data class RecordedRun(
    val id: String,
    val workoutId: String? = null,
    val startTimeMillis: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val averagePaceSecPerKm: Double,
    val averageCadence: Int = 0,
    val averageHeartRateBpm: Int = 0,
    val maxHeartRateBpm: Int = 0,
    val splits: List<KmSplit> = emptyList(),
    val points: List<GpsPoint> = emptyList(),
    val targetPaceSecPerKm: Double? = null,
    val targetHeartRateMin: Int? = null,
    val targetHeartRateMax: Int? = null,
    val complianceScorePercent: Int = 100,
    val adaptationProposal: AdaptationProposal? = null
)

@Serializable
data class AdaptationProposal(
    val id: String,
    val runId: String,
    val dateEpochMillis: Long = System.currentTimeMillis(),
    val title: String,
    val reason: String,
    val previousVdot: Double,
    val proposedVdot: Double,
    val pacingDeltaSecPerKm: Double,
    val explanationText: String,
    val isAccepted: Boolean? = null
)

@Serializable
data class NutritionAdvice(
    val preRunMealSummary: String,
    val preRunMealWindow: String,
    val preRunPrimerSummary: String,
    val preRunPrimerWindow: String,
    val intraRunFueling: String?,
    val postRunRecoveryCarbProtein: String,
    val postRunHydrationElectrolytes: String,
    val recipeIdeas: List<String>
)
