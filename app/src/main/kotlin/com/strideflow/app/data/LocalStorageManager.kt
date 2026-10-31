package com.strideflow.app.data

import android.content.Context
import com.strideflow.app.engine.AdaptiveCoach
import com.strideflow.app.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

class LocalStorageManager(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val profileFile: File get() = File(context.filesDir, "strideflow_profile.json")
    private val planFile: File get() = File(context.filesDir, "strideflow_current_plan.json")
    private val allPlansFile: File get() = File(context.filesDir, "strideflow_all_plans.json")
    private val runsFile: File get() = File(context.filesDir, "strideflow_runs.json")

    @Synchronized
    fun loadProfile(): UserProfile {
        return try {
            if (profileFile.exists()) {
                json.decodeFromString<UserProfile>(profileFile.readText())
            } else {
                val defaultProfile = UserProfile()
                saveProfile(defaultProfile)
                defaultProfile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            UserProfile()
        }
    }

    @Synchronized
    fun saveProfile(profile: UserProfile) {
        atomicWrite(profileFile, json.encodeToString(profile))
    }

    @Synchronized
    fun loadAllPlans(): List<TrainingPlan> {
        return try {
            if (allPlansFile.exists()) {
                json.decodeFromString<List<TrainingPlan>>(allPlansFile.readText())
            } else {
                val current = loadActivePlan()
                if (current != null) listOf(current) else emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    fun loadActivePlan(): TrainingPlan? {
        return try {
            if (planFile.exists()) {
                val plan = json.decodeFromString<TrainingPlan>(planFile.readText())

                // Calculate current day index (0 = Monday .. 6 = Sunday)
                val cal = Calendar.getInstance()
                val dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

                // Automatically evaluate and adapt for missed workouts
                val autoAdjusted = AdaptiveCoach.autoAdjustForMissedWorkouts(plan, dayOfWeek)
                if (autoAdjusted != plan) {
                    savePlan(autoAdjusted)
                }
                autoAdjusted
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Synchronized
    fun savePlan(plan: TrainingPlan) {
        atomicWrite(planFile, json.encodeToString(plan))

        // Update all plans list
        val plans = loadAllPlans().toMutableList()
        val index = plans.indexOfFirst { it.id == plan.id }
        if (index >= 0) {
            plans[index] = plan
        } else {
            plans.add(0, plan)
        }
        atomicWrite(allPlansFile, json.encodeToString(plans))
    }

    @Synchronized
    fun switchActivePlan(planId: String): TrainingPlan? {
        val plans = loadAllPlans()
        val target = plans.firstOrNull { it.id == planId } ?: return null

        val updatedTarget = target.copy(isActive = true)
        val updatedPlans = plans.map {
            if (it.id == planId) updatedTarget else it.copy(isActive = false)
        }
        atomicWrite(allPlansFile, json.encodeToString(updatedPlans))
        atomicWrite(planFile, json.encodeToString(updatedTarget))

        val profile = loadProfile()
        saveProfile(profile.copy(activePlanId = planId, vdot = updatedTarget.currentVdot))

        return updatedTarget
    }

    @Synchronized
    fun deletePlan(planId: String): Boolean {
        val plans = loadAllPlans().toMutableList()
        val toDelete = plans.firstOrNull { it.id == planId } ?: return false
        plans.removeAll { it.id == planId }
        atomicWrite(allPlansFile, json.encodeToString(plans))

        val current = loadActivePlan()
        if (current?.id == planId) {
            planFile.delete()
            if (plans.isNotEmpty()) {
                switchActivePlan(plans.first().id)
            }
        }
        return true
    }

    @Synchronized
    fun loadRuns(): List<RecordedRun> {
        return try {
            if (runsFile.exists()) {
                json.decodeFromString<List<RecordedRun>>(runsFile.readText())
            } else emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    fun saveRun(run: RecordedRun) {
        val existing = loadRuns().toMutableList()
        val index = existing.indexOfFirst { it.id == run.id }
        if (index >= 0) {
            existing[index] = run
        } else {
            existing.add(0, run)
        }
        atomicWrite(runsFile, json.encodeToString(existing))

        // If run is tied to a workout, mark that workout completed in the active plan
        val plan = loadActivePlan()
        if (plan != null && run.workoutId != null) {
            val updatedWorkouts = plan.workouts.map { workout ->
                if (workout.id == run.workoutId) {
                    workout.copy(isCompleted = true, actualRunId = run.id)
                } else workout
            }
            savePlan(plan.copy(workouts = updatedWorkouts))
        }
    }

    @Synchronized
    fun deleteRun(runId: String): Boolean {
        val existing = loadRuns().toMutableList()
        val runToDelete = existing.firstOrNull { it.id == runId } ?: return false
        existing.removeAll { it.id == runId }
        atomicWrite(runsFile, json.encodeToString(existing))

        if (runToDelete.workoutId != null) {
            val plan = loadActivePlan()
            if (plan != null) {
                val updatedWorkouts = plan.workouts.map { workout ->
                    if (workout.id == runToDelete.workoutId) {
                        workout.copy(isCompleted = false, actualRunId = null)
                    } else workout
                }
                savePlan(plan.copy(workouts = updatedWorkouts))
            }
        }
        return true
    }

    @Synchronized
    fun rescheduleWorkout(workoutId: String, newDayIndex: Int, newDateEpochMillis: Long?): Boolean {
        val plan = loadActivePlan() ?: return false
        val updatedWorkouts = plan.workouts.map { workout ->
            if (workout.id == workoutId) {
                workout.copy(
                    dayIndex = newDayIndex,
                    scheduledDateEpochMillis = newDateEpochMillis
                )
            } else workout
        }
        savePlan(plan.copy(workouts = updatedWorkouts))
        return true
    }

    private fun atomicWrite(targetFile: File, content: String) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            tempFile.writeText(content)
            if (tempFile.renameTo(targetFile)) {
                // renamed successfully
            } else {
                targetFile.delete()
                tempFile.renameTo(targetFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }
}
