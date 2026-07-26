package com.katok.pro.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object TokenRefreshScheduler {

    private const val UNIQUE_WORK_NAME = "token_refresh_work"
    private const val SIX_DAYS_MILLIS = 6 * 24 * 60 * 60 * 1000L

    @JvmStatic
    fun scheduleNextRefresh(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<TokenRefreshWorker>()
            .setInitialDelay(SIX_DAYS_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    @JvmStatic
    fun cancelRefresh(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
