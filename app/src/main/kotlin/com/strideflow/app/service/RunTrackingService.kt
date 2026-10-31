package com.strideflow.app.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.strideflow.app.MainActivity
import com.strideflow.app.data.LocalStorageManager
import com.strideflow.app.engine.AdaptiveCoach
import com.strideflow.app.engine.PaceSmoother
import com.strideflow.app.engine.VdotCalculator
import com.strideflow.app.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import kotlin.math.roundToInt

data class RunLiveState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isAutoPaused: Boolean = false,
    val durationSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val currentPaceSecPerKm: Double = 0.0,
    val averagePaceSecPerKm: Double = 0.0,
    val currentCadence: Int = 0,
    val currentHeartRate: Int = 0,
    val targetPaceSecPerKm: Double = 0.0,
    val targetHeartRateMin: Int? = null,
    val targetHeartRateMax: Int? = null,
    val workoutTitle: String? = null,
    val workoutId: String? = null,
    val workoutType: WorkoutType = WorkoutType.EASY,
    val splits: List<KmSplit> = emptyList(),
    val points: List<GpsPoint> = emptyList(),
    val completedRun: RecordedRun? = null
)

class RunTrackingService : Service(), LocationListener, SensorEventListener, TextToSpeech.OnInitListener {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var locationManager: LocationManager
    private var sensorManager: SensorManager? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    lateinit var bleManager: BleHeartRateManager
        private set

    private val _liveState = MutableStateFlow(RunLiveState())
    val liveState = _liveState.asStateFlow()

    private var timerJob: Job? = null
    private var lastLocation: Location? = null
    private var currentSplitStartTime = 0L
    private var currentSplitStartDistance = 0.0
    private var stepCountSinceStart = 0
    private var initialStepCount = -1

    private var audioVoiceCues = true
    private var isAutoPauseEnabled = true
    private var stationarySeconds = 0
    private var lastHrPromptTime = 0L

    private var activeWorkout: Workout? = null
    private var activePlan: TrainingPlan? = null
    private val paceSmoother = PaceSmoother()

