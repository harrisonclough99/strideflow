package com.strideflow.app.engine

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * High precision on-device implementation of Jack Daniels' VDOT physiological running formula.
 * Computes VO2max, VDOT scores from race performances, and exact training paces for all training zones.
 */
object VdotCalculator {

    data class TrainingPaces(
        val easyPaceSecPerKm: Double,
        val marathonPaceSecPerKm: Double,
        val thresholdPaceSecPerKm: Double,
        val intervalPaceSecPerKm: Double,
        val repetitionPaceSecPerKm: Double
    )

    /**
     * Calculates Jack Daniels' VDOT from a known race/time-trial distance (meters) and duration (seconds).
     */
    fun calculateVdot(distanceMeters: Double, durationSeconds: Long): Double {
        if (durationSeconds <= 0 || distanceMeters <= 0.0) return 30.0

        val timeMinutes = durationSeconds / 60.0
        val velocityMpm = distanceMeters / timeMinutes // meters per minute

        // Oxygen cost equation
        val vo2 = -4.60 + (0.182258 * velocityMpm) + (0.000104 * velocityMpm.pow(2.0))

        // % of VO2max sustainable for duration t
        val percentVo2max = 0.8 + (0.1894393 * exp(-0.012778 * timeMinutes)) +
                (0.2989558 * exp(-0.1932605 * timeMinutes))

        if (percentVo2max <= 0.0) return 30.0
        val vdot = vo2 / percentVo2max
        return (vdot * 10.0).roundToInt() / 10.0
    }

    /**
     * Solves quadratic equation for velocity (m/min) given target VO2 cost (ml/kg/min).
     */
    private fun velocityFromVo2(targetVo2: Double): Double {
        val a = 0.000104
        val b = 0.182258
        val c = -(4.60 + targetVo2)

        val discriminant = b.pow(2.0) - (4.0 * a * c)
        if (discriminant < 0) return 150.0 // fallback ~6:40/km
        return (-b + sqrt(discriminant)) / (2.0 * a)
    }

    /**
     * Converts velocity in meters per minute to pace in seconds per kilometer.
     */
    private fun velocityToPaceSecPerKm(velocityMpm: Double): Double {
        if (velocityMpm <= 0.0) return 360.0
        return (1000.0 / velocityMpm) * 60.0
    }

    /**
     * Predicts race completion time in seconds for a given VDOT and distance based on Daniels %VO2max models.
     */
    fun predictRaceTimeSeconds(vdot: Double, distanceMeters: Double): Long {
        val safeVdot = vdot.coerceIn(20.0, 85.0)
        val fraction = when {
            distanceMeters <= 5000.0 -> 0.95
            distanceMeters <= 10000.0 -> 0.90
            distanceMeters <= 21097.5 -> 0.84
            else -> 0.79
        }
        val targetVo2 = safeVdot * fraction
        val velocityMpm = velocityFromVo2(targetVo2)
        if (velocityMpm <= 0.0) return 0L
        val timeMinutes = distanceMeters / velocityMpm
        return (timeMinutes * 60.0).roundToInt().toLong()
    }

    /**
     * Computes all five canonical Daniels training paces for a given VDOT.
     */
    fun getPacesForVdot(vdot: Double): TrainingPaces {
        val safeVdot = vdot.coerceIn(20.0, 85.0)

        // Training zone percentages of VO2max
        val easyVo2 = safeVdot * 0.70
        val marathonVo2 = safeVdot * 0.82
        val thresholdVo2 = safeVdot * 0.88
        val intervalVo2 = safeVdot * 0.98
        val repetitionVo2 = safeVdot * 1.07

        return TrainingPaces(
            easyPaceSecPerKm = velocityToPaceSecPerKm(velocityFromVo2(easyVo2)),
            marathonPaceSecPerKm = velocityToPaceSecPerKm(velocityFromVo2(marathonVo2)),
            thresholdPaceSecPerKm = velocityToPaceSecPerKm(velocityFromVo2(thresholdVo2)),
            intervalPaceSecPerKm = velocityToPaceSecPerKm(velocityFromVo2(intervalVo2)),
            repetitionPaceSecPerKm = velocityToPaceSecPerKm(velocityFromVo2(repetitionVo2))
        )
    }

    /**
     * Format pace (seconds per km or mile) into string format "M:SS"
     * @param paceSec Pace in seconds per kilometer
     * @param roundToNearest5Seconds When true, rounds pace to the nearest 5s (Garmin running standard)
     */
    fun formatPace(paceSec: Double, roundToNearest5Seconds: Boolean = false): String {
        if (paceSec.isNaN() || paceSec.isInfinite() || paceSec <= 0 || paceSec > 1200.0) return "--:--"
        val totalSec = if (roundToNearest5Seconds) {
            ((paceSec / 5.0).roundToInt() * 5)
        } else {
            paceSec.roundToInt()
        }
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return "%d:%02d".format(minutes, seconds)
    }

    /**
     * Format duration (seconds) into string format "HH:MM:SS" or "MM:SS"
     */
    fun formatDuration(durationSeconds: Long): String {
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
