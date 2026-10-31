package com.strideflow.app.ui.postrun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
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
import com.strideflow.app.engine.NutritionEngine
import com.strideflow.app.engine.VdotCalculator
import com.strideflow.app.model.Workout
import com.strideflow.app.model.WorkoutType
import com.strideflow.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostRunScreen(
    runId: String,
    onReturnToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember { LocalStorageManager(context) }
    val profile = remember { storage.loadProfile() }
    val runs = remember { storage.loadRuns() }
    val run = remember(runId) { runs.firstOrNull { it.id == runId } }
    var plan by remember { mutableStateOf(storage.loadActivePlan()) }

    var adaptationProposal by remember { mutableStateOf(run?.adaptationProposal) }
    var adaptationStatusText by remember { mutableStateOf<String?>(null) }

    val mockWorkout = remember(run) {
        Workout(
            id = run?.workoutId ?: "free_run",
            dayIndex = 0,
            weekIndex = 1,
            title = "Completed Session",
            type = if ((run?.distanceMeters ?: 0.0) >= 12000) WorkoutType.LONG_RUN else WorkoutType.THRESHOLD_TEMPO,
            targetDistanceMeters = run?.distanceMeters ?: 5000.0,
            targetPaceSecPerKm = run?.targetPaceSecPerKm ?: (run?.averagePaceSecPerKm ?: 300.0),
            description = "Completed workout"
        )
    }

    val nutrition = remember(mockWorkout, profile) {
        NutritionEngine.getAdviceForWorkout(
            workout = mockWorkout,
            runnerWeightKg = profile.weightKg,
            dietaryPreference = profile.dietaryPreference
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WORKOUT SUMMARY", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
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
            // Big Stats Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("DISTANCE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text(
                            text = String.format("%.2f km", (run?.distanceMeters ?: 0.0) / 1000.0),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = NeonGreen
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TIME", fontSize = 11.sp, color = TextMuted)
                                Text(
                                    text = VdotCalculator.formatDuration(run?.durationSeconds ?: 0),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("AVG PACE", fontSize = 11.sp, color = TextMuted)
                                Text(
                                    text = "${VdotCalculator.formatPace(run?.averagePaceSecPerKm ?: 0.0)}/km",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = BrightCyan
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("COMPLIANCE", fontSize = 11.sp, color = TextMuted)
                                Text(
                                    text = "${run?.complianceScorePercent ?: 100}%",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = ElectricAmber
                                )
                            }
                        }
                    }
                }
            }

            // GPS Route Map
            if (run?.points?.isNotEmpty() == true) {
                item {
                    Text("GPS ROUTE MAP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    com.strideflow.app.ui.map.RouteMapView(
                        points = run.points,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    )
                }
            }

            // Adaptive Plan Coaching Proposal (If triggered)
            if (adaptationProposal != null) {
                val proposal = adaptationProposal!!
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonGreen)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = NeonGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ADAPTIVE COACH PROPOSAL",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    letterSpacing = 1.sp,
                                    color = NeonGreen
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = proposal.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = proposal.explanationText,
                                fontSize = 14.sp,
                                color = TextSecondary,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("VDOT Calibration:", fontSize = 13.sp, color = TextMuted)
                                Text(
                                    text = "${proposal.previousVdot}  ➔  ${proposal.proposedVdot}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonGreen
                                )
                            }

                            if (adaptationStatusText != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = adaptationStatusText!!,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrightCyan
                                )
                            } else {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (plan != null) {
                                                val updated = AdaptiveCoach.applyProposalToPlan(plan!!, proposal)
                                                storage.savePlan(updated)
                                                plan = updated
                                                storage.saveProfile(profile.copy(vdot = proposal.proposedVdot))
                                                adaptationStatusText = "✓ Plan adjusted & new paces calibrated!"
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Accept", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            adaptationStatusText = "Original plan retained."
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Keep Plan", color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Post-Run Recovery Nutrition Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Restaurant, contentDescription = null, tint = ElectricAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "POST-RUN RECOVERY NUTRITION",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp,
                                color = ElectricAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Dietary Profile: ${profile.dietaryPreference.displayName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = nutrition.postRunRecoveryCarbProtein,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = nutrition.postRunHydrationElectrolytes,
                            fontSize = 14.sp,
                            color = TextSecondary,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Recommended Recovery Fuel:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrightCyan)
                        nutrition.recipeIdeas.filter { it.startsWith("Post") || it.startsWith("Recovery") }.forEach { item ->
                            Text("• $item", fontSize = 13.sp, color = TextPrimary, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }

            // Splits Breakdown
            if (run?.splits?.isNotEmpty() == true) {
                item {
                    Text("KILOMETER SPLITS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                }

                items(run.splits) { split ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("KM ${split.splitNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                        Text(
                            text = "${VdotCalculator.formatPace(split.averagePaceSecPerKm)} /km",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = BrightCyan
                        )
                        Text(
                            text = VdotCalculator.formatDuration(split.durationSeconds),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Back to Dashboard Button
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onReturnToDashboard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrightCyan)
                ) {
                    Text("RETURN TO DASHBOARD", fontWeight = FontWeight.Black, color = Color.Black)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