    inner class LocalBinder : Binder() {
        fun getService(): RunTrackingService = this@RunTrackingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        tts = TextToSpeech(this, this)
        bleManager = BleHeartRateManager(this)
        createNotificationChannel()

        // Collect Heart Rate from BLE Manager
        serviceScope.launch {
            bleManager.currentHeartRate.collect { hr ->
                if (hr > 0) {
                    _liveState.value = _liveState.value.copy(currentHeartRate = hr)
                    checkZone2Guidance(hr)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            isTtsReady = true
        }
    }

    fun startRun(workout: Workout?, plan: TrainingPlan?, profile: UserProfile) {
        activeWorkout = workout
        activePlan = plan
        audioVoiceCues = profile.audioCuesEnabled
        isAutoPauseEnabled = profile.isAutoPauseEnabled
        stationarySeconds = 0
        paceSmoother.reset()

        // Directly connect to the paired smartwatch data stream without scanning
        bleManager.autoConnectPairedSmartwatch(profile.pairedBleDeviceAddress)

        val targetHrMin = if (workout?.type == WorkoutType.FAT_BURN_ZONE2) profile.zone2MinBpm else workout?.targetHeartRateMin
        val targetHrMax = if (workout?.type == WorkoutType.FAT_BURN_ZONE2) profile.zone2MaxBpm else workout?.targetHeartRateMax

        _liveState.value = RunLiveState(
            isRunning = true,
            isPaused = false,
            isAutoPaused = false,
            targetPaceSecPerKm = workout?.targetPaceSecPerKm ?: 0.0,
            targetHeartRateMin = targetHrMin,
            targetHeartRateMax = targetHrMax,
            workoutTitle = workout?.title ?: "Free GPS Run",
            workoutId = workout?.id,
            workoutType = workout?.type ?: WorkoutType.EASY
        )

        startForeground(NOTIFICATION_ID, buildNotification("Run started..."))
        registerSensors()
        startTimer()

        val prompt = if (workout?.type == WorkoutType.FAT_BURN_ZONE2) {
            "Starting Zone 2 Fat Burn run. Target heart rate is $targetHrMin to $targetHrMax beats per minute."
        } else {
            "Starting run. Target pace is ${VdotCalculator.formatPace(workout?.targetPaceSecPerKm ?: 0.0)} per kilometer."
        }
        speak(prompt)
    }

    fun pauseRun() {
        if (!_liveState.value.isRunning || _liveState.value.isPaused) return
        _liveState.value = _liveState.value.copy(isPaused = true)
        timerJob?.cancel()
        paceSmoother.reset()
        speak("Run paused.")
        updateNotification()
    }

    fun resumeRun() {
        if (!_liveState.value.isRunning || !_liveState.value.isPaused) return
        _liveState.value = _liveState.value.copy(isPaused = false, isAutoPaused = false)
        stationarySeconds = 0
        paceSmoother.reset()
        startTimer()
        speak("Resuming run.")
        updateNotification()
    }

    fun stopAndSaveRun(): RecordedRun? {
        if (!_liveState.value.isRunning) return null

        timerJob?.cancel()
        unregisterSensors()
        bleManager.disconnect()

        val state = _liveState.value
        val storage = LocalStorageManager(this)

        val runId = UUID.randomUUID().toString()
        val validHrPoints = state.points.map { it.heartRateBpm }.filter { it > 30 }
        val avgHr = if (validHrPoints.isNotEmpty()) validHrPoints.average().roundToInt() else 0
        val maxHr = if (validHrPoints.isNotEmpty()) validHrPoints.maxOrNull() ?: 0 else 0

        val recordedRun = RecordedRun(
            id = runId,
            workoutId = state.workoutId,
            startTimeMillis = System.currentTimeMillis() - (state.durationSeconds * 1000),
            durationSeconds = state.durationSeconds,
            distanceMeters = state.distanceMeters,
            averagePaceSecPerKm = state.averagePaceSecPerKm,
            averageCadence = state.currentCadence,
            averageHeartRateBpm = avgHr,
            maxHeartRateBpm = maxHr,
            splits = state.splits,
            points = state.points,
            targetPaceSecPerKm = state.targetPaceSecPerKm,
            targetHeartRateMin = state.targetHeartRateMin,
            targetHeartRateMax = state.targetHeartRateMax
        )

        // Evaluate on-device adaptation
        val evaluation = AdaptiveCoach.evaluateRun(recordedRun, activeWorkout, activePlan)
        val finalRun = recordedRun.copy(
            complianceScorePercent = evaluation.complianceScore,
            adaptationProposal = evaluation.proposal
        )

        // Persist offline
        storage.saveRun(finalRun)

        _liveState.value = _liveState.value.copy(
            isRunning = false,
            isPaused = false,
            completedRun = finalRun
        )

        speak("Run complete. Distance ${String.format("%.2f", state.distanceMeters / 1000.0)} kilometers. Great effort!")

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        return finalRun
    }

    @SuppressLint("MissingPermission")
    private fun registerSensors() {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.5f,
                    this
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        sensorManager?.let { sm ->
            val stepCounter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (stepCounter != null) {
                sm.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_UI)
            }
            val hrSensor = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            if (hrSensor != null) {
                sm.registerListener(this, hrSensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    private fun unregisterSensors() {
        try {
            locationManager.removeUpdates(this)
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startTimer() {
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val current = _liveState.value

                // If currently auto-paused, do not increment duration
                if (current.isAutoPaused || current.isPaused) continue

                // Check auto-pause logic: runner hasn't moved for > 10 seconds
                if (isAutoPauseEnabled && current.isRunning) {
                    if (current.currentPaceSecPerKm <= 0 || current.currentPaceSecPerKm > 900.0) { // slower than 15 min/km = stopped
                        stationarySeconds++
                        if (stationarySeconds >= 10 && !current.isAutoPaused) {
                            _liveState.value = current.copy(isAutoPaused = true)
                            speak("Auto-paused.")
                            updateNotification()
                            continue
                        }
                    } else {
                        stationarySeconds = 0
                    }
                }

                val newDuration = current.durationSeconds + 1
                val avgPace = if (current.distanceMeters > 50) {
                    (newDuration.toDouble() / (current.distanceMeters / 1000.0))
                } else 0.0

                val smoothedPace = paceSmoother.checkInactivity(System.currentTimeMillis())

                _liveState.value = current.copy(
                    durationSeconds = newDuration,
                    averagePaceSecPerKm = avgPace,
                    currentPaceSecPerKm = smoothedPace
                )
                if (newDuration % 5 == 0L) {
                    updateNotification()
                }
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!_liveState.value.isRunning || _liveState.value.isPaused) return
        if (location.accuracy > 20.0f) return

        val last = lastLocation
        var addedDist = 0.0
        val instantSpeed = location.speed.toDouble()

        if (last != null) {
            val dist = last.distanceTo(location).toDouble()
            if (dist < 15.0 * ((location.time - last.time) / 1000.0).coerceAtLeast(1.0)) {
                addedDist = dist
            }
        }
        lastLocation = location

        val current = _liveState.value

        // Check auto-resume
        if (current.isAutoPaused && instantSpeed >= 1.0) {
            stationarySeconds = 0
            paceSmoother.reset()
            _liveState.value = current.copy(isAutoPaused = false)
            speak("Resuming run.")
        }

        if (current.isAutoPaused) return

        val newDist = current.distanceMeters + addedDist
        val smoothedPace = paceSmoother.update(
            timeMillis = System.currentTimeMillis(),
            cumulativeDistance = newDist,
            rawSpeedMps = instantSpeed,
            accuracyMeters = location.accuracy
        )

        val newPoint = GpsPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            timeMillis = location.time,
            speedMps = location.speed,
            cadence = current.currentCadence,
            heartRateBpm = current.currentHeartRate
        )

        // 1 km split check
        val updatedSplits = current.splits.toMutableList()
        val splitThreshold = (updatedSplits.size + 1) * 1000.0
        if (newDist >= splitThreshold) {
            val splitDuration = current.durationSeconds - currentSplitStartTime
            val splitDist = newDist - currentSplitStartDistance
            val splitPace = if (splitDist > 0) (splitDuration.toDouble() / (splitDist / 1000.0)) else 0.0

            val splitNumber = updatedSplits.size + 1
            updatedSplits.add(
                KmSplit(
                    splitNumber = splitNumber,
                    durationSeconds = splitDuration,
                    averagePaceSecPerKm = splitPace,
                    distanceMeters = splitDist,
                    averageHeartRateBpm = current.currentHeartRate
                )
            )

            currentSplitStartTime = current.durationSeconds
            currentSplitStartDistance = newDist

            speak("Kilometer $splitNumber completed. Pace ${VdotCalculator.formatPace(splitPace)} per kilometer.")
        }

        _liveState.value = current.copy(
            distanceMeters = newDist,
            currentPaceSecPerKm = smoothedPace,
            points = current.points + newPoint,
            splits = updatedSplits
        )
    }

    private fun checkZone2Guidance(hr: Int) {
        val state = _liveState.value
        if (!state.isRunning || state.isPaused || state.isAutoPaused) return
        val min = state.targetHeartRateMin ?: return
        val max = state.targetHeartRateMax ?: return

        val now = System.currentTimeMillis()
        if (now - lastHrPromptTime < 30_000L) return // Prompt at most once per 30 seconds

        if (hr > max + 3) {
            lastHrPromptTime = now
            speak("Heart rate $hr beats per minute. Above fat burn zone. Slow down slightly.")
        } else if (hr < min - 5 && state.durationSeconds > 60) {
            lastHrPromptTime = now
            speak("Heart rate $hr beats per minute. Below Zone 2. You can increase intensity.")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toInt()
            if (initialStepCount == -1) {
                initialStepCount = totalSteps
            }
            stepCountSinceStart = totalSteps - initialStepCount
            val durationMin = _liveState.value.durationSeconds / 60.0
            val cadence = if (durationMin > 0.2) (stepCountSinceStart / durationMin).roundToInt() else 0
            _liveState.value = _liveState.value.copy(currentCadence = cadence)
        } else if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            val hr = event.values.firstOrNull()?.roundToInt() ?: 0
            if (hr in 30..250) {
                _liveState.value = _liveState.value.copy(currentHeartRate = hr)
                checkZone2Guidance(hr)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun speak(text: String) {
        if (!audioVoiceCues || !isTtsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "run_tts_${System.currentTimeMillis()}")
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StrideFlow Running")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun updateNotification() {
        val state = _liveState.value
        val distKm = String.format("%.2f", state.distanceMeters / 1000.0)
        val pace = VdotCalculator.formatPace(state.averagePaceSecPerKm)
        val time = VdotCalculator.formatDuration(state.durationSeconds)
        val hr = if (state.currentHeartRate > 0) " | ${state.currentHeartRate} bpm" else ""
        val status = if (state.isAutoPaused) " [AUTO-PAUSED]" else if (state.isPaused) " [PAUSED]" else ""
        val text = "$distKm km | $pace/km | $time$hr$status"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "StrideFlow Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground notifications for running GPS tracking"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        timerJob?.cancel()
        serviceScope.cancel()
        unregisterSensors()
        bleManager.disconnect()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "strideflow_tracking_channel"
        const val NOTIFICATION_ID = 4201
    }
}
