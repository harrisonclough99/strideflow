# StrideFlow 🏃‍♂️⚡

> **100% On-Device Adaptive Running Coach & Workout Tracker for Android (Optimized for Google Pixel 10 Pro)**

StrideFlow is a native Android running companion that performs all computations—physiological VDOT scoring, dynamic plan adjustments, GPS route tracking, and workout-adaptive nutrition advice—**entirely on your device**. It requires no cloud connection, no Wi-Fi, and no account signup, ensuring total privacy and instantaneous performance.

---

## 📱 Download & Install (Google Pixel 10 Pro / Android)

[![Download APK](https://img.shields.io/badge/Download_APK-v1.1.0_(18.7_MB)-00E676?style=for-the-badge&logo=android&logoColor=black)](https://github.com/harrisonclough99/strideflow/releases/download/v1.1.0/StrideFlow-v1.1.0.apk)

- **Direct Download Link**: **[StrideFlow-v1.1.0.apk](https://github.com/harrisonclough99/strideflow/releases/download/v1.1.0/StrideFlow-v1.1.0.apk)** (18.7 MB)
- **GitHub Release Page**: [github.com/harrisonclough99/strideflow/releases/tag/v1.1.0](https://github.com/harrisonclough99/strideflow/releases/tag/v1.1.0)

Tap the download link above directly on your phone to download and install.

---

## 🌟 Key Features

### 1. 🧠 Physiological VDOT Engine & Adaptive Coaching
- **Jack Daniels VDOT Model**: Computes aerobic capacity and calibrated pacing zones:
  - *Easy / Recovery Pace*
  - *Marathon Pace*
  - *Threshold (Tempo) Pace*
  - *Interval Pace*
  - *Repetition Pace*
- **Autonomous Plan Adaptation**: Automatically assesses workout performance against targets. If you consistently exceed or struggle with prescribed paces, StrideFlow proposes calibrated VDOT adjustments.
- **Missed Run Auto-Rescheduling**: If life gets in the way and you miss a workout, the engine detects the gap and safely reschedules quality workouts (Tempo, Intervals, Long Runs) to upcoming rest/easy days without overloading your weekly volume.

### 2. ⌚ Smartwatch & Heart Rate Integration (Direct Paired Access)
- **Zero-Scan Direct Connection**: Reads bonded devices directly from `BluetoothAdapter.bondedDevices`. Because your smartwatch (Pixel Watch, Galaxy Watch, Garmin, etc.) is already paired with your phone at the OS level, StrideFlow connects immediately via low-latency Bluetooth LE GATT (`TRANSPORT_LE`) without draining battery on over-the-air BLE scans.
- **Unified Sensor Bridging**: Listens to standard Bluetooth GATT Heart Rate notifications (`0x180D` / `0x2A37`) and registers Android's `Sensor.TYPE_HEART_RATE` from `SensorManager` to capture heart rate whether delivered via Bluetooth GATT or OS sensor bridging.
- **Zone 2 Fat Burn Workouts**: Karvonen-based target zone calculation (60%–70% heart rate reserve) with real-time HUD dial and audio cues if you drift out of the fat-burning zone.

### 3. 📊 Performance Analytics & Metrics Over Time
- **Unified 4-Tab Bottom Navigation**: Seamlessly switch between Coach, Plan Calendar, Performance Analytics, and History.
- **Time Range Filters**: Filter trends across 7 Days, 30 Days, 12 Weeks, or All Time.
- **VDOT Progression & Race Prediction**: Track aerobic fitness curves over time and view Jack Daniels predicted finish times for 5K, 10K, Half Marathon, and Marathon.
- **Pace Curves & Weekly Volume**: Visual pace progression graphs and weekly mileage bar charts comparing completed volume against target baselines.
- **Cardiovascular Efficiency**: Monitor heart rate response and cadence trends over time.

### 4. 🎯 Realistic Calculated & Customizable Plan Target Paces
- **Scientifically Calibrated Prefilling**: Automatically calculates a realistic target pace and finish time based on your current ability (VDOT) and scheduled plan duration (~4% progressive gain over 8 weeks).
- **Full Customizability**: Adjust target pace or finish time at any point with interactive stepper controls and text inputs. All scheduled workouts in the plan automatically update their target paces.

### 5. 🥗 Distance-Tiered Nutrition & Fueling Protocols
Workout-tailored fueling guidelines for before, during, and after runs:
- **5K (< 7.5 km / ~20–30 min)**: Ample glycogen stored; light carb snack 30–60m prior, water only during, balanced meal within 60m post-run.
- **10K (7.5–15 km / ~45–60+ min)**: Easily digestible meal 1–2h prior (porridge, toast with jam); water or small sips of electrolyte drink; 3:1 or 4:1 carb-to-protein recovery meal.
- **Half Marathon (15–25 km / ~90+ min)**: Carb meal 3h prior + top-up 35m prior; 30–60g carbs/hour starting at minute 45; immediate liquid recovery carbs/protein followed by a nutrient-dense meal.
- **Marathon & Ultra (> 25 km / 3+ hours)**: Full carb-loading 2–3 days prior; breakfast 3–4h prior; scheduled fueling every 30–45m (60–75g carbs/hour); immediate recovery drinks transitioning to sodium and complex carb replenishment.
- **Dietary Preference Filtering**: Omnivore, Vegetarian, Vegan, Gluten-Free, and Low-FODMAP options.

### 6. 🗺️ Offline Route Mapping & GPS Tracking
- **Vector Route Canvas**: High-contrast route map showing full GPS track, start pin, finish pin, and per-kilometer splits without loading external network map tiles.
- **Accidental Run Deletion**: Permanently delete accidental recordings from history with automatic unmarking of linked scheduled plan workouts.
- **Auto-Pause & Countdown**: Automatically pauses when stationary for > 10 seconds; configurable 3s/5s/10s countdown buffer before GPS recording starts.

---

## 🛠️ Project Structure

```
strideflow/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/strideflow/app/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── Navigation.kt
│   │   │   │   ├── data/
│   │   │   │   │   └── LocalStorageManager.kt       # Atomic offline JSON persistence
│   │   │   │   ├── engine/
│   │   │   │   │   ├── VdotCalculator.kt             # Jack Daniels VDOT & pacing zones
│   │   │   │   │   ├── PlanGenerator.kt              # Periodized plan generation
│   │   │   │   │   ├── AdaptiveCoach.kt              # Autonomous plan adaptations & missed runs
│   │   │   │   │   └── NutritionEngine.kt            # Distance-tiered fueling rules
│   │   │   │   ├── model/
│   │   │   │   │   └── Models.kt                     # Data models & enumerations
│   │   │   │   ├── service/
│   │   │   │   │   ├── BleHeartRateManager.kt        # Direct paired smartwatch & BLE GATT
│   │   │   │   │   ├── RunTrackingService.kt         # Persistent foreground GPS service
│   │   │   │   │   ├── NotificationReceiver.kt       # Exact alarm broadcast receiver
│   │   │   │   │   └── NutritionNotificationScheduler.kt # Scheduled exact alarms
│   │   │   │   └── ui/
│   │   │   │       ├── dashboard/                    # Overview & active workout launch
│   │   │   │       ├── plan/                         # Calendar & List view plan manager
│   │   │   │       ├── run/                          # Active HUD, GPS canvas & audio cues
│   │   │   │       ├── postrun/                      # Performance compliance & adaptation
│   │   │   │       ├── history/                      # Recorded runs & route polyline maps
│   │   │   │       ├── settings/                     # Paired smartwatch & time scroll wheels
│   │   │   │       └── theme/                        # OLED dark high-contrast theme
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   │       └── kotlin/com/strideflow/app/
│   │           └── StrideFlowTests.kt                # Unit test verification suite
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml                            # Version catalog
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 📋 Prerequisites

- **Java Development Kit (JDK)**: OpenJDK 17 or higher
- **Android SDK**:
  - `compileSdk = 36`
  - `targetSdk = 36`
  - `minSdk = 26` (Android 8.0 Oreo+)
  - `build-tools = 35.0.0` or `36.0.0`
- **Gradle**: 9.1.0 (managed via included Gradle Wrapper)

---

## 🚀 Setup & Build Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/harrisonclough99/strideflow.git
cd strideflow
```

### 2. Configure Local Environment
Ensure your `JAVA_HOME` points to JDK 17+ and `ANDROID_HOME` (or `local.properties`) points to your Android SDK installation:

**PowerShell**:
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

**Bash**:
```bash
export JAVA_HOME="/path/to/jdk-17"
export ANDROID_HOME="$HOME/Android/Sdk"
```

### 3. Run Unit Tests
```bash
# Windows
.\gradlew.bat testDebugUnitTest

# macOS / Linux
./gradlew testDebugUnitTest
```

### 4. Build Debug APK
```bash
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 5. Install on Google Pixel 10 Pro
Connect your device with USB Debugging enabled, then execute:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔒 100% Offline & Privacy Guarantee

StrideFlow is built from the ground up to be **strictly offline**:
- **Zero Network Permissions**: The application does not declare `INTERNET` permission in its manifest.
- **Zero Cloud SDKs**: No Firebase, no analytics, no external tracking, no ads.
- **Local Persistence**: All workout history, GPS coordinates, and personal physiological metrics are stored exclusively in app-private sandboxed JSON storage on your device.

---

## 📄 License
This project is licensed under the Apache License 2.0.
