package com.strideflow.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.strideflow.app.engine.NutritionEngine
import com.strideflow.app.engine.VdotCalculator
import com.strideflow.app.model.*
import com.strideflow.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStartRun: (String?) -> Unit,
    onOpenPlanSetup: () -> Unit,
    onOpenPlanCalendar: () -> Unit,
    onOpenPerformance: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember { LocalStorageManager(context) }
    var profile by remember { mutableStateOf(storage.loadProfile()) }
    var plan by remember { mutableStateOf(storage.loadActivePlan()) }
    var allPlans by remember { mutableStateOf(storage.loadAllPlans()) }
    var showNutritionDialog by remember { mutableStateOf(false) }
    var showPlanSwitcherDialog by remember { mutableStateOf(false) }

    val todayWorkout = plan?.workouts?.firstOrNull { !it.isCompleted }
    val todayNutrition = remember(todayWorkout, profile) {
        if (todayWorkout != null) {
            NutritionEngine.getAdviceForWorkout(
                workout = todayWorkout,
                runnerWeightKg = profile.weightKg,
                dietaryPreference = profile.dietaryPreference
            )
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(NeonGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STRIDEFLOW",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPerformance) {
                        Icon(Icons.Default.ShowChart, contentDescription = "Performance & Metrics", tint = NeonGreen)
                    }
                    IconButton(onClick = onOpenPlanCalendar) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Plan Schedule", tint = BrightCyan)
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "Run History", tint = TextPrimary)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurfaceElevated,
                contentColor = TextPrimary
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { /* Already on Coach/Dashboard */ },
                    icon = { Icon(Icons.Default.DirectionsRun, contentDescription = "Coach") },
                    label = { Text("Coach", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = NeonGreen,
                        indicatorColor = NeonGreen,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenPlanCalendar,
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Plan") },
                    label = { Text("Plan", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = BrightCyan,
                        indicatorColor = BrightCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenPerformance,
                    icon = { Icon(Icons.Default.ShowChart, contentDescription = "Performance") },
                    label = { Text("Performance", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = NeonGreen,
                        indicatorColor = NeonGreen,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenHistory,
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = TextPrimary,
                        indicatorColor = DarkSurface,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // VDOT & Active Plan Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = plan?.title ?: "No Active Plan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (plan != null) "Week ${plan?.currentWeek} of ${plan?.totalWeeks}" else "Tap below to assess and create plan",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                            // VDOT Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NeonGreen.copy(alpha = 0.15f))
                                    .border(1.dp, NeonGreen, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "VDOT ${String.format("%.1f", plan?.currentVdot ?: profile.vdot)}",
                                    fontWeight = FontWeight.Black,
                                    color = NeonGreen,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        if (plan == null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onOpenPlanSetup,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                            ) {
                                Icon(Icons.Default.AddChart, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Assess Fitness & Create Plan", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onOpenPlanCalendar,
                                    modifier = Modifier.weight(1.3f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrightCyan)
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = BrightCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Schedule", color = BrightCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        allPlans = storage.loadAllPlans()
                                        showPlanSwitcherDialog = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary)
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Change Plan", color = TextPrimary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Missed Run Auto-Adjustment Notification Banner
            if (plan?.adjustmentNote != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricAmber)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Sync, contentDescription = null, tint = ElectricAmber, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PLAN AUTO-ADJUSTED FOR MISSED RUN",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.8.sp,
                                        color = ElectricAmber
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val cleared = plan!!.copy(adjustmentNote = null)
                                        storage.savePlan(cleared)
                                        plan = cleared
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = plan!!.adjustmentNote!!,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Performance & Trends Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPerformance() },
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(NeonGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ShowChart,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "PERFORMANCE & METRICS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "VDOT, pace progression, weekly mileage & HR",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Text("View Trends ➔", fontWeight = FontWeight.Bold, color = NeonGreen, fontSize = 12.sp)
                    }
                }
            }

            // Today's Scheduled Workout Card
            item {
                Text(
                    text = "TODAY'S WORKOUT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (todayWorkout != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val badgeColor = try {
                                    Color(android.graphics.Color.parseColor(todayWorkout.type.badgeColorHex))
                                } catch (e: Exception) {
                                    BrightCyan
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(badgeColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = todayWorkout.type.displayName.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = badgeColor
                                    )
                                }

                                Text(
                                    text = if (todayWorkout.targetDistanceMeters > 0) {
                                        String.format("%.1f km", todayWorkout.targetDistanceMeters / 1000.0)
                                    } else "Rest Day",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = todayWorkout.title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = todayWorkout.description,
                                fontSize = 14.sp,
                                color = TextSecondary,
                                lineHeight = 20.sp
                            )

                            if (todayWorkout.targetPaceSecPerKm > 0) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DarkSurfaceElevated)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("TARGET PACE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                        Text(
                                            text = "${VdotCalculator.formatPace(todayWorkout.targetPaceSecPerKm)} /km",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            color = BrightCyan
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("CADENCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                        Text(
                                            text = "${profile.cadenceTargetBpm} spm",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Start Run Button
                            if (todayWorkout.type != WorkoutType.REST) {
                                Button(
                                    onClick = { onStartRun(todayWorkout.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(26.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("START WORKOUT", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.Black)
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No workout scheduled today", color = TextSecondary, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onStartRun(null) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrightCyan)
                                ) {
                                    Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Free Run", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { onStartRun(null) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricAmber)
                                ) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Zone 2 Run", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Adaptive Nutrition & Fueling Card
            if (todayWorkout != null && todayWorkout.type != WorkoutType.REST && todayNutrition != null) {
                item {
                    Text(
                        text = "WORKOUT FUELING & RECOVERY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = ElectricAmber)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Fueling Strategy", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                                }
                                Text(
                                    text = profile.dietaryPreference.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricAmber
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Pre-Run Meal
                            Text(
                                text = "Pre-Run (${todayNutrition.preRunMealWindow}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Text(
                                text = todayNutrition.preRunMealSummary,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Pre-Run Primer
                            Text(
                                text = "Primer (${todayNutrition.preRunPrimerWindow}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Text(
                                text = todayNutrition.preRunPrimerSummary,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )

                            if (todayNutrition.intraRunFueling != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "During Run:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VividRed
                                )
                                Text(
                                    text = todayNutrition.intraRunFueling,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { showNutritionDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("View Tailored Snack & Meal Ideas", color = BrightCyan, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (showNutritionDialog && todayNutrition != null) {
            AlertDialog(
                onDismissRequest = { showNutritionDialog = false },
                title = { Text("Tailored Meal & Snack Ideas (${profile.dietaryPreference.displayName})", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        todayNutrition.recipeIdeas.forEach { idea ->
                            Text("• $idea", fontSize = 14.sp, color = TextPrimary)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNutritionDialog = false }) {
                        Text("Got It", color = NeonGreen)
                    }
                },
                containerColor = DarkSurfaceElevated
            )
        }

        // Change Plan / Plan Switcher Dialog
        if (showPlanSwitcherDialog) {
            AlertDialog(
                onDismissRequest = { showPlanSwitcherDialog = false },
                title = { Text("Change Training Plan", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Select a plan to activate or create a new goal:", fontSize = 13.sp, color = TextSecondary)

                        allPlans.forEach { p ->
                            val isActive = p.id == plan?.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val switched = storage.switchActivePlan(p.id)
                                        if (switched != null) {
                                            plan = switched
                                            profile = storage.loadProfile()
                                            allPlans = storage.loadAllPlans()
                                            showPlanSwitcherDialog = false
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) DarkSurfaceElevated else DarkSurface
                                ),
                                border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, NeonGreen) else androidx.compose.foundation.BorderStroke(0.5.dp, DarkOutline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(p.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                        Text("Week ${p.currentWeek}/${p.totalWeeks} · VDOT ${String.format("%.1f", p.currentVdot)}", fontSize = 11.sp, color = TextSecondary)
                                    }
                                    if (isActive) {
                                        Text("ACTIVE", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    } else {
                                        Text("Tap to Activate", color = BrightCyan, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                showPlanSwitcherDialog = false
                                onOpenPlanSetup()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create New Plan", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlanSwitcherDialog = false }) {
                        Text("Close", color = TextSecondary)
                    }
                },
                containerColor = DarkSurfaceElevated
            )
        }
    }
}
