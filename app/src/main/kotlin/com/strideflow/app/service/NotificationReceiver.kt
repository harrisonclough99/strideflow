package com.strideflow.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.strideflow.app.MainActivity

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        intent ?: return

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "StrideFlow Reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Time to check your workout!"
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: CHANNEL_REMINDERS
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1001)

        createNotificationChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = if (channelId == CHANNEL_NUTRITION) {
            android.R.drawable.ic_menu_agenda
        } else {
            android.R.drawable.ic_media_play
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(iconRes)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        const val CHANNEL_NUTRITION = "strideflow_nutrition_channel"
        const val CHANNEL_REMINDERS = "strideflow_reminders_channel"

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val nutritionChannel = NotificationChannel(
                    CHANNEL_NUTRITION,
                    "Nutrition & Fueling Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Scheduled meal and fueling alerts before upcoming workouts"
                }

                val reminderChannel = NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Workout Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminders for scheduled runs and training sessions"
                }

                nm.createNotificationChannel(nutritionChannel)
                nm.createNotificationChannel(reminderChannel)
            }
        }
    }
}
