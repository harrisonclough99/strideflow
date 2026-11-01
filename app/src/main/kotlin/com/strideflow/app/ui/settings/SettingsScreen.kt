package com.strideflow.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.strideflow.app.model.DietaryPreference
import com.strideflow.app.model.UnitSystem
import com.strideflow.app.service.BleHeartRateManager
import com.strideflow.app.service.BleStatus
import com.strideflow.app.service.NutritionNotificationScheduler
import com.strideflow.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember { LocalStorageManager(context) }
    var profile by remember { mutableStateOf(storage.loadProfile()) }

    var maxHrText by remember { mutableStateOf("${profile.maxHeartRateBpm}") }
    var restHrText by remember { mutableStateOf("${profile.restingHeartRateBpm}") }

    // Smartwatch BLE & Paired Device Manager
    val bleManager = remember { BleHeartRateManager(context) }
    val bleStatus by bleManager.status.collectAsState()
    val discoveredDevices by bleManager.discoveredDevices.collectAsState()
    val liveHr by bleManager.currentHeartRate.collectAsState()

    var pairedDevices by remember { mutableStateOf(bleManager.getPairedSmartwatches()) }
    var isUnbondedScanOpen by remember { mutableStateOf(false) }

    val blePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        pairedDevices = bleManager.getPairedSmartwatches()
        bleManager.autoConnectPairedSmartwatch(profile.pairedBleDeviceAddress)
    }

    // On entry, fetch bonded devices and connect to paired watch directly without scanning
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blePermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BODY_SENSORS
                )
            )
        } else {
            pairedDevices = bleManager.getPairedSmartwatches()
            bleManager.autoConnectPairedSmartwatch(profile.pairedBleDeviceAddress)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            bleManager.stopScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS & SENSORS", fontWeight = FontWeight.Black, fontSize = 16.sp) },
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // DIRECT PAIRED SMARTWATCH & HEART RATE STREAM
            item {
                Text("PAIRED SMARTWATCH & HEART RATE STREAM", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (liveHr > 0 || profile.pairedBleDeviceAddress != null) NeonGreen else DarkOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Watch,
                                    contentDescription = null,
                                    tint = if (liveHr > 0) NeonGreen else BrightCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = profile.pairedBleDeviceName ?: (pairedDevices.firstOrNull { it.isSmartwatch }?.name ?: "Paired Smartwatch"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (bleStatus == BleStatus.CONNECTED) "Connected (Reading live heart rate)"
                                        else if (bleStatus == BleStatus.CONNECTING) "Connecting to paired watch..."
                                        else "Paired at phone OS level",
                                        fontSize = 12.sp,
                                        color = if (bleStatus == BleStatus.CONNECTED) NeonGreen else TextSecondary
                                    )
                                }
                            }

                            if (liveHr > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurface)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = VividRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$liveHr BPM",
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = NeonGreen,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Device List from bonded devices
                        if (pairedDevices.isNotEmpty()) {
                            Text(
                                text = "Detected Paired Watches / Devices:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            pairedDevices.forEach { dev ->
                                val isCurrentTarget = profile.pairedBleDeviceAddress == dev.address || (profile.pairedBleDeviceAddress == null && dev.isSmartwatch)
                                val isConnected = bleStatus == BleStatus.CONNECTED && isCurrentTarget

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrentTarget) DarkSurface else DarkSurfaceElevated)
                                        .border(1.dp, if (isCurrentTarget) BrightCyan else DarkOutline, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(dev.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                            if (dev.isSmartwatch) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(BrightCyan.copy(alpha = 0.2f))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text("WATCH", fontSize = 9.sp, fontWeight = FontWeight.Black, color = BrightCyan)
                                                }
                                            }
                                        }
                                        Text(dev.address, fontSize = 11.sp, color = TextMuted)
                                    }

                                    if (isConnected) {
                                        Text("STREAMING ✓", fontSize = 11.sp, color = NeonGreen, fontWeight = FontWeight.Black)
                                    } else {
                                        Button(
                                            onClick = {
                                                bleManager.connect(dev.address)
                                                profile = profile.copy(
                                                    pairedBleDeviceAddress = dev.address,
                                                    pairedBleDeviceName = dev.name
                                                )
                                                storage.saveProfile(profile)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = BrightCyan),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Connect", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "No bonded watches detected yet.",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Your smartwatch connects to your phone via Android Bluetooth settings or its companion app (e.g. Pixel Watch, Wear OS, Galaxy Wearable, Garmin). Once paired with your phone, StrideFlow automatically accesses its heart rate stream.",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pairedDevices = bleManager.getPairedSmartwatches()
                                    bleManager.autoConnectPairedSmartwatch(profile.pairedBleDeviceAddress)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refresh", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1.4f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Phone Bluetooth", fontSize = 12.sp)
                            }

                            if (profile.pairedBleDeviceAddress != null || bleStatus == BleStatus.CONNECTED) {
                                OutlinedButton(
                                    onClick = {
                                        bleManager.disconnect()
                                        profile = profile.copy(pairedBleDeviceAddress = null, pairedBleDeviceName = null)
                                        storage.saveProfile(profile)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Unlink", color = VividRed, fontSize = 12.sp)
                                }
                            }
                        }

                        // Collapsible fallback for unbonded standalone chest straps
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isUnbondedScanOpen = !isUnbondedScanOpen }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Unbonded HRM / Chest Strap Fallback", fontSize = 12.sp, color = TextMuted)
                            Icon(
                                imageVector = if (isUnbondedScanOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = isUnbondedScanOpen) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Button(
                                    onClick = {
                                        if (bleStatus == BleStatus.SCANNING) bleManager.stopScan() else bleManager.startScan()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface)
                                ) {
                                    Text(if (bleStatus == BleStatus.SCANNING) "Stop Scanning" else "Scan for Unbonded Sensor", fontSize = 12.sp)
                                }

                                if (discoveredDevices.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    discoveredDevices.forEach { dev ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(DarkSurface)
                                                .clickable {
                                                    bleManager.connect(dev.address)
                                                    profile = profile.copy(pairedBleDeviceAddress = dev.address, pairedBleDeviceName = dev.name)
                                                    storage.saveProfile(profile)
                                                    isUnbondedScanOpen = false
                                                }
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(dev.name, fontSize = 12.sp, color = TextPrimary)
                                            Text("Connect ➔", fontSize = 12.sp, color = BrightCyan)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // AUTO-PAUSE & COUNTDOWN SETTINGS
            item {
                Text("RUNNING BEHAVIOR & AUTOMATION", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Pause when Stopped", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text("Automatically pauses after 10s stationary and resumes upon moving", fontSize = 12.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = profile.isAutoPauseEnabled,
                                onCheckedChange = { checked ->
                                    profile = profile.copy(isAutoPauseEnabled = checked)
                                    storage.saveProfile(profile)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = DarkOutline)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pace Stability (Garmin 5s Rounding)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text("Rounds live pace to 5s (5:00, 5:05) like Garmin watches for rock-steady stability without numerical flutter", fontSize = 12.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = profile.isPaceRounding5sEnabled,
                                onCheckedChange = { checked ->
                                    profile = profile.copy(isPaceRounding5sEnabled = checked)
                                    storage.saveProfile(profile)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = DarkOutline)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Start Countdown", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text("Countdown buffer before GPS starts", fontSize = 12.sp, color = TextSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(3, 5, 10).forEach { sec ->
                                    val isSelected = profile.countdownDurationSeconds == sec
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) NeonGreen else DarkSurface)
                                            .clickable {
                                                profile = profile.copy(countdownDurationSeconds = sec)
                                                storage.saveProfile(profile)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${sec}s", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.Black else TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // NOTIFICATIONS & NUTRITION ALERTS + INTERACTIVE TIME SCROLL WHEEL
            item {
                Text("NOTIFICATIONS & SCHEDULED ALERTS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Nutrition Notifications Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Nutrition & Fueling Notifications", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text("Pre-run meal & top-up alerts based on scheduled distance (30-60m for 5K, 90m for 10K, 3h for Half/Full)", fontSize = 12.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = profile.isNutritionNotificationsEnabled,
                                onCheckedChange = { checked ->
                                    profile = profile.copy(isNutritionNotificationsEnabled = checked)
                                    storage.saveProfile(profile)
                                    NutritionNotificationScheduler.syncNotifications(
                                        context, storage.loadActivePlan(), profile
                                    )
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = DarkOutline)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Run Reminder Notifications Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Daily Run Reminder", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text("Sends a notification 30 minutes before your scheduled workout", fontSize = 12.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = profile.isRunReminderNotificationsEnabled,
                                onCheckedChange = { checked ->
                                    profile = profile.copy(isRunReminderNotificationsEnabled = checked)
                                    storage.saveProfile(profile)
                                    NutritionNotificationScheduler.syncNotifications(
                                        context, storage.loadActivePlan(), profile
                                    )
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = DarkOutline)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Interactive Scroll Wheel for Target Workout Time (Hours & Minutes)
                        TimeWheelPicker(
                            hour = profile.scheduledRunHour,
                            minute = profile.scheduledRunMinute,
                            onTimeChanged = { newH, newM ->
                                profile = profile.copy(scheduledRunHour = newH, scheduledRunMinute = newM)
                                storage.saveProfile(profile)
                                NutritionNotificationScheduler.syncNotifications(
                                    context, storage.loadActivePlan(), profile
                                )
                            }
                        )
                    }
                }
            }

            // ZONE 2 FAT BURN & HEART RATE PARAMETERS
            item {
                Text("ZONE 2 FAT BURN & HEART RATE ZONES", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = maxHrText,
                                onValueChange = {
                                    maxHrText = it
                                    it.toIntOrNull()?.let { m ->
                                        profile = profile.copy(maxHeartRateBpm = m)
                                        storage.saveProfile(profile)
                                    }
                                },
                                label = { Text("Max HR (BPM)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = restHrText,
                                onValueChange = {
                                    restHrText = it
                                    it.toIntOrNull()?.let { r ->
                                        profile = profile.copy(restingHeartRateBpm = r)
                                        storage.saveProfile(profile)
                                    }
                                },
                                label = { Text("Rest HR (BPM)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurface)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Zone 2 Fat Burn Window: ${profile.zone2MinBpm} – ${profile.zone2MaxBpm} BPM",
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // UNITS & DIET
            item {
                Text("MEASUREMENT UNITS & DIET", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UnitSystem.values().forEach { unit ->
                        val isSelected = profile.unitSystem == unit
                        Button(
                            onClick = {
                                profile = profile.copy(unitSystem = unit)
                                storage.saveProfile(profile)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) NeonGreen else DarkSurfaceElevated
                            )
                        ) {
                            Text(
                                text = "${unit.name} (${unit.distanceLabel})",
                                color = if (isSelected) Color.Black else TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DietaryPreference.values().forEach { diet ->
                        val isSelected = profile.dietaryPreference == diet
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    profile = profile.copy(dietaryPreference = diet)
                                    storage.saveProfile(profile)
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) DarkSurfaceElevated else DarkSurface),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, BrightCyan) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(diet.displayName, color = if (isSelected) BrightCyan else TextPrimary, fontSize = 14.sp)
                                if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = BrightCyan)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text("SAVE & RETURN", fontWeight = FontWeight.Black, color = Color.Black)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
