/*
 * Copyright (C) 2014-2020 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
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

package com.amaze.filemanager.asynchronous.services

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.IBinder
import android.widget.RemoteViews
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.amaze.filemanager.R
import com.amaze.filemanager.application.AppConfig
import com.amaze.filemanager.asynchronous.management.ServiceWatcherUtil
import com.amaze.filemanager.filesystem.HybridFileParcelable
import com.amaze.filemanager.filesystem.compressed.CompressedHelper
import com.amaze.filemanager.filesystem.compressed.CompressionFormat
import com.amaze.filemanager.filesystem.compressed.createcontents.Compressor
import com.amaze.filemanager.filesystem.files.FileUtils
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.ui.notifications.NotificationConstants
import com.amaze.filemanager.utils.DatapointParcelable
import com.amaze.filemanager.utils.ObtainableServiceBinder
import com.amaze.filemanager.utils.ProgressHandler
import com.amaze.filemanager.utils.registerReceiverCompat
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

@Suppress("TooManyFunctions") // Hack.
class CompressService : AbstractProgressiveService() {
    private val log: Logger = LoggerFactory.getLogger(CompressService::class.java)

    private val mBinder: IBinder = ObtainableServiceBinder(this)
    private val disposables = CompositeDisposable()
    private lateinit var mNotifyManager: NotificationManagerCompat
    private lateinit var mBuilder: NotificationCompat.Builder
    private var progressListener: ProgressListener? = null
    private val progressHandler = ProgressHandler()

    // list of data packages, to initiate chart in process viewer fragment
    private val dataPackages = ArrayList<DatapointParcelable>()
    private var accentColor = 0
    private var sharedPreferences: SharedPreferences? = null
    private var customSmallContentViews: RemoteViews? = null
    private var customBigContentViews: RemoteViews? = null

    override fun onCreate() {
        super.onCreate()
        registerReceiverCompat(
            receiver1,
            IntentFilter(KEY_COMPRESS_BROADCAST_CANCEL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(
        intent: Intent,
        flags: Int,
        startId: Int,
    ): Int {
        val mZipPath = intent.getStringExtra(KEY_COMPRESS_PATH)
        if (mZipPath.isNullOrEmpty()) {
            log.warn("missing compression output path")
            stopSelf()
            return START_NOT_STICKY
        }
        val baseFiles: ArrayList<HybridFileParcelable> =
            if (SDK_INT >= TIRAMISU) {
                intent.getParcelableArrayListExtra(
                    KEY_COMPRESS_FILES,
                    HybridFileParcelable::class.java,
                )!!
            } else {
                intent.getParcelableArrayListExtra(KEY_COMPRESS_FILES)!!
            }
        val zipFile = File(mZipPath)
        val compressionFormat =
            CompressionFormat.fromOrdinal(intent.getIntExtra(KEY_COMPRESS_FORMAT, CompressionFormat.ZIP.ordinal))
                .takeIf { CompressedHelper.isCreatable(it) }
                ?: CompressionFormat.ZIP
        mNotifyManager = NotificationManagerCompat.from(applicationContext)
        if (!zipFile.exists()) {
            try {
                zipFile.createNewFile()
            } catch (e: IOException) {
                log.warn("failed to create zip file", e)
            }
        }
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        accentColor =
            (application as AppConfig)
                .utilsProvider
                .colorPreference
                .getCurrentUserColorPreferences(this, sharedPreferences).accent
        val notificationIntent =
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.KEY_INTENT_PROCESS_VIEWER, true)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                getPendingIntentFlag(0),
            )
        customSmallContentViews = RemoteViews(packageName, R.layout.notification_service_small)
        customBigContentViews = RemoteViews(packageName, R.layout.notification_service_big)
        val stopIntent = Intent(KEY_COMPRESS_BROADCAST_CANCEL)
        val stopPendingIntent =
            PendingIntent.getBroadcast(
                applicationContext,
                1234,
                stopIntent,
                getPendingIntentFlag(FLAG_UPDATE_CURRENT),
            )
        val action =
            NotificationCompat.Action(
                R.drawable.ic_zip_box_grey,
                getString(R.string.stop_ftp),
                stopPendingIntent,
            )
        mBuilder =
            NotificationCompat.Builder(this, NotificationConstants.CHANNEL_NORMAL_ID)
                .setSmallIcon(R.drawable.ic_zip_box_grey)
                .setContentIntent(pendingIntent)
                .setCustomContentView(customSmallContentViews)
                .setCustomBigContentView(customBigContentViews)
                .setCustomHeadsUpContentView(customSmallContentViews)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .addAction(action)
                .setOngoing(true)
                .setColor(accentColor)
        NotificationConstants.setMetadata(this, mBuilder, NotificationConstants.TYPE_NORMAL)
        if (SDK_INT >= Q) {
            ServiceCompat.startForeground(
                this,
                NotificationConstants.ZIP_ID,
                mBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NotificationConstants.ZIP_ID, mBuilder.build())
        }
        initNotificationViews()
        super.onStartCommand(intent, flags, startId)
        super.progressHalted()
        val zipTask = CompressTask(this, baseFiles, zipFile.absolutePath, compressionFormat)
        disposables.add(zipTask.compress())
        // If we get killed, after returning from here, restart
        return START_NOT_STICKY
    }

    override fun getNotificationManager(): NotificationManagerCompat = mNotifyManager

    override fun getNotificationBuilder(): NotificationCompat.Builder = mBuilder

    override fun getNotificationId(): Int = NotificationConstants.ZIP_ID

    @StringRes
    override fun getTitle(move: Boolean): Int = R.string.compressing

    override fun getNotificationCustomViewSmall(): RemoteViews = customSmallContentViews!!

    override fun getNotificationCustomViewBig(): RemoteViews = customBigContentViews!!

    override fun getProgressListener(): ProgressListener? = progressListener

    override fun setProgressListener(progressListener: ProgressListener?) {
        this.progressListener = progressListener
    }

    override fun getDataPackages(): ArrayList<DatapointParcelable> = dataPackages

    override fun getProgressHandler(): ProgressHandler = progressHandler

    override fun clearDataPackages() = dataPackages.clear()

    inner class CompressTask(
        private val zipService: CompressService,
        private val baseFiles: ArrayList<HybridFileParcelable>,
        private val zipPath: String,
        private val compressionFormat: CompressionFormat,
    ) {
        private lateinit var watcherUtil: ServiceWatcherUtil

        /**
         * Main use case for executing zipping task by given [zipPath]
         */
        fun compress(): Disposable {
            return Completable.create { emitter ->
                execute(
                    emitter,
                    zipService.applicationContext,
                    FileUtils.hybridListToFileArrayList(baseFiles),
                    zipPath,
                    compressionFormat,
                )

                emitter.onComplete()
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        watcherUtil.stopWatch()
                        val intent =
                            Intent(MainActivity.KEY_INTENT_LOAD_LIST)
                                .putExtra(MainActivity.KEY_INTENT_LOAD_LIST_FILE, zipPath)
                                .setPackage(applicationContext.packageName)
                        zipService.sendBroadcast(intent)
                        zipService.stopSelf()
                    },
                    { log.error(it.message ?: "CompressService.CompressAsyncTask.compress failed") },
                )
        }

        /**
         * Deletes the destination file zip file if exists
         */
        fun cancel() {
            progressHandler.cancelled = true
            val zipFile = File(zipPath)
            if (zipFile.exists()) zipFile.delete()
        }

        /**
         * Main logic for zipping specified files.
         */
        fun execute(
            emitter: CompletableEmitter,
            context: Context,
            baseFiles: ArrayList<File>,
            zipPath: String,
            compressionFormat: CompressionFormat,
        ) {
            val zipDirectory = File(zipPath)
            watcherUtil = ServiceWatcherUtil(progressHandler)
            watcherUtil.watch(this@CompressService)
            try {
                var processedFiles = 0
                val compressor =
                    CompressionFormat.getCompressor(
                        compressionFormat,
                        context,
                        zipPath,
                        baseFiles,
                        object : Compressor.OnUpdate {
                            override fun onStart(
                                totalBytes: Long,
                                firstName: String,
                            ) {
                                progressHandler.sourceSize = baseFiles.size
                                progressHandler.totalSize = totalBytes
                                progressHandler.setProgressListener { speed: Long ->
                                    publishResults(speed, false, false)
                                }
                                zipService.addFirstDatapoint(
                                    firstName,
                                    baseFiles.size,
                                    totalBytes,
                                    false,
                                )
                            }

                            override fun onUpdate(entryPath: String) {
                                progressHandler.fileName = entryPath
                                progressHandler.sourceFilesProcessed = ++processedFiles
                            }

                            override fun onFinish() {}

                            override fun isCancelled(): Boolean = progressHandler.cancelled || emitter.isDisposed
                        },
                    )
                compressor.compress()
            } catch (e: IOException) {
                log.warn("failed to zip file", e)
            } finally {
                try {
                    context.sendBroadcast(
                        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            .setData(Uri.fromFile(zipDirectory)),
                    )
                } catch (e: IOException) {
                    log.warn("failed to close zip streams", e)
                }
            }
        }

    }

    /*
     * Class used for the client Binder. Because we know this service always runs in the same process
     * as its clients, we don't need to deal with IPC.
     */
    private val receiver1: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                progressHandler.cancelled = true
            }
        }

    override fun onBind(arg0: Intent): IBinder = mBinder

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver1)
        disposables.dispose()
    }

    companion object {
        const val KEY_COMPRESS_PATH = "zip_path"
        const val KEY_COMPRESS_FILES = "zip_files"
        const val KEY_COMPRESS_FORMAT = "compress_format"
        const val KEY_COMPRESS_BROADCAST_CANCEL = "zip_cancel"
    }
}

