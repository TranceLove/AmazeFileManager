/*
 * Copyright (C) 2014-2024 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.asynchronous.workers

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.widget.RemoteViews
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.amaze.filemanager.R
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.ui.notifications.NotificationConstants
import com.amaze.filemanager.utils.DatapointParcelable
import com.amaze.filemanager.utils.ProgressHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * Base class for progressive workers replacing [AbstractProgressiveService].
 * Manages foreground notification with RemoteViews, WakeLock, and a coroutine-based
 * progress watcher.
 */
abstract class AbstractProgressiveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        private val LOG = LoggerFactory.getLogger(AbstractProgressiveWorker::class.java)

        /** Tag applied to all progressive work requests for observation. */
        const val TAG_PROGRESSIVE_WORK = "progressive_work"

        // Data keys for progress reporting
        const val KEY_SERVICE_TYPE = "SERVICE_TYPE"
        const val KEY_FILE_NAME = "FILE_NAME"
        const val KEY_AMOUNT_OF_SOURCE_FILES = "AMOUNT_OF_SOURCE_FILES"
        const val KEY_SOURCE_PROGRESS = "SOURCE_PROGRESS"
        const val KEY_TOTAL_SIZE = "TOTAL_SIZE"
        const val KEY_BYTE_PROGRESS = "BYTE_PROGRESS"
        const val KEY_SPEED_RAW = "SPEED_RAW"
        const val KEY_MOVE = "MOVE"
        const val KEY_COMPLETED = "COMPLETED"
        const val KEY_NEEDS_PASSWORD = "NEEDS_PASSWORD"
        const val KEY_ARCHIVE_PATH = "ARCHIVE_PATH"

        // Service type constants matching ProcessViewerFragment
        const val SERVICE_COPY = 0
        const val SERVICE_EXTRACT = 1
        const val SERVICE_COMPRESS = 2
        const val SERVICE_ENCRYPT = 3
        const val SERVICE_DECRYPT = 4

        // Progress watcher state (replaces ServiceWatcherUtil.ServiceStatusCallbacks)
        private const val STATE_UNSET = -1
        private const val STATE_HALTED = 0
        private const val STATE_RESUMED = 1

        /**
         * For compatibility purposes. Wraps the pending intent flag with FLAG_IMMUTABLE
         * if device SDK >= 32.
         */
        @JvmStatic
        fun getPendingIntentFlag(pendingIntentFlag: Int): Int {
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                pendingIntentFlag
            } else {
                pendingIntentFlag or PendingIntent.FLAG_IMMUTABLE
            }
        }
    }

    protected val progressHandler = ProgressHandler()
    private var wakeLock: PowerManager.WakeLock? = null
    private var isNotificationTitleSet = false

    /**
     * Worker-local position tracking, replaces the static ServiceWatcherUtil.position.
     * Updated by extractors/compressors via the [updatePosition] lambda.
     */
    val position = AtomicLong(0L)

    /** UpdatePosition implementation that uses the worker-local [position]. */
    val updatePosition: com.amaze.filemanager.fileoperations.utils.UpdatePosition =
        com.amaze.filemanager.fileoperations.utils.UpdatePosition { toAdd ->
            position.addAndGet(toAdd)
        }

    // Halted/resumed state tracking (replaces ServiceWatcherUtil.ServiceStatusCallbacks states)
    private var state = STATE_UNSET
    private var haltCounter = -1

    abstract fun getNotificationId(): Int

    @StringRes
    abstract fun getTitle(move: Boolean): Int

    abstract fun getSmallIcon(): Int

    abstract fun getServiceType(): Int

    abstract suspend fun doProgressiveWork(): Result

    /**
     * Override to perform cleanup when the worker is stopped by system or user.
     */
    protected open fun cleanUpOnStop() {}

    final override suspend fun doWork(): Result {
        acquireWakeLock()
        try {
            setForeground(createForegroundInfo())
            val result =
                coroutineScope {
                    val watcherJob = launchProgressWatcher()
                    try {
                        doProgressiveWork()
                    } finally {
                        watcherJob.cancel()
                    }
                }
            return result
        } catch (e: CancellationException) {
            handleStopped()
            throw e
        } catch (e: Exception) {
            LOG.error("Worker failed", e)
            return Result.failure()
        } finally {
            releaseWakeLock()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    // Note: CoroutineWorker.onStopped() is final. Cleanup when the coroutine is cancelled
    // is handled by doWork()'s finally block and isStopped checks.
    private fun handleStopped() {
        progressHandler.setCancelled(true)
        releaseWakeLock()
        cleanUpOnStop()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = buildProgressNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                getNotificationId(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(getNotificationId(), notification)
        }
    }

    // -- Notification building (legacy RemoteViews) --

    /**
     * Build the progress notification using RemoteViews.
     * TODO: When compileSdk 36: return if (Build.VERSION.SDK_INT >= 36) buildApi36Notification()
     * else buildLegacyNotification()
     */
    private fun buildProgressNotification(): Notification = buildLegacyNotification()

    private fun buildLegacyNotification(): Notification {
        val ctx = applicationContext
        val customSmallView = RemoteViews(ctx.packageName, R.layout.notification_service_small)
        val customBigView = RemoteViews(ctx.packageName, R.layout.notification_service_big)

        val notificationIntent =
            Intent(ctx, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(MainActivity.KEY_INTENT_PROCESS_VIEWER, true)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                ctx,
                0,
                notificationIntent,
                getPendingIntentFlag(0),
            )

        val builder =
            NotificationCompat.Builder(ctx, NotificationConstants.CHANNEL_NORMAL_ID)
                .setContentIntent(pendingIntent)
                .setCustomContentView(customSmallView)
                .setCustomBigContentView(customBigView)
                .setCustomHeadsUpContentView(customSmallView)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setOngoing(true)
                .setSmallIcon(getSmallIcon())

        NotificationConstants.setMetadata(ctx, builder, NotificationConstants.TYPE_NORMAL)
        return builder.build()
    }

    // -- Progress watcher coroutine (replaces ServiceWatcherUtil polling) --

    private fun kotlinx.coroutines.CoroutineScope.launchProgressWatcher(): Job =
        launch {
            while (isActive) {
                delay(1000) // poll every second

                val currentPosition = position.get()

                if (progressHandler.fileName == null) continue

                if (currentPosition == progressHandler.writtenSize &&
                    state != STATE_HALTED &&
                    ++haltCounter > 5
                ) {
                    haltCounter = 0
                    state = STATE_HALTED
                } else if (currentPosition != progressHandler.writtenSize) {
                    if (state == STATE_HALTED) {
                        state = STATE_RESUMED
                        haltCounter = 0
                    } else {
                        state = STATE_UNSET
                        haltCounter = 0
                    }
                }

                progressHandler.addWrittenLength(currentPosition)

                if (currentPosition == progressHandler.totalSize || progressHandler.cancelled) {
                    break
                }
            }
        }

    // -- Progress publishing (replaces AbstractProgressiveService.publishResults) --

    /**
     * Publishes progress to WorkManager's setProgress mechanism for observation by UI.
     */
    protected suspend fun publishProgressData(
        speed: Long,
        isComplete: Boolean,
        move: Boolean,
    ) {
        if (progressHandler.cancelled) return

        val data =
            Data.Builder()
                .putInt(KEY_SERVICE_TYPE, getServiceType())
                .putString(KEY_FILE_NAME, progressHandler.fileName)
                .putInt(KEY_AMOUNT_OF_SOURCE_FILES, progressHandler.sourceSize)
                .putInt(KEY_SOURCE_PROGRESS, progressHandler.sourceFilesProcessed)
                .putLong(KEY_TOTAL_SIZE, progressHandler.totalSize)
                .putLong(KEY_BYTE_PROGRESS, progressHandler.writtenSize)
                .putLong(KEY_SPEED_RAW, speed)
                .putBoolean(KEY_MOVE, move)
                .putBoolean(KEY_COMPLETED, isComplete)
                .build()

        setProgress(data)
    }

    // -- WakeLock management --

    private fun acquireWakeLock() {
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock =
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "${this::class.java.name}:wakelock",
            ).apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L) // 6 hour timeout matching dataSync limit
            }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to release wakelock", e)
        }
        wakeLock = null
    }
}

/**
 * Extension function to convert WorkManager Data to DatapointParcelable.
 */
fun Data.toDatapointParcelable(): DatapointParcelable? {
    val name = getString(AbstractProgressiveWorker.KEY_FILE_NAME) ?: return null
    return DatapointParcelable(
        name = name,
        amountOfSourceFiles = getInt(AbstractProgressiveWorker.KEY_AMOUNT_OF_SOURCE_FILES, 0),
        sourceProgress = getInt(AbstractProgressiveWorker.KEY_SOURCE_PROGRESS, 0),
        totalSize = getLong(AbstractProgressiveWorker.KEY_TOTAL_SIZE, 0L),
        byteProgress = getLong(AbstractProgressiveWorker.KEY_BYTE_PROGRESS, 0L),
        speedRaw = getLong(AbstractProgressiveWorker.KEY_SPEED_RAW, 0L),
        move = getBoolean(AbstractProgressiveWorker.KEY_MOVE, false),
        completed = getBoolean(AbstractProgressiveWorker.KEY_COMPLETED, false),
    )
}
