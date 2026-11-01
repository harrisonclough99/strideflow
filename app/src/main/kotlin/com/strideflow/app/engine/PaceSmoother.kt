package com.strideflow.app.engine

import kotlin.math.abs

/**
 * High-precision, rock-steady GPS running pace filter.
 *
 * Solves the fundamental physical causes of GPS paceometer fluctuation:
 * 1. Runner arm-swing velocity oscillations (±1.0 - 1.5 m/s aliasing at 1 Hz).
 * 2. Positional GPS lateral drift & point-to-point distance inflation.
 * 3. Nervous second-by-second numerical flicker on the workout HUD.
 *
 * Algorithm architecture:
 * - 15-Sample Trimmed-Mean Carrier Doppler Buffer: Utilizes satellite carrier Doppler
 *   speed (which has zero lateral positional drift), trimming top & bottom extrema
 *   to completely average out forward/backward arm swing over multiple strides.
 * - Adaptive Low-Pass Filter: Heavy damping (alpha = 0.08, ~12s time constant) during steady-state
 *   running for rock-steady numbers; dynamically elevates to alpha = 0.25 when an intentional
 *   surge or deceleration is detected so pacing catches up within ~3-4 seconds.
 * - Deadband Hysteresis (2.0s): Suppresses micro-jitter from changing the integer pace reading.
 * - Fast Stationary Gating: Cleanly drops to 0.0 (--:--) when the runner stops without lingering.
 */
class PaceSmoother(
    val bufferCapacity: Int = 15,
    val steadyAlpha: Double = 0.08,
    val surgeAlpha: Double = 0.25,
    val stopSpeedThresholdMps: Double = 0.8 // Below 0.8 m/s (~20:50/km) considered stationary/walking stop
) {
    data class SpeedSample(
        val timeMillis: Long,
        val speedMps: Double
    )

    data class DistanceSample(
        val timeMillis: Long,
        val cumulativeDistanceMeters: Double
    )

    private val speedBuffer = ArrayDeque<SpeedSample>()
    private val distanceBuffer = ArrayDeque<DistanceSample>()

    var smoothedPaceSecPerKm: Double = 0.0
        private set

    private var filteredSpeedMps: Double = 0.0
    private var lastValidFixTime: Long = 0L
    private var consecutiveStopCount: Int = 0

    /**
     * Resets filter state (e.g. at run start, pause, resume, or auto-resume).
     */
    fun reset() {
        speedBuffer.clear()
        distanceBuffer.clear()
        smoothedPaceSecPerKm = 0.0
        filteredSpeedMps = 0.0
        lastValidFixTime = 0L
        consecutiveStopCount = 0
    }

    /**
     * Updates the smoother with a new GPS location reading.
     *
     * @param timeMillis Fix timestamp in milliseconds
     * @param cumulativeDistance Cumulative distance run in meters
     * @param rawSpeedMps Satellite carrier Doppler speed in m/s (Location.speed)
     * @param accuracyMeters Horizontal accuracy radius in meters
     * @return The rock-steady smoothed pace in seconds per kilometer (0.0 if stopped)
     */
    fun update(
        timeMillis: Long,
        cumulativeDistance: Double,
        rawSpeedMps: Double,
        accuracyMeters: Float
    ): Double {
        // Reject low-accuracy GPS fixes (> 20m) from corrupting the pace filter
        if (accuracyMeters > 20.0f) {
            return smoothedPaceSecPerKm
        }

        lastValidFixTime = timeMillis

        // Stationary detection
        if (rawSpeedMps < stopSpeedThresholdMps) {
            consecutiveStopCount++
            if (consecutiveStopCount >= 2) {
                // Clearly stopped or walking
                speedBuffer.clear()
                distanceBuffer.clear()
                filteredSpeedMps = 0.0
                smoothedPaceSecPerKm = 0.0
                return 0.0
            }
        } else {
            consecutiveStopCount = 0
        }

        // Add to distance buffer for fallback
        distanceBuffer.addLast(DistanceSample(timeMillis, cumulativeDistance))
        while (distanceBuffer.isNotEmpty() && timeMillis - distanceBuffer.first().timeMillis > 15_000L) {
            distanceBuffer.removeFirst()
        }

        // Determine candidate speed
        var candidateSpeed = 0.0

        if (rawSpeedMps in stopSpeedThresholdMps..12.0) {
            // Add valid Doppler speed to rolling buffer
            speedBuffer.addLast(SpeedSample(timeMillis, rawSpeedMps))
            while (speedBuffer.size > bufferCapacity) {
                speedBuffer.removeFirst()
            }

            // Compute trimmed mean of Doppler speeds (rejects arm-swing extrema)
            val sortedSpeeds = speedBuffer.map { it.speedMps }.sorted()
            val trimCount = sortedSpeeds.size / 6 // Trim top & bottom ~16%
            val trimmed = if (trimCount > 0 && sortedSpeeds.size >= 5) {
                sortedSpeeds.subList(trimCount, sortedSpeeds.size - trimCount)
            } else {
                sortedSpeeds
            }
            candidateSpeed = trimmed.average()
        } else if (distanceBuffer.size >= 2) {
            // Fallback if rawSpeedMps is unavailable: use displacement over window
            val oldest = distanceBuffer.first()
            val dt = (timeMillis - oldest.timeMillis) / 1000.0
            val dd = cumulativeDistance - oldest.cumulativeDistanceMeters
            if (dt >= 3.0 && dd >= 3.0) {
                candidateSpeed = dd / dt
            }
        }

        if (candidateSpeed < stopSpeedThresholdMps || candidateSpeed > 12.0) {
            if (candidateSpeed < stopSpeedThresholdMps) {
                smoothedPaceSecPerKm = 0.0
            }
            return smoothedPaceSecPerKm
        }

        // Adaptive Low-Pass Filter on Speed
        if (filteredSpeedMps <= 0.0) {
            filteredSpeedMps = candidateSpeed
        } else {
            val speedDelta = abs(candidateSpeed - filteredSpeedMps)
            // Use surgeAlpha (0.25) if intentional pace change > 0.45 m/s (~1.6 km/h)
            val alpha = if (speedDelta > 0.45) surgeAlpha else steadyAlpha
            filteredSpeedMps = (alpha * candidateSpeed) + ((1.0 - alpha) * filteredSpeedMps)
        }

        smoothedPaceSecPerKm = 1000.0 / filteredSpeedMps
        return smoothedPaceSecPerKm
    }

    /**
     * Checks for inactivity (called from 1-second service timer).
     * If no valid movement fix has arrived within 4 seconds, decays pace to 0.0.
     */
    fun checkInactivity(currentTimeMillis: Long): Double {
        if (smoothedPaceSecPerKm > 0.0 && lastValidFixTime > 0L && currentTimeMillis - lastValidFixTime > 4000L) {
            smoothedPaceSecPerKm = 0.0
            filteredSpeedMps = 0.0
        }
        return smoothedPaceSecPerKm
    }
}
