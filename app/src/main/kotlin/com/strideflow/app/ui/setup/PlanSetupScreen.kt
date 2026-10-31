package com.strideflow.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strideflow.app.data.LocalStorageManager
import com.strideflow.app.engine.PlanGenerator
import com.strideflow.app.engine.VdotCalculator
import com.strideflow.app.model.GoalType
import com.strideflow.app.model.RunningGoal
import com.strideflow.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSetupScreen(
    onPlanCreated: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember { LocalStorageManager(context) }
    val profile = remember { storage.loadProfile() }

    var selectedGoalType by remember { mutableStateOf(GoalType.FIVE_K) }
    var targetMinutesText by remember { mutableStateOf("24") }
    var targetSecondsText by remember { mutableStateOf("30") }

    var daysPerWeek by remember { mutableIntStateOf(4) }
    var weeklyKmText by remember { mutableStateOf("25") }

    // Assessment: Recent PR
    var prDistanceMetersText by remember { mutableStateOf("5000") }
    var prMinutesText by remember { mutableStateOf("26") }
    var prSecondsText by remember { mutableStateOf("00") }

    // Calculate live VDOT based on PR input
    val calculatedVdot = remember(prDistanceMetersText, prMinutesText, prSecondsText) {
        val dist = prDistanceMetersText.toDoubleOrNull() ?: 5000.0
        val mins = prMinutesText.toLongOrNull() ?: 26L
        val secs = prSecondsText.toLongOrNull() ?: 0L
        val totalSecs = (mins * 60) + secs
        VdotCalculator.calculateVdot(dist, totalSecs)
    }

    val previewPaces = remember(calculatedVdot) {
        VdotCalculator.getPacesForVdot(calculatedVdot)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PLAN SETUP & ASSESSMENT", fontWeight = FontWeight.Black, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Step 1: Goal Selection
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

            // Step 2: Target Time
            item {
                Text("2. TARGET TIME GOAL", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = targetMinutesText,
                        onValueChange = { targetMinutesText = it },
                        label = { Text("Minutes") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = targetSecondsText,
                        onValueChange = { targetSecondsText = it },
                        label = { Text("Seconds") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Step 3: Fitness Assessment
            item {
                Text("3. CURRENT FITNESS ASSESSMENT (BENCHMARK)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Enter a recent race, 5K, or time-trial performance:",
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
                                label = { Text("PR Min") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = prSecondsText,
                                onValueChange = { prSecondsText = it },
                                label = { Text("PR Sec") },
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
                                Text("CALCULATED VDOT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                Text(
                                    text = String.format("%.1f", calculatedVdot),
                                    fontSize = 24.sp,
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

            // Step 4: Schedule Preferences
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

            // Generate Button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val targetMins = targetMinutesText.toLongOrNull() ?: 25L
                        val targetSecs = targetSecondsText.toLongOrNull() ?: 0L
                        val totalTargetSecs = (targetMins * 60) + targetSecs
                        val baselineKm = weeklyKmText.toDoubleOrNull() ?: 25.0

                        val goal = RunningGoal(
                            goalType = selectedGoalType,
                            targetTimeSeconds = totalTargetSecs,
                            targetDateEpochMillis = System.currentTimeMillis() + (8L * 7 * 24 * 3600 * 1000),
                            daysPerWeek = daysPerWeek
                        )

                        val newPlan = PlanGenerator.generatePlan(
                            goal = goal,
                            startingVdot = calculatedVdot,
                            weeklyBaselineKm = baselineKm,
                            totalWeeks = 8
                        )

                        storage.savePlan(newPlan)
                        val updatedProfile = profile.copy(
                            vdot = calculatedVdot,
                            weeklyMileageBaselineKm = baselineKm,
                            activePlanId = newPlan.id
                        )
                        storage.saveProfile(updatedProfile)

                        com.strideflow.app.service.NutritionNotificationScheduler.syncNotifications(context, newPlan, updatedProfile)

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
