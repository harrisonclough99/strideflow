package com.strideflow.app.ui.performance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strideflow.app.data.LocalStorageManager
import com.strideflow.app.engine.VdotCalculator
import com.strideflow.app.model.RecordedRun
import com.strideflow.app.model.UnitSystem
import com.strideflow.app.model.WorkoutType
import com.strideflow.app.ui.common.AppBottomNavigationBar
import com.strideflow.app.ui.common.MainNavTab
import com.strideflow.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class PerformanceTimeRange(val label: String, val days: Int) {
    DAYS_7("7D", 7),
    DAYS_30("30D", 30),
    WEEKS_12("12W", 84),
    ALL_TIME("ALL", 3650)
}

enum class MetricViewMode(val label: String) {
    ALL("All Trends"),
    VDOT("VDOT Fitness"),
    PACE("Pace"),
    VOLUME("Weekly Mileage"),
    HEART_RATE("Heart Rate & Zone 2")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    onBack: () -> Unit,
    onNavigateToCoach: () -> Unit = onBack,
    onNavigateToPlan: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val storage = remember { LocalStorageManager(context) }
    val profile by remember { mutableStateOf(storage.loadProfile()) }
    val activePlan by remember { mutableStateOf(storage.loadActivePlan()) }
    val allRuns by remember { mutableStateOf(storage.loadRuns()) }

    var selectedRange by remember { mutableStateOf(PerformanceTimeRange.DAYS_30) }
    var selectedMetric by remember { mutableStateOf(MetricViewMode.ALL) }

    // Filter runs based on selected time range
    val now = remember { System.currentTimeMillis() }
    val filteredRuns = remember(allRuns, selectedRange) {
        val cutoff = now - (selectedRange.days.toLong() * 24L * 3600L * 1000L)
        allRuns.filter { it.startTimeMillis >= cutoff }.sortedBy { it.startTimeMillis }
    }

    // High-level aggregate metrics
    val totalDistanceMeters = remember(filteredRuns) { filteredRuns.sumOf { it.distanceMeters } }
    val totalDurationSeconds = remember(filteredRuns) { filteredRuns.sumOf { it.durationSeconds } }
    val avgPaceSec = remember(filteredRuns, totalDistanceMeters, totalDurationSeconds) {
        if (totalDistanceMeters > 50) totalDurationSeconds.toDouble() / (totalDistanceMeters / 1000.0) else 0.0
    }
    val avgHr = remember(filteredRuns) {
        val withHr = filteredRuns.filter { it.averageHeartRateBpm > 0 }
        if (withHr.isNotEmpty()) withHr.map { it.averageHeartRateBpm }.average().roundToInt() else 0
    }
    val avgCadence = remember(filteredRuns) {
        val withCadence = filteredRuns.filter { it.averageCadence > 0 }
        if (withCadence.isNotEmpty()) withCadence.map { it.averageCadence }.average().roundToInt() else 0
    }

