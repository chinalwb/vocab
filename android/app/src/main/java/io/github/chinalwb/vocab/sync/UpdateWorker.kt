package io.github.chinalwb.vocab.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.chinalwb.vocab.MainActivity
import io.github.chinalwb.vocab.R
import io.github.chinalwb.vocab.VocabApp
import io.github.chinalwb.vocab.data.CheckResult
import java.util.concurrent.TimeUnit

private const val CHANNEL = "updates"
private const val WORK_NAME = "vocab-update-check"

/** Checks Pages for new entries in the background and posts a notification when there are some. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as VocabApp).repository
        return when (val r = repo.check()) {
            is CheckResult.Updated -> {
                if (r.added + r.changed > 0) notify(applicationContext, r)
                Result.success()
            }
            CheckResult.UpToDate -> Result.success()
            is CheckResult.Failed -> Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

fun describe(r: CheckResult.Updated): String = buildList {
    if (r.added > 0) add("新增 ${r.added} 条")
    if (r.changed > 0) add("更新 ${r.changed} 条")
    if (r.removed > 0) add("移除 ${r.removed} 条")
}.joinToString(" · ")

private fun notify(context: Context, r: CheckResult.Updated) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(CHANNEL, "词库更新", NotificationManager.IMPORTANCE_DEFAULT))
    val tap = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
    val n = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("词汇笔记有更新")
        .setContentText(describe(r))
        .setContentIntent(tap)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(1, n)
}
