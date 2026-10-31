package com.strideflow.app.engine

/**
 * Robust running GPS pace smoothing filter.
 *
 * Solves instantaneous GPS pace fluctuation caused by:
 * - 1 Hz GPS Doppler noise and multipath error
 * - Runner arm swing oscillations (which fluctuate ±1.5 m/s every stride cycle)
 * - Sporadic fix jitter
 *
 * Implements industry-standard running watch filtering:
 * 1. Rolling window buffer (10s) measuring net displacement over time
 * 2. Exponential Moving Average (EMA) smoothing (alpha = 0.22)
 * 3. Outlier accuracy gating (< 20m)
 * 4. Clean stationary detection (< 0.8 m/s drops to 0.0, rendering "--:--" without division-by-zero spikes)
 */
class PaceSmoother(
    val windowDurationMs: Long = 10_000L,
    val minWindowDurationMs: Long = 3_000L,
    val minDistanceMeters: Double = 5.0,
    val emaAlpha: Double = 0.22,
    val stopSpeedThresholdMps: Double = 0.8 // < 0.8 m/s (~20:50/km) considered stationary/walking stop
) {
    data class WindowPoint(
        val timeMillis: Long,
        val cumulativeDistanceMeters: Double
    )

    private val window = ArrayDeque<WindowPoint>()
    var smoothedPaceSecPerKm: Double = 0.0
        private set
    private var lastValidFixTime: Long = 0L
    private var consecutiveStopCount: Int = 0

    /**
     * Resets filter state (e.g., at workout start, resume, or auto-resume).
     */
    fun reset() {
        window.clear()
        smoothedPaceSecPerKm = 0.0
        lastValidFixTime = 0L
        consecutiveStopCount = 0
    }

    /**
     * Updates the smoother with a new GPS location reading.
     *
     * @param timeMillis Fix timestamp in milliseconds
     * @param cumulativeDistance Cumulative distance run so far in meters
     * @param rawSpeedMps Instantaneous speed reported by GPS provider (m/s)
     * @param accuracyMeters GPS horizontal accuracy radius in meters
     * @return The smoothed pace in seconds per kilometer (0.0 if stationary)
     */
    fun update(
        timeMillis: Long,
        cumulativeDistance: Double,
        rawSpeedMps: Double,
        accuracyMeters: Float
    ): Double {
        // Reject low accuracy fixes (> 20m) from corrupting the pace filter
        if (accuracyMeters > 20.0f) {
            return smoothedPaceSecPerKm
        }

        lastValidFixTime = timeMillis

        // Stationary detection
        if (rawSpeedMps < stopSpeedThresholdMps) {
            consecutiveStopCount++
            if (consecutiveStopCount >= 2) {
                // Definitely stopped or walking very slowly
                window.clear()
                window.addLast(WindowPoint(timeMillis, cumulativeDistance))
                smoothedPaceSecPerKm = 0.0
                return 0.0
            }
        } else {
            consecutiveStopCount = 0
        }

        // Prune window entries older than windowDurationMs
        val cutoff = timeMillis - windowDurationMs
        while (window.isNotEmpty() && window.first().timeMillis < cutoff) {
            window.removeFirst()
        }

        window.addLast(WindowPoint(timeMillis, cumulativeDistance))

        // Calculate candidate speed
        val oldest = window.first()
        val deltaDist = cumulativeDistance - oldest.cumulativeDistanceMeters
        val deltaTimeSec = (timeMillis - oldest.timeMillis) / 1000.0

        var candidateSpeedMps = 0.0

        if (window.size >= 2 && deltaTimeSec >= (minWindowDurationMs / 1000.0) && deltaDist >= minDistanceMeters) {
            // Window displacement speed naturally cancels out arm swings and 1Hz Doppler noise
            candidateSpeedMps = deltaDist / deltaTimeSec
        } else if (rawSpeedMps >= stopSpeedThresholdMps && rawSpeedMps <= 12.0) {
            // Warm-up phase before window reaches minWindowDurationMs
            candidateSpeedMps = rawSpeedMps
        }

        if (candidateSpeedMps < stopSpeedThresholdMps || candidateSpeedMps > 12.0) {
            if (candidateSpeedMps < stopSpeedThresholdMps) {
                smoothedPaceSecPerKm = 0.0
            }
            return smoothedPaceSecPerKm
        }

        val instantPace = 1000.0 / candidateSpeedMps

        // Apply Exponential Moving Average (EMA)
        smoothedPaceSecPerKm = if (smoothedPaceSecPerKm <= 0.0) {
            instantPace
        } else {
            (emaAlpha * instantPace) + ((1.0 - emaAlpha) * smoothedPaceSecPerKm)
        }

        return smoothedPaceSecPerKm
    }

    /**
     * Checks for inactivity (e.g. called from 1-second service timer).
     * If no valid movement fix has arrived within 4 seconds, decays pace to 0.0.
     */
    fun checkInactivity(currentTimeMillis: Long): Double {
        if (smoothedPaceSecPerKm > 0.0 && lastValidFixTime > 0L && currentTimeMillis - lastValidFixTime > 4000L) {
            smoothedPaceSecPerKm = 0.0
        }
        return smoothedPaceSecPerKm
    }
}
