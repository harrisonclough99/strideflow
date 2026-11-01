package com.strideflow.app

import com.strideflow.app.engine.PaceSmoother
import com.strideflow.app.engine.VdotCalculator
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PaceSmootherTest {

    @Test
    fun testPaceSmootherFiltersArmSwingJitter() {
        val smoother = PaceSmoother()
        val baseSpeed = 3.333 // 5:00 min/km (~300 sec/km)
        var cumulativeDist = 0.0
        val startTime = 100000L

        // Arm swing causes instantaneous speed to oscillate by ±1.2 m/s every second
        val noisePattern = listOf(-1.1, 1.2, -0.9, 1.1, -1.0, 1.0, -1.2, 1.3, -0.8, 1.0, -1.1, 1.2)
        var lastPace = 0.0

        for (i in 0 until 30) {
            val time = startTime + (i * 1000L)
            cumulativeDist += baseSpeed // Runner actually covers 3.33m per second steadily
            val noisyInstantSpeed = (baseSpeed + noisePattern[i % noisePattern.size]).coerceAtLeast(1.0)

            val smoothedPace = smoother.update(
                timeMillis = time,
                cumulativeDistance = cumulativeDist,
                rawSpeedMps = noisyInstantSpeed,
                accuracyMeters = 8.0f
            )

            if (i >= 15) {
                // Pace should be tightly centered around 300 s/km (5:00/km)
                assertTrue("Expected pace near 300 s/km (5:00), got $smoothedPace", smoothedPace in 285.0..315.0)
                // With deadband hysteresis and Doppler trimming, second-to-second jitter should be <= 3.0 seconds
                val secondToSecondDelta = abs(smoothedPace - lastPace)
                assertTrue("Second-to-second jitter should be <= 3.0 s, was $secondToSecondDelta", secondToSecondDelta <= 3.0)
            }
            lastPace = smoothedPace
        }
    }

    @Test
    fun testPaceSmootherRejectsInaccurateGpsPoints() {
        val smoother = PaceSmoother()
        smoother.update(1000L, 10.0, 3.33, 5.0f)
        val initialPace = smoother.smoothedPaceSecPerKm

        // Inaccurate GPS point (> 20m accuracy) should be rejected
        val paceAfterBadFix = smoother.update(2000L, 20.0, 8.0, 35.0f)
        assertEquals("Smoothed pace should not change on inaccurate GPS point", initialPace, paceAfterBadFix, 0.001)
    }

    @Test
    fun testPaceSmootherStationaryDetection() {
        val smoother = PaceSmoother()
        // Running at 3.33 m/s
        smoother.update(1000L, 10.0, 3.33, 5.0f)
        smoother.update(2000L, 20.0, 3.33, 5.0f)
        assertTrue(smoother.smoothedPaceSecPerKm > 0.0)

        // Stop moving (speed < 0.8 m/s for 2 consecutive fixes)
        smoother.update(3000L, 20.2, 0.3, 5.0f)
        val paceAtStop = smoother.update(4000L, 20.3, 0.2, 5.0f)

        assertEquals("Pace should drop to 0.0 when stopped", 0.0, paceAtStop, 0.001)
        assertEquals("--:--", VdotCalculator.formatPace(paceAtStop))
    }

    @Test
    fun testPaceSmootherInactivityDecay() {
        val smoother = PaceSmoother()
        smoother.update(1000L, 10.0, 3.33, 5.0f)
        assertTrue(smoother.smoothedPaceSecPerKm > 0.0)

        // After 4.5 seconds with no GPS updates, inactivity check should decay pace to 0.0
        val decayedPace = smoother.checkInactivity(5600L)
        assertEquals("Pace should decay to 0.0 when inactive for > 4s", 0.0, decayedPace, 0.001)
    }

    @Test
    fun testFormatPaceDisplayBehavior() {
        assertEquals("5:00", VdotCalculator.formatPace(300.0))
        assertEquals("4:15", VdotCalculator.formatPace(255.0))
        assertEquals("6:30", VdotCalculator.formatPace(390.0))
        assertEquals("--:--", VdotCalculator.formatPace(0.0))
        assertEquals("--:--", VdotCalculator.formatPace(-10.0))
        assertEquals("--:--", VdotCalculator.formatPace(1250.0)) // > 20 min/km (walking/stopped)
    }

    @Test
    fun testGarmin5SecondPaceRounding() {
        // 301.2s (~5:01) -> rounds to 5:00
        assertEquals("5:00", VdotCalculator.formatPace(301.2, roundToNearest5Seconds = true))
        // 303.4s (~5:03) -> rounds to 5:05
        assertEquals("5:05", VdotCalculator.formatPace(303.4, roundToNearest5Seconds = true))
        // 258.0s (4:18) -> rounds to 4:20
        assertEquals("4:20", VdotCalculator.formatPace(258.0, roundToNearest5Seconds = true))
        // 257.0s (4:17) -> rounds to 4:15
        assertEquals("4:15", VdotCalculator.formatPace(257.0, roundToNearest5Seconds = true))
        // Stopped -> returns --:--
        assertEquals("--:--", VdotCalculator.formatPace(0.0, roundToNearest5Seconds = true))
    }
}
