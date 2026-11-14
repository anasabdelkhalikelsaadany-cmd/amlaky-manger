package com.molaky.manager

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class MolakyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleReminders(this)
    }
}

fun scheduleReminders(context: Context) {
    val now = LocalDateTime.now()
    var next = now.toLocalDate().atTime(9, 0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    val delayMinutes = Duration.between(now, next).toMinutes()

    val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
        .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "molaky_daily_reminders_v2",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val dao = AppDatabase.get(applicationContext).dao()
            val today = LocalDate.now()
            val pays = dao.allPayments().groupBy { it.unitId }
            val changes = dao.allRentChanges().groupBy { it.unitId }
            val summaries = dao.allUnits().map {
                summarize(it, changes[it.id].orEmpty(), pays[it.id].orEmpty(), today)
            }
            val lines = reminderLines(summaries, today)
            if (lines.isNotEmpty()) showNotification(applicationContext, lines)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

private const val CHANNEL_ID = "molaky_reminders"

fun showNotification(context: Context, lines: List<String>) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) return

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= 26) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "تنبيهات الإيجارات", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
    val open = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val shown = lines.take(8)
    val extra = lines.size - shown.size
    val body = shown.joinToString("\n") + if (extra > 0) "\nو $extra تنبيهات أخرى" else ""

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("مدير أملاكي – ${lines.size} تنبيه")
        .setContentText(lines.first())
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    manager.notify(1001, notification)
}
