package com.strideflow.app.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.strideflow.app.engine.VdotCalculator
import com.strideflow.app.model.TrainingPlan
import com.strideflow.app.model.UserProfile
import com.strideflow.app.model.Workout
import com.strideflow.app.model.WorkoutType
import java.util.Calendar

object NutritionNotificationScheduler {

    /**
     * Reschedules all nutrition and workout reminders according to current plan and preferences.
     */
    @SuppressLint("ScheduleExactAlarm")
    fun syncNotifications(context: Context, plan: TrainingPlan?, profile: UserProfile) {
        cancelAllAlarms(context)

        plan ?: return
        val currentWeek = plan.currentWeek
        val workouts = plan.workouts.filter { it.weekIndex == currentWeek && !it.isCompleted && !it.isMissed && it.type != WorkoutType.REST }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val currentDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0 .. Sunday = 6

        for (workout in workouts) {
            // Days difference from current day
            val dayDiff = workout.dayIndex - currentDayOfWeek
            if (dayDiff < 0) continue // Skip days in past this week

            // Calculate target workout time on that day
            val workoutCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayDiff)
                set(Calendar.HOUR_OF_DAY, profile.scheduledRunHour)
                set(Calendar.MINUTE, profile.scheduledRunMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val runTimeMillis = workoutCal.timeInMillis
            if (runTimeMillis <= now) continue

            val baseId = (workout.dayIndex * 100) + (workout.weekIndex * 10)

            // 1. Run Reminder Notification
            if (profile.isRunReminderNotificationsEnabled) {
                val reminderTime = runTimeMillis - (30 * 60 * 1000L) // 30 min before
                if (reminderTime > now) {
                    val distKm = String.format("%.1f km", workout.targetDistanceMeters / 1000.0)
                    val pace = VdotCalculator.formatPace(workout.targetPaceSecPerKm)
                    scheduleAlarm(
                        context = context,
                        alarmManager = alarmManager,
                        triggerTimeMillis = reminderTime,
                        notificationId = baseId + 1,
                        title = "Upcoming Run: ${workout.title}",
                        message = "$distKm scheduled today. Target pace: $pace/km. Lace up!",
                        channelId = NotificationReceiver.CHANNEL_REMINDERS
                    )
                }
            }

            // 2. Nutrition & Fueling Notifications
            if (profile.isNutritionNotificationsEnabled) {
                scheduleNutritionAlarmsForWorkout(
                    context = context,
                    alarmManager = alarmManager,
                    workout = workout,
                    runTimeMillis = runTimeMillis,
                    baseId = baseId + 2,
                    now = now
                )
            }
        }
    }

    private fun scheduleNutritionAlarmsForWorkout(
        context: Context,
        alarmManager: AlarmManager,
        workout: Workout,
        runTimeMillis: Long,
        baseId: Int,
        now: Long
    ) {
        val distanceKm = workout.targetDistanceMeters / 1000.0

        when {
            // Tier 4: Marathon & Ultra (> 25 km)
            distanceKm >= 25.0 -> {
                // 3.5 hours prior
                val preMealTime = runTimeMillis - (210 * 60 * 1000L)
                if (preMealTime > now) {
                    scheduleAlarm(
                        context = context,
                        alarmManager = alarmManager,
                        triggerTimeMillis = preMealTime,
                        notificationId = baseId,
                        title = "Pre-Run Breakfast (Long Run / Marathon)",
                        message = "Time for your high-carb, low-residue meal (white toast with honey/jam, bagel, or cereal with banana).",
                        channelId = NotificationReceiver.CHANNEL_NUTRITION
                    )
                }
                // 35 min prior
                val primerTime = runTimeMillis - (35 * 60 * 1000L)
                if (primerTime > now) {
                    scheduleAlarm(
                        context = context,
                        alarmManager = alarmManager,
                        triggerTimeMillis = primerTime,
                        notificationId = baseId + 1,
                        title = "Pre-Run Fueling Prep",
                        message = "Final top-up: energy chew + sip of electrolyte drink. Pack 60–75g carbs/hour for your run.",
                        channelId = NotificationReceiver.CHANNEL_NUTRITION
                    )
                }
            }

            // Tier 3: Half Marathon (15 km to < 25 km)
            distanceKm >= 15.0 -> {
                // 3 hours prior
                val preMealTime = runTimeMillis - (180 * 60 * 1000L)
                if (preMealTime > now) {
                    scheduleAlarm(
                        context = context,
                        alarmManager = alarmManager,
                        triggerTimeMillis = preMealTime,
                        notificationId = baseId,
                        title = "Pre-Run Carbohydrate Meal (Half Marathon)",
                        message = "Eat a carb-rich meal (oatmeal, bagel with jam, or toast with honey). Keep fat & fiber low.",
                        channelId = NotificationReceiver.CHANNEL_NUTRITION
                    )
                }
                // 35 min prior
                val primerTime = runTimeMillis - (35 * 60 * 1000L)
                if (primerTime > now) {
                    scheduleAlarm(
                        context = context,
                        alarmManager = alarmManager,
                        triggerTimeMillis = primerTime,
                        notificationId = baseId + 1,
                        title = "Pre-Run Top-Up (Half Marathon)",
                        message = "Small top-up: half a banana or energy chew. Prepare to fuel with 30–60g carbs/hr after min 45.",
                        channelId = NotificationReceiver.CHANNEL_NUTRITION
                    )
                }
            }

            // Tier 2: 10K (7.5 km to < 15 km)
            distanceKm >= 7.5 -> {
                // 90 minutes prior
                val preMealTime = runTimeMillis - (90 * 60 * 1000L)
                if (preMealTime > now) {
                    scheduleAlarm(
                        context = context,
                        alarmManager = alarmManager,
                        triggerTimeMillis = preMealTime,
                        notificationId = baseId,
                        title = "Pre-Run Nutrition (10K)",
                        message = "Eat easily digestible porridge (oatmeal) with banana or toast with jam/honey. Low fiber & low fat.",
                        channelId = NotificationReceiver.CHANNEL_NUTRITION
                    )
                }
            }

            // Tier 1: 5K (< 7.5 km)
            else -> {
                // 45 minutes prior
                val preMealTime = runTimeMillis - (45 * 60 * 1000L)
                if (preMealTime > now) {
                    scheduleAlarm(
                        context = context,
                        alarmManager = alarmManager,
                        triggerTimeMillis = preMealTime,
                        notificationId = baseId,
                        title = "Pre-Run Nutrition (5K)",
                        message = "Light carb snack: single banana or toast with a little honey. Your body already has plenty of stored glycogen for a 5K.",
                        channelId = NotificationReceiver.CHANNEL_NUTRITION
                    )
                }
            }
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerTimeMillis: Long,
        notificationId: Int,
        title: String,
        message: String,
        channelId: String
    ) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(NotificationReceiver.EXTRA_TITLE, title)
            putExtra(NotificationReceiver.EXTRA_MESSAGE, message)
            putExtra(NotificationReceiver.EXTRA_CHANNEL_ID, channelId)
            putExtra(NotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            }
        } catch (e: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        }
    }

    fun cancelAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (i in 0..100) {
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, i, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }
    }
}
