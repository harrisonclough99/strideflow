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
import androidx.compose.material.icons.filled.*
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
import com.strideflow.app.engine.VdotCalculator
import com.strideflow.app.model.Workout
import com.strideflow.app.model.WorkoutType
import com.strideflow.app.ui.theme.*

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

    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan?.title ?: "TRAINING PLAN", fontWeight = FontWeight.Black, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
    }
}
