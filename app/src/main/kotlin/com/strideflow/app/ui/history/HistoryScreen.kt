package com.strideflow.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
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
import com.strideflow.app.engine.VdotCalculator
import com.strideflow.app.model.RecordedRun
import com.strideflow.app.ui.common.AppBottomNavigationBar
import com.strideflow.app.ui.common.MainNavTab
import com.strideflow.app.ui.map.RouteMapView
import com.strideflow.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onSelectRun: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateToCoach: () -> Unit = onBack,
    onNavigateToPlan: () -> Unit = {},
    onNavigateToPerformance: () -> Unit = {}
) {
    val context = LocalContext.current
    val storage = remember { LocalStorageManager(context) }
    var runs by remember { mutableStateOf(storage.loadRuns()) }
    val dateFormatter = remember { SimpleDateFormat("EEE, MMM d, yyyy · HH:mm", Locale.getDefault()) }

    var runToDelete by remember { mutableStateOf<RecordedRun?>(null) }
    var expandedRunMapId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RUN HISTORY", fontWeight = FontWeight.Black, fontSize = 16.sp) },
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
                currentTab = MainNavTab.HISTORY,
                onNavigateToCoach = onNavigateToCoach,
                onNavigateToPlan = onNavigateToPlan,
                onNavigateToPerformance = onNavigateToPerformance,
                onNavigateToHistory = { /* Already on History */ }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (runs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recorded runs yet.\nComplete your first workout to see analytics & maps!",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "TOTAL RECORDED RUNS: ${runs.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                }

                items(runs, key = { it.id }) { run ->
                    val isMapExpanded = expandedRunMapId == run.id

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Row: Date & Actions (Map Toggle, Delete)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateFormatter.format(Date(run.startTimeMillis)),
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Map Toggle Button
                                    if (run.points.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                expandedRunMapId = if (isMapExpanded) null else run.id
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Map,
                                                contentDescription = "Toggle Map",
                                                tint = if (isMapExpanded) NeonGreen else TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }

                                    // Delete Run Button
                                    IconButton(
                                        onClick = { runToDelete = run },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Run",
                                            tint = VividRed.copy(alpha = 0.85f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Main Run Metrics Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectRun(run.id) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = String.format("%.2f km", run.distanceMeters / 1000.0),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Duration: ${VdotCalculator.formatDuration(run.durationSeconds)}",
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${VdotCalculator.formatPace(run.averagePaceSecPerKm)} /km",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = BrightCyan
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (run.averageHeartRateBpm > 0) {
                                            Icon(Icons.Default.Favorite, contentDescription = null, tint = VividRed, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "${run.averageHeartRateBpm} bpm  ·  ",
                                                fontSize = 12.sp,
                                                color = TextMuted
                                            )
                                        }
                                        Text(
                                            text = "${run.complianceScorePercent}% Match",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonGreen
                                        )
                                    }
                                }
                            }

                            // Expandable Route Map Preview
                            AnimatedVisibility(visible = isMapExpanded) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    RouteMapView(
                                        points = run.points,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tap metrics above to see full split breakdown & nutrition review",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
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

        // Delete Confirmation Dialog
        if (runToDelete != null) {
            AlertDialog(
                onDismissRequest = { runToDelete = null },
                title = { Text("Delete Recorded Run?", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Text(
                        "Are you sure you want to permanently delete this ${String.format("%.2f km", runToDelete!!.distanceMeters / 1000.0)} run? This will remove all GPS coordinates and analytics.",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val id = runToDelete!!.id
                            storage.deleteRun(id)
                            runs = storage.loadRuns()
                            runToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VividRed)
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { runToDelete = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurfaceElevated
            )
        }
    }
}
