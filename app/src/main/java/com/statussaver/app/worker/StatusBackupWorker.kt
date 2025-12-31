package com.statussaver.app.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.statussaver.app.data.repository.StatusRepository
import com.statussaver.app.util.Constants
import com.statussaver.app.util.SAFHelper
import java.util.concurrent.TimeUnit

class StatusBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "StatusBackupWorker"

        /**
         * Schedule periodic backup work
         */
        fun schedulePeriodicBackup(context: Context, intervalMinutes: Long = Constants.DEFAULT_BACKUP_INTERVAL_MINUTES) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<StatusBackupWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(Constants.BACKUP_WORK_TAG)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.BACKUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.d(TAG, "Scheduled periodic backup every $intervalMinutes minutes")
        }

        /**
         * Run backup immediately
         */
        fun runImmediateBackup(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<StatusBackupWorker>()
                .addTag(Constants.BACKUP_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Triggered immediate backup")
        }

        /**
         * Cancel all backup work
         */
        fun cancelAllWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(Constants.BACKUP_WORK_NAME)
            Log.d(TAG, "Cancelled all backup work")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting backup work...")

        // Check if we have valid SAF permission
        if (!SAFHelper.hasValidPermission(applicationContext)) {
            Log.w(TAG, "No valid SAF permission, skipping backup")
            return Result.success() // Don't retry, user needs to grant permission
        }

        return try {
            val repository = StatusRepository(applicationContext)
            val (backedUp, skipped) = repository.performFullBackup()
            
            Log.d(TAG, "Backup completed: $backedUp new, $skipped skipped")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed: ${e.message}")
            Result.retry()
        }
    }
}
