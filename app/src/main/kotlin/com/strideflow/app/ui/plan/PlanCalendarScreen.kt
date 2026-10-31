package com.strideflow.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.strideflow.app.engine.AdaptiveCoach
import com.strideflow.app.engine.VdotCalculator
import com.strideflow.app.model.Workout
import com.strideflow.app.model.WorkoutType
import com.strideflow.app.service.NutritionNotificationScheduler
import com.strideflow.app.ui.theme.*
import kotlin.math.roundToInt

enum class PlanViewMode {
    CALENDAR,
    LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanCalendarScreen(
    onSelectWorkoutToRun: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember { LocalStorageManager(context) }
    var plan by remember { mutableStateOf(storage.loadActivePlan()) }
    var viewMode by remember { mutableStateOf(PlanViewMode.CALENDAR) }
    var selectedWeek by remember { mutableIntStateOf(plan?.currentWeek ?: 1) }

    var workoutToReschedule by remember { mutableStateOf<Workout?>(null) }
    var newDayIndex by remember { mutableIntStateOf(0) }
    var showEditTargetPaceDialog by remember { mutableStateOf(false) }

    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan?.title ?: "TRAINING PLAN", fontWeight = FontWeight.Black, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Toggle Calendar / List
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceElevated)
                            .padding(2.dp)
                    ) {
                        IconButton(
                            onClick = { viewMode = PlanViewMode.CALENDAR },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Calendar View",
                                tint = if (viewMode == PlanViewMode.CALENDAR) NeonGreen else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewMode = PlanViewMode.LIST },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.FormatListBulleted,
                                contentDescription = "List View",
                                tint = if (viewMode == PlanViewMode.LIST) NeonGreen else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (plan == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No active plan. Create one on the dashboard!", color = TextSecondary, fontSize = 15.sp)
            }
        } else {
            val totalWeeks = plan!!.totalWeeks
            val currentWeekWorkouts = plan!!.workouts.filter { it.weekIndex == selectedWeek }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Active Plan Target & Pace Banner
                item {
                    val distanceKm = (plan!!.goal.goalType.defaultDistanceMeters / 1000.0).coerceAtLeast(1.0)
                    val targetPaceSec = plan!!.goal.targetTimeSeconds.toDouble() / distanceKm

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TARGET: ${plan!!.goal.goalType.displayName.uppercase()}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    color = BrightCyan
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${VdotCalculator.formatPace(targetPaceSec)} /km • ${VdotCalculator.formatDuration(plan!!.goal.targetTimeSeconds)}",
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                    color = NeonGreen
                                )
                                Text(
                                    text = "Calibrated VDOT ${String.format("%.1f", plan!!.currentVdot)}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            OutlinedButton(
                                onClick = { showEditTargetPaceDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Edit Pace", fontSize = 12.sp, color = BrightCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Week Selector Tabs
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WEEK $selectedWeek OF $totalWeeks",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp,
                            color = BrightCyan
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = { if (selectedWeek > 1) selectedWeek-- },
                                enabled = selectedWeek > 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Week", tint = if (selectedWeek > 1) TextPrimary else TextMuted)
                            }
                            IconButton(
                                onClick = { if (selectedWeek < totalWeeks) selectedWeek++ },
                                enabled = selectedWeek < totalWeeks,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Week", tint = if (selectedWeek < totalWeeks) TextPrimary else TextMuted)
                            }
                        }
                    }
                }

                if (viewMode == PlanViewMode.CALENDAR) {
                    // CALENDAR VIEW: 7 Day Grid
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            daysOfWeek.forEachIndexed { dayIndex, dayName ->
                                val dayWorkout = currentWeekWorkouts.firstOrNull { it.dayIndex == dayIndex }
                                val isRest = dayWorkout == null || dayWorkout.type == WorkoutType.REST

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (dayWorkout?.isCompleted == true) DarkSurfaceElevated.copy(alpha = 0.6f) else DarkSurface
                                    ),
                                    border = if (dayWorkout?.isCompleted == true) {
                                        androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, DarkOutline)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left: Day of week indicator
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isRest) DarkSurfaceElevated else BrightCyan.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isRest) TextMuted else BrightCyan
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column {
                                                Text(
                                                    text = dayWorkout?.title ?: "Rest / Recovery",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = if (isRest) TextMuted else TextPrimary
                                                )
                                                if (!isRest && dayWorkout != null) {
                                                    Text(
                                                        text = "${String.format("%.1f km", dayWorkout.targetDistanceMeters / 1000.0)} · Target: ${VdotCalculator.formatPace(dayWorkout.targetPaceSecPerKm)}/km",
                                                        fontSize = 12.sp,
                                                        color = TextSecondary
                                                    )
                                                }
                                            }
                                        }

                                        // Right: Completed Check or Reschedule Action
                                        if (dayWorkout?.isCompleted == true) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(NeonGreen.copy(alpha = 0.15f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("COMPLETED", fontSize = 10.sp, fontWeight = FontWeight.Black, color = NeonGreen)
                                            }
                                        } else if (dayWorkout?.isMissed == true) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(ElectricAmber.copy(alpha = 0.2f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("MISSED", fontSize = 10.sp, fontWeight = FontWeight.Black, color = ElectricAmber)
                                            }
                                        } else if (!isRest && dayWorkout != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        workoutToReschedule = dayWorkout
                                                        newDayIndex = dayWorkout.dayIndex
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.EditCalendar, contentDescription = "Reschedule", tint = ElectricAmber, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(
                                                    onClick = { onSelectWorkoutToRun(dayWorkout.id) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = NeonGreen, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // LIST VIEW: Grouped Workouts for the Week
                    items(currentWeekWorkouts) { workout ->
                        val isRest = workout.type == WorkoutType.REST

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${daysOfWeek.getOrElse(workout.dayIndex) { "Day ${workout.dayIndex}" }} · ${workout.type.displayName}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRest) TextMuted else BrightCyan
                                    )

                                    if (workout.isCompleted) {
                                        Text("✓ Completed", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    } else if (!isRest) {
                                        TextButton(
                                            onClick = {
                                                workoutToReschedule = workout
                                                newDayIndex = workout.dayIndex
                                            },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Reschedule", fontSize = 12.sp, color = ElectricAmber)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = workout.title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = workout.description,
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )

                                if (!isRest) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurfaceElevated)
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Dist: ${String.format("%.1f km", workout.targetDistanceMeters / 1000.0)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text("Target: ${VdotCalculator.formatPace(workout.targetPaceSecPerKm)}/km", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrightCyan)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        // Reschedule Dialog
        if (workoutToReschedule != null) {
            val workout = workoutToReschedule!!
            AlertDialog(
                onDismissRequest = { workoutToReschedule = null },
                title = { Text("Reschedule Workout", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Move \"${workout.title}\" to another day of Week ${workout.weekIndex}:",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            daysOfWeek.forEachIndexed { idx, name ->
                                val isSelected = newDayIndex == idx
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) NeonGreen else DarkSurface)
                                        .border(1.dp, if (isSelected) NeonGreen else DarkOutline, RoundedCornerShape(8.dp))
                                        .clickable { newDayIndex = idx },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            storage.rescheduleWorkout(workout.id, newDayIndex, null)
                            plan = storage.loadActivePlan()
                            workoutToReschedule = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                    ) {
                        Text("Move Workout", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { workoutToReschedule = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurfaceElevated
            )
        }

        // Edit Target Pace Dialog
        if (showEditTargetPaceDialog && plan != null) {
            val distKm = (plan!!.goal.goalType.defaultDistanceMeters / 1000.0).coerceAtLeast(1.0)
            val currentPaceSec = plan!!.goal.targetTimeSeconds.toDouble() / distKm
            val currentTargetTime = plan!!.goal.targetTimeSeconds

            var editHours by remember { mutableStateOf("${currentTargetTime / 3600}") }
            var editMins by remember { mutableStateOf("%02d".format((currentTargetTime % 3600) / 60)) }
            var editSecs by remember { mutableStateOf("%02d".format(currentTargetTime % 60)) }

            var editPaceMin by remember { mutableStateOf("${(currentPaceSec / 60).toInt()}") }
            var editPaceSec by remember { mutableStateOf("%02d".format((currentPaceSec % 60).roundToInt())) }

            // Realistic baseline calculation
            val realisticTimeSec = remember(plan) {
                val targetVdot = (plan!!.startingVdot + 1.8).coerceIn(20.0, 85.0)
                VdotCalculator.predictRaceTimeSeconds(targetVdot, plan!!.goal.goalType.defaultDistanceMeters)
            }
            val realisticPaceSec = realisticTimeSec.toDouble() / distKm

            AlertDialog(
                onDismissRequest = { showEditTargetPaceDialog = false },
                title = { Text("Change Plan Target Pace", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Realistic baseline: ${VdotCalculator.formatPace(realisticPaceSec)} /km (${VdotCalculator.formatDuration(realisticTimeSec)})",
                            fontSize = 12.sp,
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Adjust your target pace or finish time. All upcoming workouts in your active plan will update accordingly.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        // Steppers & Pace Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    val curSec = ((editPaceMin.toLongOrNull() ?: 4L) * 60) + (editPaceSec.toLongOrNull() ?: 0L)
                                    val newPace = (curSec - 5).coerceAtLeast(120)
                                    editPaceMin = "${newPace / 60}"
                                    editPaceSec = "%02d".format(newPace % 60)
                                    val totalSec = (newPace * distKm).roundToInt().toLong()
                                    editHours = "${totalSec / 3600}"
                                    editMins = "%02d".format((totalSec % 3600) / 60)
                                    editSecs = "%02d".format(totalSec % 60)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("-5s", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrightCyan)
                            }

                            OutlinedTextField(
                                value = editPaceMin,
                                onValueChange = {
                                    editPaceMin = it
                                    val m = it.toLongOrNull() ?: 4L
                                    val s = editPaceSec.toLongOrNull() ?: 0L
                                    val totalSec = (((m * 60) + s) * distKm).roundToInt().toLong()
                                    editHours = "${totalSec / 3600}"
                                    editMins = "%02d".format((totalSec % 3600) / 60)
                                    editSecs = "%02d".format(totalSec % 60)
                                },
                                label = { Text("Min") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Text(":", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = editPaceSec,
                                onValueChange = {
                                    editPaceSec = it
                                    val m = editPaceMin.toLongOrNull() ?: 4L
                                    val s = it.toLongOrNull() ?: 0L
                                    val totalSec = (((m * 60) + s) * distKm).roundToInt().toLong()
                                    editHours = "${totalSec / 3600}"
                                    editMins = "%02d".format((totalSec % 3600) / 60)
                                    editSecs = "%02d".format(totalSec % 60)
                                },
                                label = { Text("Sec") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )

                            FilledTonalIconButton(
                                onClick = {
                                    val curSec = ((editPaceMin.toLongOrNull() ?: 4L) * 60) + (editPaceSec.toLongOrNull() ?: 0L)
                                    val newPace = curSec + 5
                                    editPaceMin = "${newPace / 60}"
                                    editPaceSec = "%02d".format(newPace % 60)
                                    val totalSec = (newPace * distKm).roundToInt().toLong()
                                    editHours = "${totalSec / 3600}"
                                    editMins = "%02d".format((totalSec % 3600) / 60)
                                    editSecs = "%02d".format(totalSec % 60)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("+5s", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrightCyan)
                            }
                        }

                        Text(
                            text = "New Finish Time: ${editHours}h ${editMins}m ${editSecs}s",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrightCyan
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val h = editHours.toLongOrNull() ?: 0L
                            val m = editMins.toLongOrNull() ?: 25L
                            val s = editSecs.toLongOrNull() ?: 0L
                            val newTotalSecs = (h * 3600) + (m * 60) + s

                            val updatedPlan = AdaptiveCoach.updatePlanTargetTime(plan!!, newTotalSecs)
                            storage.savePlan(updatedPlan)
                            val userProf = storage.loadProfile()
                            val updatedProfile = userProf.copy(vdot = updatedPlan.currentVdot)
                            storage.saveProfile(updatedProfile)
                            NutritionNotificationScheduler.syncNotifications(context, updatedPlan, updatedProfile)
                            plan = updatedPlan
                            showEditTargetPaceDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                    ) {
                        Text("Apply & Update Paces", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditTargetPaceDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurfaceElevated
            )
        }
    }
}
