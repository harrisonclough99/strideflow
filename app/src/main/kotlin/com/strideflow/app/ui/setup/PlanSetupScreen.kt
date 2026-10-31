package com.strideflow.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.strideflow.app.data.LocalStorageManager
import com.strideflow.app.engine.PlanGenerator
import com.strideflow.app.engine.VdotCalculator
import com.strideflow.app.model.GoalType
import com.strideflow.app.model.RunningGoal
import com.strideflow.app.model.UnitSystem
import com.strideflow.app.service.NutritionNotificationScheduler
import com.strideflow.app.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSetupScreen(
    onPlanCreated: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember { LocalStorageManager(context) }
    val profile = remember { storage.loadProfile() }
    val allRuns = remember { storage.loadRuns() }

    // Derive initial baseline VDOT from past runs or saved profile
    val initialVdot = remember {
        val runsWithProposal = allRuns.mapNotNull { it.adaptationProposal?.proposedVdot }
        if (runsWithProposal.isNotEmpty()) {
            runsWithProposal.last()
        } else {
            profile.vdot.coerceIn(20.0, 85.0)
        }
    }

    var selectedGoalType by remember { mutableStateOf(GoalType.FIVE_K) }
    var daysPerWeek by remember { mutableIntStateOf(4) }
    var weeklyKmText by remember { mutableStateOf(profile.weeklyMileageBaselineKm.toInt().toString()) }

    // Assessment: Recent Benchmark or PR
    var prDistanceMetersText by remember { mutableStateOf("5000") }
    val initialPrTimeSec = remember(initialVdot) {
        VdotCalculator.predictRaceTimeSeconds(initialVdot, 5000.0)
    }
    var prMinutesText by remember { mutableStateOf("${initialPrTimeSec / 60}") }
    var prSecondsText by remember { mutableStateOf("%02d".format(initialPrTimeSec % 60)) }

    // Live calculated current ability VDOT
    val calculatedCurrentVdot = remember(prDistanceMetersText, prMinutesText, prSecondsText) {
        val dist = prDistanceMetersText.toDoubleOrNull() ?: 5000.0
        val mins = prMinutesText.toLongOrNull() ?: 25L
        val secs = prSecondsText.toLongOrNull() ?: 0L
        val totalSecs = (mins * 60) + secs
        VdotCalculator.calculateVdot(dist, totalSecs)
    }

    // Realistic target calculation helper (8-week progressive training cycle: +1.8 VDOT / ~4% improvement)
    val realisticTargetVdot = remember(calculatedCurrentVdot) {
        (calculatedCurrentVdot + 1.8).coerceIn(20.0, 85.0)
    }
    val realisticTargetTimeSec = remember(realisticTargetVdot, selectedGoalType) {
        VdotCalculator.predictRaceTimeSeconds(realisticTargetVdot, selectedGoalType.defaultDistanceMeters)
    }
    val realisticTargetPaceSecPerKm = remember(realisticTargetTimeSec, selectedGoalType) {
        realisticTargetTimeSec.toDouble() / (selectedGoalType.defaultDistanceMeters / 1000.0)
    }

    // Target fields (prefilled with realistic calculated values, freely editable by user)
    var isCustomizedByUser by remember { mutableStateOf(false) }

    var targetHoursText by remember { mutableStateOf("${realisticTargetTimeSec / 3600}") }
    var targetMinutesText by remember { mutableStateOf("%02d".format((realisticTargetTimeSec % 3600) / 60)) }
    var targetSecondsText by remember { mutableStateOf("%02d".format(realisticTargetTimeSec % 60)) }

    var targetPaceMinText by remember { mutableStateOf("${(realisticTargetPaceSecPerKm / 60).toInt()}") }
    var targetPaceSecText by remember { mutableStateOf("%02d".format((realisticTargetPaceSecPerKm % 60).roundToInt())) }

    // When goal type or ability changes, update prefilled realistic pace if user hasn't explicitly customized
    LaunchedEffect(selectedGoalType, calculatedCurrentVdot) {
        if (!isCustomizedByUser) {
            val hours = realisticTargetTimeSec / 3600
            val mins = (realisticTargetTimeSec % 3600) / 60
            val secs = realisticTargetTimeSec % 60

            targetHoursText = "$hours"
            targetMinutesText = "%02d".format(mins)
            targetSecondsText = "%02d".format(secs)

            val paceTotalSec = realisticTargetPaceSecPerKm.roundToInt()
            targetPaceMinText = "${paceTotalSec / 60}"
            targetPaceSecText = "%02d".format(paceTotalSec % 60)
        }
    }

    // Helper to sync from Target Time to Target Pace
    fun updatePaceFromTime(hours: Long, minutes: Long, seconds: Long) {
        val totalSec = (hours * 3600) + (minutes * 60) + seconds
        val distKm = selectedGoalType.defaultDistanceMeters / 1000.0
        if (distKm > 0) {
            val paceSec = totalSec.toDouble() / distKm
            val paceMin = (paceSec / 60).toInt()
            val paceSecRemain = (paceSec % 60).roundToInt()
            targetPaceMinText = "$paceMin"
            targetPaceSecText = "%02d".format(paceSecRemain)
        }
    }

    // Helper to sync from Target Pace to Target Time
    fun updateTimeFromPace(paceMin: Long, paceSec: Long) {
        val paceSecPerKm = (paceMin * 60) + paceSec
        val distKm = selectedGoalType.defaultDistanceMeters / 1000.0
        val totalSec = (paceSecPerKm * distKm).roundToInt().toLong()
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        targetHoursText = "$hours"
        targetMinutesText = "%02d".format(mins)
        targetSecondsText = "%02d".format(secs)
    }

    val previewPaces = remember(calculatedCurrentVdot) {
        VdotCalculator.getPacesForVdot(calculatedCurrentVdot)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PLAN SETUP & ASSESSMENT", fontWeight = FontWeight.Black, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // STEP 1: GOAL SELECTION
            item {
                Text("1. SELECT YOUR RUNNING GOAL", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalType.values().forEach { goal ->
                        val isSelected = selectedGoalType == goal
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedGoalType = goal },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) DarkSurfaceElevated else DarkSurface),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, NeonGreen) else androidx.compose.foundation.BorderStroke(1.dp, DarkOutline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = goal.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) NeonGreen else TextPrimary,
                                    fontSize = 15.sp
                                )
                                if (isSelected) {
                                    Text("✓ Selected", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // STEP 2: FITNESS ASSESSMENT (CURRENT ABILITY)
            item {
                Text("2. CURRENT FITNESS ASSESSMENT (CURRENT ABILITY)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Enter a recent 5K, race, or benchmark time trial to calibrate your aerobic ability:",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = prDistanceMetersText,
                            onValueChange = { prDistanceMetersText = it },
                            label = { Text("Benchmark Distance (Meters)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = prMinutesText,
                                onValueChange = { prMinutesText = it },
                                label = { Text("Minutes") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = prSecondsText,
                                onValueChange = { prSecondsText = it },
                                label = { Text("Seconds") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Live VDOT Calculation Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("CURRENT VDOT ABILITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                Text(
                                    text = String.format("%.1f", calculatedCurrentVdot),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonGreen
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Easy Pace: ${VdotCalculator.formatPace(previewPaces.easyPaceSecPerKm)}/km", fontSize = 12.sp, color = TextSecondary)
                                Text("Threshold: ${VdotCalculator.formatPace(previewPaces.thresholdPaceSecPerKm)}/km", fontSize = 12.sp, color = BrightCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // STEP 3: PREFILLED & CUSTOMIZABLE PLAN TARGET PACE & TIME
            item {
                Text("3. PLAN TARGET PACE & TIME GOAL", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, if (!isCustomizedByUser) NeonGreen else BrightCyan)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Realistic Target Banner
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (!isCustomizedByUser) "CALCULATED REALISTIC TARGET" else "CUSTOM TARGET PACE",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = if (!isCustomizedByUser) NeonGreen else BrightCyan
                                )
                                Text(
                                    text = if (!isCustomizedByUser) {
                                        "Calibrated from current VDOT ${String.format("%.1f", calculatedCurrentVdot)} with 8-week progressive training (+1.8 VDOT / ~4% gain)."
                                    } else {
                                        "User-modified target pace. You can reset to the realistic calculation anytime."
                                    },
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            if (isCustomizedByUser) {
                                TextButton(
                                    onClick = {
                                        isCustomizedByUser = false
                                        val hours = realisticTargetTimeSec / 3600
                                        val mins = (realisticTargetTimeSec % 3600) / 60
                                        val secs = realisticTargetTimeSec % 60
                                        targetHoursText = "$hours"
                                        targetMinutesText = "%02d".format(mins)
                                        targetSecondsText = "%02d".format(secs)
                                        val paceTotalSec = realisticTargetPaceSecPerKm.roundToInt()
                                        targetPaceMinText = "${paceTotalSec / 60}"
                                        targetPaceSecText = "%02d".format(paceTotalSec % 60)
                                    }
                                ) {
                                    Text("Reset", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // SECTION A: TARGET PACE INPUT & STEPPERS
                        Text("TARGET PACE (${profile.unitSystem.paceLabel})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Decrement 5s Button
                            FilledTonalIconButton(
                                onClick = {
                                    isCustomizedByUser = true
                                    val currentPaceSec = ((targetPaceMinText.toLongOrNull() ?: 4L) * 60) + (targetPaceSecText.toLongOrNull() ?: 0L)
                                    val newPaceSec = (currentPaceSec - 5).coerceAtLeast(120)
                                    targetPaceMinText = "${newPaceSec / 60}"
                                    targetPaceSecText = "%02d".format(newPaceSec % 60)
                                    updateTimeFromPace(newPaceSec / 60, newPaceSec % 60)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Text("-5s", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrightCyan)
                            }

                            OutlinedTextField(
                                value = targetPaceMinText,
                                onValueChange = {
                                    isCustomizedByUser = true
                                    targetPaceMinText = it
                                    val min = it.toLongOrNull() ?: 4L
                                    val sec = targetPaceSecText.toLongOrNull() ?: 0L
                                    updateTimeFromPace(min, sec)
                                },
                                label = { Text("Pace Min") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Text(":", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextSecondary)

                            OutlinedTextField(
                                value = targetPaceSecText,
                                onValueChange = {
                                    isCustomizedByUser = true
                                    targetPaceSecText = it
                                    val min = targetPaceMinText.toLongOrNull() ?: 4L
                                    val sec = it.toLongOrNull() ?: 0L
                                    updateTimeFromPace(min, sec)
                                },
                                label = { Text("Pace Sec") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )

                            // Increment 5s Button
                            FilledTonalIconButton(
                                onClick = {
                                    isCustomizedByUser = true
                                    val currentPaceSec = ((targetPaceMinText.toLongOrNull() ?: 4L) * 60) + (targetPaceSecText.toLongOrNull() ?: 0L)
                                    val newPaceSec = currentPaceSec + 5
                                    targetPaceMinText = "${newPaceSec / 60}"
                                    targetPaceSecText = "%02d".format(newPaceSec % 60)
                                    updateTimeFromPace(newPaceSec / 60, newPaceSec % 60)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Text("+5s", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrightCyan)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = DarkOutline)
                        Spacer(modifier = Modifier.height(14.dp))

                        // SECTION B: CORRESPONDING TOTAL FINISH TIME
                        Text("CORRESPONDING FINISH TIME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = targetHoursText,
                                onValueChange = {
                                    isCustomizedByUser = true
                                    targetHoursText = it
                                    val h = it.toLongOrNull() ?: 0L
                                    val m = targetMinutesText.toLongOrNull() ?: 0L
                                    val s = targetSecondsText.toLongOrNull() ?: 0L
                                    updatePaceFromTime(h, m, s)
                                },
                                label = { Text("Hours") },
                                modifier = Modifier.weight(0.9f),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = targetMinutesText,
                                onValueChange = {
                                    isCustomizedByUser = true
                                    targetMinutesText = it
                                    val h = targetHoursText.toLongOrNull() ?: 0L
                                    val m = it.toLongOrNull() ?: 0L
                                    val s = targetSecondsText.toLongOrNull() ?: 0L
                                    updatePaceFromTime(h, m, s)
                                },
                                label = { Text("Minutes") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = targetSecondsText,
                                onValueChange = {
                                    isCustomizedByUser = true
                                    targetSecondsText = it
                                    val h = targetHoursText.toLongOrNull() ?: 0L
                                    val m = targetMinutesText.toLongOrNull() ?: 0L
                                    val s = it.toLongOrNull() ?: 0L
                                    updatePaceFromTime(h, m, s)
                                },
                                label = { Text("Seconds") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurface)
                                .padding(10.dp)
                        ) {
                            val paceDisplay = "${targetPaceMinText}:${targetPaceSecText.padStart(2, '0')}"
                            val timeDisplay = if ((targetHoursText.toLongOrNull() ?: 0L) > 0) {
                                "${targetHoursText}h ${targetMinutesText}m ${targetSecondsText}s"
                            } else {
                                "${targetMinutesText}m ${targetSecondsText}s"
                            }
                            Text(
                                text = "🎯 Goal: ${selectedGoalType.displayName} in $timeDisplay ($paceDisplay /km)",
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // STEP 4: SCHEDULE PREFERENCES
            item {
                Text("4. WEEKLY SCHEDULE & VOLUME", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Training Days per Week:", fontSize = 14.sp, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 4, 5, 6).forEach { d ->
                            val selected = daysPerWeek == d
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) NeonGreen else DarkSurfaceElevated)
                                    .border(1.dp, if (selected) NeonGreen else DarkOutline, RoundedCornerShape(8.dp))
                                    .clickable { daysPerWeek = d },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$d",
                                    color = if (selected) Color.Black else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = weeklyKmText,
                    onValueChange = { weeklyKmText = it },
                    label = { Text("Current Weekly Baseline (km)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // GENERATE BUTTON
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val targetH = targetHoursText.toLongOrNull() ?: 0L
                        val targetM = targetMinutesText.toLongOrNull() ?: 25L
                        val targetS = targetSecondsText.toLongOrNull() ?: 0L
                        val totalTargetSecs = (targetH * 3600) + (targetM * 60) + targetS
                        val baselineKm = weeklyKmText.toDoubleOrNull() ?: 25.0

                        val goal = RunningGoal(
                            goalType = selectedGoalType,
                            targetTimeSeconds = totalTargetSecs,
                            targetDateEpochMillis = System.currentTimeMillis() + (8L * 7 * 24 * 3600 * 1000),
                            daysPerWeek = daysPerWeek
                        )

                        val newPlan = PlanGenerator.generatePlan(
                            goal = goal,
                            startingVdot = calculatedCurrentVdot,
                            weeklyBaselineKm = baselineKm,
                            totalWeeks = 8
                        )

                        storage.savePlan(newPlan)
                        val updatedProfile = profile.copy(
                            vdot = calculatedCurrentVdot,
                            weeklyMileageBaselineKm = baselineKm,
                            activePlanId = newPlan.id
                        )
                        storage.saveProfile(updatedProfile)

                        NutritionNotificationScheduler.syncNotifications(context, newPlan, updatedProfile)

                        onPlanCreated()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text("GENERATE ON-DEVICE TRAINING PLAN", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.Black)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