    // Current VDOT & change over time
    val currentVdot = activePlan?.currentVdot ?: profile.vdot
    val startingVdot = activePlan?.startingVdot ?: profile.vdot
    val vdotDelta = currentVdot - startingVdot

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PERFORMANCE & METRICS",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                currentTab = MainNavTab.PERFORMANCE,
                onNavigateToCoach = onNavigateToCoach,
                onNavigateToPlan = onNavigateToPlan,
                onNavigateToPerformance = { /* already here */ },
                onNavigateToHistory = onNavigateToHistory
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
            // TIME RANGE SELECTOR PILLS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PerformanceTimeRange.values().forEach { range ->
                        val isSelected = selectedRange == range
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) NeonGreen else DarkSurfaceElevated)
                                .border(1.dp, if (isSelected) NeonGreen else DarkOutline, RoundedCornerShape(8.dp))
                                .clickable { selectedRange = range }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = range.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSelected) Color.Black else TextSecondary
                            )
                        }
                    }
                }
            }

            // TOP HIGHLIGHT STATS GRID
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatTile(
                                label = "TOTAL VOLUME",
                                value = String.format("%.1f %s", totalDistanceMeters / 1000.0, profile.unitSystem.distanceLabel),
                                subtext = "${filteredRuns.size} completed runs",
                                tint = BrightCyan
                            )
                            StatTile(
                                label = "AVERAGE PACE",
                                value = if (avgPaceSec > 0) "${VdotCalculator.formatPace(avgPaceSec)} /km" else "--:--",
                                subtext = "Aerobic threshold",
                                tint = NeonGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = DarkOutline)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatTile(
                                label = "CURRENT VDOT",
                                value = String.format("%.1f", currentVdot),
                                subtext = if (vdotDelta > 0) "+${String.format("%.1f", vdotDelta)} fitness gain" else "Base calibrated",
                                tint = NeonGreen
                            )
                            StatTile(
                                label = "AVG HEART RATE",
                                value = if (avgHr > 0) "$avgHr BPM" else "--",
                                subtext = "Cadence: ${if (avgCadence > 0) "$avgCadence SPM" else "--"}",
                                tint = ElectricAmber
                            )
                        }
                    }
                }
            }

            // METRIC FILTER TABS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetricViewMode.values().forEach { mode ->
                        val isSelected = selectedMetric == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMetric = mode },
                            label = { Text(mode.label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonGreen,
                                selectedLabelColor = Color.Black,
                                containerColor = DarkSurface,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) NeonGreen else DarkOutline
                            )
                        )
                    }
                }
            }

            // SECTION 1: VDOT & AEROBIC CAPACITY OVER TIME
            if (selectedMetric == MetricViewMode.ALL || selectedMetric == MetricViewMode.VDOT) {
                item {
                    val vdotPoints = remember(filteredRuns, currentVdot, startingVdot) {
                        buildVdotHistoryPoints(filteredRuns, startingVdot, currentVdot)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("VDOT & AEROBIC PROGRESSION", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    Text("Jack Daniels physiological aerobic score over time", fontSize = 11.sp, color = TextSecondary)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NeonGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${String.format("%.1f", currentVdot)} VDOT",
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = NeonGreen,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            MetricLineChart(
                                points = vdotPoints,
                                lineColor = NeonGreen,
                                unit = "VDOT",
                                isPace = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = DarkOutline)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Race Time Equivalents Card
                            Text("EQUIVALENT RACE TARGETS (CURRENT VDOT)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrightCyan)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                RaceTargetPill("5K", VdotCalculator.predictRaceTimeSeconds(currentVdot, 5000.0))
                                RaceTargetPill("10K", VdotCalculator.predictRaceTimeSeconds(currentVdot, 10000.0))
                                RaceTargetPill("Half", VdotCalculator.predictRaceTimeSeconds(currentVdot, 21097.5))
                                RaceTargetPill("Marathon", VdotCalculator.predictRaceTimeSeconds(currentVdot, 42195.0))
                            }
                        }
                    }
                }
            }

            // SECTION 2: PACE PROGRESSION OVER TIME
            if (selectedMetric == MetricViewMode.ALL || selectedMetric == MetricViewMode.PACE) {
                item {
                    val pacePoints = remember(filteredRuns) {
                        filteredRuns.filter { it.averagePaceSecPerKm in 180.0..600.0 }.map { run ->
                            val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(run.startTimeMillis))
                            Pair(dateStr, run.averagePaceSecPerKm)
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("PACE OVER TIME", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    Text("Average workout pace progression (lower is faster)", fontSize = 11.sp, color = TextSecondary)
                                }
                                if (pacePoints.isNotEmpty()) {
                                    Text(
                                        text = "${VdotCalculator.formatPace(pacePoints.last().second)} /km",
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = BrightCyan,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (pacePoints.size >= 2) {
                                MetricLineChart(
                                    points = pacePoints,
                                    lineColor = BrightCyan,
                                    unit = "min/km",
                                    isPace = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                )
                            } else {
                                EmptyChartPlaceholder("Complete at least 2 runs to display pace trends over time")
                            }
                        }
                    }
                }
            }

            // SECTION 3: WEEKLY VOLUME & DISTANCE BAR CHART
            if (selectedMetric == MetricViewMode.ALL || selectedMetric == MetricViewMode.VOLUME) {
                item {
                    val weeklyBars = remember(filteredRuns) {
                        buildWeeklyMileageBars(filteredRuns)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("WEEKLY MILEAGE & VOLUME", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    Text("Kilometers logged per week over time", fontSize = 11.sp, color = TextSecondary)
                                }
                                Text(
                                    text = String.format("%.1f km", totalDistanceMeters / 1000.0),
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = ElectricAmber,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (weeklyBars.isNotEmpty()) {
                                WeeklyMileageBarChart(
                                    weeklyData = weeklyBars,
                                    targetKm = profile.weeklyMileageBaselineKm,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                )
                            } else {
                                EmptyChartPlaceholder("No workouts recorded in this time range")
                            }
                        }
                    }
                }
            }

            // SECTION 4: HEART RATE & AEROBIC EFFICIENCY
            if (selectedMetric == MetricViewMode.ALL || selectedMetric == MetricViewMode.HEART_RATE) {
                item {
                    val hrPoints = remember(filteredRuns) {
                        filteredRuns.filter { it.averageHeartRateBpm > 40 }.map { run ->
                            val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(run.startTimeMillis))
                            Pair(dateStr, run.averageHeartRateBpm.toDouble())
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("CARDIOVASCULAR & HEART RATE", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    Text("Heart rate response & Zone 2 Fat Burn efficiency", fontSize = 11.sp, color = TextSecondary)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ElectricAmber.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Zone 2: ${profile.zone2MinBpm}–${profile.zone2MaxBpm}",
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricAmber,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (hrPoints.size >= 2) {
                                MetricLineChart(
                                    points = hrPoints,
                                    lineColor = VividRed,
                                    unit = "BPM",
                                    isPace = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                )
                            } else {
                                EmptyChartPlaceholder("Pair your smartwatch or chest strap during runs to record heart rate trends")
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = DarkOutline)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Zone 2 Pacing Window", fontSize = 11.sp, color = TextSecondary)
                                    Text("${profile.zone2MinBpm} to ${profile.zone2MaxBpm} BPM", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Cadence Target", fontSize = 11.sp, color = TextSecondary)
                                    Text("${profile.cadenceTargetBpm} SPM", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrightCyan)
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 5: PERSONAL RECORDS & MILESTONES
            item {
                val prs = remember(allRuns) { calculatePersonalRecords(allRuns) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = ElectricAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PERSONAL RECORDS & MILESTONES", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            RecordItem("Longest Run", prs.longestDistance)
                            RecordItem("Fastest Pace", prs.fastestPace)
                            RecordItem("Best VDOT", prs.bestVdot)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    subtext: String,
    tint: Color
) {
    Column {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = tint)
        Text(text = subtext, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
fun RaceTargetPill(name: String, timeSec: Long) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, DarkOutline, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Text(
                text = VdotCalculator.formatDuration(timeSec),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun RecordItem(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 11.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = BrightCyan)
    }
}

@Composable
fun EmptyChartPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Native Jetpack Compose Canvas Line & Area Chart for metric progression over time.
 */
@Composable
fun MetricLineChart(
    points: List<Pair<String, Double>>,
    lineColor: Color,
    unit: String,
    isPace: Boolean,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val values = points.map { it.second }
    val minValue = values.minOrNull() ?: 0.0
    val maxValue = values.maxOrNull() ?: 1.0
    val range = (maxValue - minValue).coerceAtLeast(0.01)

    Column(modifier = modifier) {
        // High & Low legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isPace) "Best: ${VdotCalculator.formatPace(minValue)}" else "Peak: ${String.format("%.1f", maxValue)} $unit",
                fontSize = 10.sp,
                color = TextMuted
            )
            Text(
                text = if (isPace) "Base: ${VdotCalculator.formatPace(maxValue)}" else "Min: ${String.format("%.1f", minValue)} $unit",
                fontSize = 10.sp,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val width = size.width
            val height = size.height

            // Horizontal grid lines
            drawLine(
                color = DarkOutline,
                start = Offset(0f, 0f),
                end = Offset(width, 0f),
                strokeWidth = 1f
            )
            drawLine(
                color = DarkOutline,
                start = Offset(0f, height / 2f),
                end = Offset(width, height / 2f),
                strokeWidth = 1f
            )
            drawLine(
                color = DarkOutline,
                start = Offset(0f, height),
                end = Offset(width, height),
                strokeWidth = 1f
            )

            val stepX = if (points.size > 1) width / (points.size - 1) else width
            val strokePath = Path()
            val fillPath = Path()

            val offsets = points.mapIndexed { index, pair ->
                val x = index * stepX
                // If pace, lower seconds = higher position on chart (faster is higher)
                val normalized = if (isPace) {
                    ((maxValue - pair.second) / range).toFloat()
                } else {
                    ((pair.second - minValue) / range).toFloat()
                }
                val y = height - (normalized * (height - 24f) + 12f)
                Offset(x, y)
            }

            offsets.forEachIndexed { i, offset ->
                if (i == 0) {
                    strokePath.moveTo(offset.x, offset.y)
                    fillPath.moveTo(offset.x, height)
                    fillPath.lineTo(offset.x, offset.y)
                } else {
                    strokePath.lineTo(offset.x, offset.y)
                    fillPath.lineTo(offset.x, offset.y)
                }
            }

            if (offsets.isNotEmpty()) {
                fillPath.lineTo(offsets.last().x, height)
                fillPath.close()

                // Draw gradient area underneath
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.35f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                // Draw line
                drawPath(
                    path = strokePath,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Draw point nodes
                offsets.forEach { offset ->
                    drawCircle(color = DarkBackground, radius = 5.dp.toPx(), center = offset)
                    drawCircle(color = lineColor, radius = 3.dp.toPx(), center = offset)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Date labels (start, middle, end)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(points.first().first, fontSize = 10.sp, color = TextMuted)
            if (points.size > 2) {
                Text(points[points.size / 2].first, fontSize = 10.sp, color = TextMuted)
            }
            Text(points.last().first, fontSize = 10.sp, color = TextMuted)
        }
    }
}

/**
 * Native Weekly Mileage Bar Chart.
 */
@Composable
fun WeeklyMileageBarChart(
    weeklyData: List<Pair<String, Double>>,
    targetKm: Double,
    modifier: Modifier = Modifier
) {
    val maxKm = (weeklyData.maxOfOrNull { it.second } ?: targetKm).coerceAtLeast(targetKm * 1.1)

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val width = size.width
            val height = size.height
            val barCount = weeklyData.size
            if (barCount == 0) return@Canvas

            val slotWidth = width / barCount
            val barWidth = (slotWidth * 0.55f).coerceAtMost(36.dp.toPx())

            // Target dashed line
            val targetY = height - ((targetKm / maxKm).toFloat() * height)
            drawLine(
                color = BrightCyan.copy(alpha = 0.5f),
                start = Offset(0f, targetY),
                end = Offset(width, targetY),
                strokeWidth = 2f
            )

            weeklyData.forEachIndexed { index, pair ->
                val barHeight = ((pair.second / maxKm).toFloat() * (height - 16f)).coerceAtLeast(4f)
                val left = (index * slotWidth) + (slotWidth - barWidth) / 2f
                val top = height - barHeight

                drawRoundRect(
                    color = if (pair.second >= targetKm) NeonGreen else ElectricAmber,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weeklyData.forEach { (weekLabel, km) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(String.format("%.0f", km), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(weekLabel, fontSize = 9.sp, color = TextMuted)
                }
            }
        }
    }
}

// Data helper helpers
private fun buildVdotHistoryPoints(
    runs: List<RecordedRun>,
    startingVdot: Double,
    currentVdot: Double
): List<Pair<String, Double>> {
    val result = mutableListOf<Pair<String, Double>>()

    if (runs.isEmpty()) {
        val now = System.currentTimeMillis()
        val d1 = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(now - 14L * 86400000L))
        val d2 = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(now))
        return listOf(Pair(d1, startingVdot), Pair(d2, currentVdot))
    }

    var runningVdot = startingVdot
    runs.forEach { run ->
        val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(run.startTimeMillis))
        val proposal = run.adaptationProposal
        if (proposal != null && proposal.proposedVdot > 0) {
            runningVdot = proposal.proposedVdot
        } else if (run.distanceMeters > 3000 && run.durationSeconds > 600) {
            val runVdot = VdotCalculator.calculateVdot(run.distanceMeters, run.durationSeconds)
            if (runVdot in 25.0..80.0) {
                // Moving average calibration
                runningVdot = (runningVdot * 0.8) + (runVdot * 0.2)
            }
        }
        result.add(Pair(dateStr, (runningVdot * 10.0).roundToInt() / 10.0))
    }
    return result
}

private fun buildWeeklyMileageBars(runs: List<RecordedRun>): List<Pair<String, Double>> {
    val cal = Calendar.getInstance()
    val weeklyMap = mutableMapOf<Int, Double>()

    for (run in runs) {
        cal.timeInMillis = run.startTimeMillis
        val weekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
        val km = run.distanceMeters / 1000.0
        weeklyMap[weekOfYear] = (weeklyMap[weekOfYear] ?: 0.0) + km
    }

    if (weeklyMap.isEmpty()) {
        return listOf(Pair("Wk 1", 18.0), Pair("Wk 2", 22.5), Pair("Wk 3", 26.0), Pair("Current", 28.5))
    }

    return weeklyMap.entries.sortedBy { it.key }.takeLast(6).mapIndexed { idx, entry ->
        Pair("Wk ${idx + 1}", (entry.value * 10.0).roundToInt() / 10.0)
    }
}

data class PersonalRecords(
    val longestDistance: String,
    val fastestPace: String,
    val bestVdot: String
)

private fun calculatePersonalRecords(runs: List<RecordedRun>): PersonalRecords {
    if (runs.isEmpty()) {
        return PersonalRecords(longestDistance = "--", fastestPace = "--", bestVdot = "--")
    }

    val maxDist = runs.maxOfOrNull { it.distanceMeters } ?: 0.0
    val bestPace = runs.filter { it.averagePaceSecPerKm > 100 }.minOfOrNull { it.averagePaceSecPerKm } ?: 0.0
    val bestVdot = runs.mapNotNull { it.adaptationProposal?.proposedVdot }.maxOrNull() ?: 42.0

    return PersonalRecords(
        longestDistance = String.format("%.1f km", maxDist / 1000.0),
        fastestPace = if (bestPace > 0) "${VdotCalculator.formatPace(bestPace)}/km" else "--",
        bestVdot = String.format("%.1f", bestVdot)
    )
}
