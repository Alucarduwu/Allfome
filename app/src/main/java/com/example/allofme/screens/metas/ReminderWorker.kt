package com.example.allofme.screens.metas

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager as SysNotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.allofme.data.database.AppDatabase
import com.example.allofme.data.repository.MetaRepository
import com.example.allofme.viewmodels.MetaViewModel
import com.example.allofme.viewmodels.MetaViewModelFactory
import java.util.Calendar

object NotificationManager {

    private const val CHANNEL_ID = "metas_channel"
    private const val CHANNEL_NAME = "Metas Notifications"
    private const val CHANNEL_DESCRIPTION = "Notificaciones para tareas pendientes"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            SysNotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = CHANNEL_DESCRIPTION }

        val notificationManager: SysNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as SysNotificationManager
        notificationManager.createNotificationChannel(channel)
        Log.d("NotificationManager", "Canal de notificación creado: $CHANNEL_ID")
    }

    fun sendNotification(context: Context, title: String, message: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("NotificationManager", "No se tiene el permiso POST_NOTIFICATIONS. Notificación no enviada.")
                return
            }
        }

        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            Log.d("NotificationManager", "Notificación enviada: $title - $message")
        } catch (e: SecurityException) {
            Log.e("NotificationManager", "Error al enviar notificación: Permiso denegado", e)
        }
    }

    fun scheduleNotifications(context: Context) {
        // Notificación diaria a las 8:00
        scheduleDailyAlarm(context, 8, 0, 1)

        // Notificación semanal: viernes y sábado a las 10:00
        scheduleWeeklyAlarm(context, 10, 0, 2, Calendar.FRIDAY)
        scheduleWeeklyAlarm(context, 10, 0, 3, Calendar.SATURDAY)

        // Notificación mensual: última semana del mes, lunes a las 10:00
        scheduleMonthlyAlarm(context, 10, 0, 4, Calendar.MONDAY)

        Log.d("NotificationManager", "Notificaciones programadas correctamente")
    }

    private fun scheduleDailyAlarm(context: Context, hour: Int, minute: Int, notificationId: Int) {
        scheduleAlarm(context, hour, minute, notificationId)
    }

    private fun scheduleWeeklyAlarm(context: Context, hour: Int, minute: Int, notificationId: Int, dayOfWeek: Int) {
        scheduleAlarm(context, hour, minute, notificationId, dayOfWeek)
    }

    private fun scheduleMonthlyAlarm(context: Context, hour: Int, minute: Int, notificationId: Int, dayOfWeek: Int) {
        scheduleAlarm(context, hour, minute, notificationId, dayOfWeek, true)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(
        context: Context,
        hour: Int,
        minute: Int,
        notificationId: Int,
        dayOfWeek: Int? = null,
        isLastWeekOfMonth: Boolean = false
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MetaAlarmReceiver::class.java).apply {
            putExtra("notificationId", notificationId)
            putExtra("isLastWeekOfMonth", isLastWeekOfMonth)
            putExtra("hour", hour)
            putExtra("minute", minute)
            dayOfWeek?.let { putExtra("dayOfWeek", it) }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            dayOfWeek?.let { set(Calendar.DAY_OF_WEEK, it) }

            if (isLastWeekOfMonth) {
                moveToLastWeekOfMonth(dayOfWeek ?: Calendar.MONDAY)
            }

            if (before(Calendar.getInstance())) {
                if (isLastWeekOfMonth) add(Calendar.WEEK_OF_MONTH, 1)
                else add(Calendar.DAY_OF_MONTH, 7.takeIf { dayOfWeek != null } ?: 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    private fun Calendar.moveToLastWeekOfMonth(dayOfWeek: Int) {
        val lastDay = getActualMaximum(Calendar.DAY_OF_MONTH)
        set(Calendar.DAY_OF_MONTH, lastDay)
        set(Calendar.DAY_OF_WEEK, dayOfWeek)
    }

    class MetaAlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val notificationId = intent.getIntExtra("notificationId", 0)
            val isLastWeekOfMonth = intent.getBooleanExtra("isLastWeekOfMonth", false)
            val hour = intent.getIntExtra("hour", 1)
            val minute = intent.getIntExtra("minute", 0)
            val dayOfWeek = intent.getIntExtra("dayOfWeek", -1).takeIf { it != -1 }

            val repository = MetaRepository(AppDatabase.getInstance(context).metaDao())
            val sharedPrefs = context.getSharedPreferences("metas_prefs", Context.MODE_PRIVATE)
            val viewModel = MetaViewModelFactory(repository, sharedPrefs).create(MetaViewModel::class.java)

            viewModel.actualizarMetasSegunFecha()

            when (notificationId) {
                1 -> {
                    val pendingDaily = viewModel.getPendingDailyTasks()
                    if (pendingDaily.isNotEmpty())
                        sendNotification(context, "Tareas pendientes del día", "Faltan ${pendingDaily.size} tareas del día por completar", notificationId)
                }
                2, 3 -> {
                    val pendingWeekly = viewModel.getPendingWeeklyTasks()
                    if (pendingWeekly.isNotEmpty())
                        sendNotification(context, "Tareas pendientes de la semana", "Faltan ${pendingWeekly.size} tareas de la semana por completar", notificationId)
                }
                4 -> {
                    if (isLastWeekOfMonth) {
                        val pendingMonthly = viewModel.getPendingMonthlyTasks()
                        if (pendingMonthly.isNotEmpty())
                            sendNotification(context, "Tareas pendientes del mes", "Faltan ${pendingMonthly.size} tareas del mes por completar", notificationId)
                    }
                }
            }

            // Reprogramar la alarma automáticamente
            NotificationManager.scheduleAlarm(context, hour, minute, notificationId, dayOfWeek, isLastWeekOfMonth)
        }
    }
}
