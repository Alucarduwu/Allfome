// ReminderManager.kt
package com.example.allofme.screens.metas

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Calendar

object ReminderManager {

    fun initReminders(context: Context) {
        // 🔹 Diarias 21:00
        scheduleDaily(context, 0, 0, "Tienes tareas pendientes del día")

        // 🔹 Semanales sábado y domingo 10:00
        scheduleWeekly(context, Calendar.SATURDAY, 10, 0, "Tienes tareas pendientes de la semana")
        scheduleWeekly(context, Calendar.SUNDAY, 10, 0, "Tienes tareas pendientes de la semana")

        // 🔹 Mensuales última semana 10:00
        val today = Calendar.getInstance()
        val lastWeekDay = today.getActualMaximum(Calendar.DAY_OF_MONTH) - 6
        scheduleMonthly(context, lastWeekDay, 10, 0, "Tienes tareas pendientes del mes")

        // 🔹 Día específico del mes 9:00
        val specificDay = 15
        scheduleSpecificDay(context, specificDay, 9, 0, "Tienes tareas específicas para hoy")
    }

    private fun scheduleDaily(context: Context, hour: Int, minute: Int, message: String) {
        scheduleAlarm(context, hour, minute, message)
    }

    private fun scheduleWeekly(context: Context, dayOfWeek: Int, hour: Int, minute: Int, message: String) {
        scheduleAlarm(context, hour, minute, message, dayOfWeek = dayOfWeek)
    }

    private fun scheduleMonthly(context: Context, dayOfMonth: Int, hour: Int, minute: Int, message: String) {
        scheduleAlarm(context, hour, minute, message, dayOfMonth = dayOfMonth)
    }

    private fun scheduleSpecificDay(context: Context, dayOfMonth: Int, hour: Int, minute: Int, message: String) {
        scheduleAlarm(context, hour, minute, message, dayOfMonth = dayOfMonth)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        message: String,
        dayOfWeek: Int = 0,
        dayOfMonth: Int = 0
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("message", message)
        }

        val requestCode = hour * 100 + minute + (dayOfWeek * 10) + dayOfMonth
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            if (dayOfWeek != 0) {
                set(Calendar.DAY_OF_WEEK, dayOfWeek)
                // Si ya pasó el día de la semana, suma 7 días
                if (before(Calendar.getInstance())) add(Calendar.WEEK_OF_YEAR, 1)
            }

            if (dayOfMonth != 0) {
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                // Si ya pasó el día del mes, suma un mes
                if (before(Calendar.getInstance())) add(Calendar.MONTH, 1)
            }

            if (dayOfWeek == 0 && dayOfMonth == 0 && before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val message = intent.getStringExtra("message") ?: "¡Tienes tareas pendientes!"
            showNotification(context, message)
        }

        private fun showNotification(context: Context, message: String) {
            val channelId = "reminder_channel"
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(channelId, "Recordatorios", NotificationManager.IMPORTANCE_HIGH)
            channel.enableLights(true)
            channel.lightColor = 0xFFFFC0CB.toInt()
            manager.createNotificationChannel(channel)

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Recordatorio")
                .setContentText(message)
                .setColor(0xFFFFC0CB.toInt())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            manager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}
