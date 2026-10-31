package com.strideflow.app.ui.run

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.strideflow.app.data.LocalStorageManager
import com.strideflow.app.engine.VdotCalculator
import com.strideflow.app.model.WorkoutType
import com.strideflow.app.service.RunLiveState
import com.strideflow.app.service.RunTrackingService
import com.strideflow.app.ui.map.RouteMapView
import com.strideflow.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun ActiveRunScreen(
    workoutId: String?,
    onRunFinished: (String) -> Unit,
    onCancelRun: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember { LocalStorageManager(context) }
    val profile = remember { storage.loadProfile() }
    val plan = remember { storage.loadActivePlan() }
    val workout = remember(workoutId) { plan?.workouts?.firstOrNull { it.id == workoutId } }

    var service by remember { mutableStateOf<RunTrackingService?>(null) }
    var liveState by remember { mutableStateOf(RunLiveState()) }

    // Countdown state before start
    var countdownRemaining by remember { mutableIntStateOf(profile.countdownDurationSeconds.coerceAtLeast(3)) }
    var isCountdownActive by remember { mutableStateOf(true) }

    // HUD vs Map View Toggle
    var showMapView by remember { mutableStateOf(false) }

    // Screen-awake lock during run
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val b = binder as? RunTrackingService.LocalBinder
                service = b?.getService()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
    }

    // Permission launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineLocationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineLocationGranted) {
            val intent = Intent(context, RunTrackingService::class.java)
            ContextCompat.startForegroundService(context, intent)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    // Countdown Timer Loop
    LaunchedEffect(isCountdownActive) {
        if (isCountdownActive) {
            while (countdownRemaining > 0) {
                delay(1000L)
                countdownRemaining--
            }
            isCountdownActive = false
        }
    }

    // Start tracking once countdown finishes
    LaunchedEffect(isCountdownActive, service) {
        if (!isCountdownActive) {
            val s = service ?: return@LaunchedEffect
            if (!s.liveState.value.isRunning) {
                s.startRun(workout, plan, profile)
            }
            s.liveState.collect { state ->
                liveState = state
                if (state.completedRun != null) {
                    onRunFinished(state.completedRun.id)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                // already unbound
            }
        }
    }

    // Countdown Overlay Screen
    if (isCountdownActive) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "GET READY",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = if (countdownRemaining > 0) "$countdownRemaining" else "GO!",
                    fontSize = 110.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = if (countdownRemaining > 0) NeonGreen else BrightCyan
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = workout?.title ?: "Free GPS Run",
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(40.dp))
                OutlinedButton(
                    onClick = onCancelRun,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, VividRed)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = VividRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CANCEL RUN", color = VividRed, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // Main Running HUD / Map UI
    val isFatBurn = workout?.type == WorkoutType.FAT_BURN_ZONE2
    val targetHrMin = liveState.targetHeartRateMin ?: profile.zone2MinBpm
    val targetHrMax = liveState.targetHeartRateMax ?: profile.zone2MaxBpm
    val hr = liveState.currentHeartRate

    val hrStatusColor = when {
        hr <= 0 -> TextMuted
        hr in targetHrMin..targetHrMax -> NeonGreen
        hr > targetHrMax -> VividRed
        else -> ElectricAmber
    }

    val hrStatusText = when {
        hr <= 0 -> "SEARCHING WATCH..."
        hr in targetHrMin..targetHrMax -> "IN ZONE 2 (FAT BURN)"
        hr > targetHrMax -> "ABOVE ZONE 2 (SLOW DOWN)"
        else -> "BELOW ZONE 2"
    }

    val targetPace = workout?.targetPaceSecPerKm ?: 0.0
    val currentPace = liveState.currentPaceSecPerKm
    val paceDiff = currentPace - targetPace
    val paceStatusColor = when {
        targetPace <= 0 -> BrightCyan
        currentPace <= 0 -> TextMuted
        abs(paceDiff) <= 12 -> NeonGreen
        paceDiff < -12 -> ElectricAmber
        else -> VividRed
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar: Workout Title & HUD/Map View Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = (workout?.title ?: "Free GPS Run").uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        color = TextSecondary
                    )
                    if (liveState.isAutoPaused) {
                        Text(
                            text = "AUTO-PAUSED (STOPPED)",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = ElectricAmber
                        )
                    } else if (liveState.isPaused) {
                        Text(
                            text = "PAUSED",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = VividRed
                        )
                    }
                }

                // HUD / Map Toggle Button
                IconButton(
                    onClick = { showMapView = !showMapView },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                ) {
                    Icon(
                        imageVector = if (showMapView) Icons.Default.Speed else Icons.Default.Map,
                        contentDescription = "Toggle View",
                        tint = BrightCyan
                    )
                }
            }

            // Body: Map View or Primary Metric Display
            if (showMapView) {
                RouteMapView(
                    points = liveState.points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    isLiveTracking = true
                )
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Zone 2 Heart Rate Guidance Pill (for Fat Burn run)
                    if (isFatBurn || hr > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(hrStatusColor.copy(alpha = 0.2f))
                                .border(1.dp, hrStatusColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = hrStatusColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hr > 0) "$hr BPM · $hrStatusText" else hrStatusText,
                                    color = hrStatusColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Main Metric: GIANT Current Pace
                    Text(
                        text = "CURRENT PACE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.5.sp,
                        color = TextMuted
                    )
                    Text(
                        text = VdotCalculator.formatPace(currentPace),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = paceStatusColor
                    )
                    Text(
                        text = if (targetPace > 0) "Target: ${VdotCalculator.formatPace(targetPace)} /km" else "/km",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }

            // Bottom Metrics Strip: Distance, Time, Heart Rate
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurfaceElevated)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Distance
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DISTANCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text(
                        text = String.format("%.2f", liveState.distanceMeters / 1000.0),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Text("km", fontSize = 11.sp, color = TextSecondary)
                }

                // Time
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TIME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text(
                        text = VdotCalculator.formatDuration(liveState.durationSeconds),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Text("elapsed", fontSize = 11.sp, color = TextSecondary)
                }

                // Heart Rate / Cadence
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (hr > 0) "HEART RATE" else "CADENCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text(
                        text = if (hr > 0) "$hr" else "${liveState.currentCadence}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (hr > 0) hrStatusColor else BrightCyan
                    )
                    Text(if (hr > 0) "bpm (watch)" else "spm", fontSize = 11.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Running Action Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pause / Resume Button
                if (liveState.isPaused || liveState.isAutoPaused) {
                    Button(
                        onClick = { service?.resumeRun() },
                        modifier = Modifier.size(74.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = Color.Black, modifier = Modifier.size(36.dp))
                    }
                } else {
                    Button(
                        onClick = { service?.pauseRun() },
                        modifier = Modifier.size(74.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricAmber)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.Black, modifier = Modifier.size(36.dp))
                    }
                }

                // Finish Run Button
                Button(
                    onClick = {
                        val completed = service?.stopAndSaveRun()
                        if (completed != null) {
                            onRunFinished(completed.id)
                        }
                    },
                    modifier = Modifier.size(74.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = VividRed)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Finish & Save", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}
