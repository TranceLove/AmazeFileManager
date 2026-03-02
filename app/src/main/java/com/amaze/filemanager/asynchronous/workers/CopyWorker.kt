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

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.amaze.filemanager.R
import com.amaze.filemanager.application.AppConfig
import com.amaze.filemanager.asynchronous.asynctasks.DeleteTask
import com.amaze.filemanager.database.CryptHandler
import com.amaze.filemanager.database.models.explorer.EncryptedEntry
import com.amaze.filemanager.fileoperations.exceptions.ShellNotRunningException
import com.amaze.filemanager.fileoperations.filesystem.OpenMode
import com.amaze.filemanager.filesystem.FileProperties
import com.amaze.filemanager.filesystem.HybridFile
import com.amaze.filemanager.filesystem.HybridFileParcelable
import com.amaze.filemanager.filesystem.Operations
import com.amaze.filemanager.filesystem.files.CryptUtil
import com.amaze.filemanager.filesystem.files.FileUtils
import com.amaze.filemanager.filesystem.files.GenericCopyUtil
import com.amaze.filemanager.filesystem.files.MediaConnectionUtils
import com.amaze.filemanager.filesystem.root.CopyFilesCommand
import com.amaze.filemanager.filesystem.root.MoveFileCommand
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.ui.notifications.NotificationConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Worker for copying/moving files, replacing [com.amaze.filemanager.asynchronous.services.CopyService].
 */
class CopyWorker(
    context: Context,
    params: WorkerParameters,
) : AbstractProgressiveWorker(context, params) {
    companion object {
        private val LOG = LoggerFactory.getLogger(CopyWorker::class.java)

        const val TAG_IS_ROOT_EXPLORER = "is_root"
        const val TAG_COPY_TARGET = "COPY_DIRECTORY"
        const val TAG_COPY_SOURCES = "FILE_PATHS"
        const val TAG_COPY_OPEN_MODE = "MODE"
        const val TAG_COPY_MOVE = "move"

        /**
         * Enqueue a copy/move operation via WorkManager.
         *
         * @param context application context
         * @param sourcePaths array of source file paths
         * @param targetPath destination directory path
         * @param openMode target [OpenMode] ordinal
         * @param move true if this is a move operation
         * @param isRootExplorer true if root explorer is enabled
         */
        @JvmStatic
        fun enqueue(
            context: Context,
            sourcePaths: Array<String>,
            targetPath: String,
            openMode: Int,
            move: Boolean,
            isRootExplorer: Boolean,
        ) {
            val data =
                Data.Builder()
                    .putStringArray(TAG_COPY_SOURCES, sourcePaths)
                    .putString(TAG_COPY_TARGET, targetPath)
                    .putInt(TAG_COPY_OPEN_MODE, openMode)
                    .putBoolean(TAG_COPY_MOVE, move)
                    .putBoolean(TAG_IS_ROOT_EXPLORER, isRootExplorer)
                    .build()

            val workRequest =
                OneTimeWorkRequestBuilder<CopyWorker>()
                    .setInputData(data)
                    .addTag(TAG_PROGRESSIVE_WORK)
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("copy_work", ExistingWorkPolicy.APPEND, workRequest)
        }

        /**
         * Convenience overload that accepts an [ArrayList] of [HybridFileParcelable].
         * Extracts paths and delegates to the primary [enqueue] method.
         */
        @JvmStatic
        fun enqueue(
            context: Context,
            sourceFiles: ArrayList<HybridFileParcelable>,
            targetPath: String,
            openMode: Int,
            move: Boolean,
            isRootExplorer: Boolean,
        ) {
            val paths = sourceFiles.map { it.path }.toTypedArray()
            enqueue(context, paths, targetPath, openMode, move, isRootExplorer)
        }
    }

    override fun getNotificationId(): Int = NotificationConstants.COPY_ID

    override fun getTitle(move: Boolean): Int = if (move) R.string.moving else R.string.copying

    override fun getSmallIcon(): Int = R.drawable.ic_content_copy_white_36dp

    override fun getServiceType(): Int = SERVICE_COPY

    override suspend fun doProgressiveWork(): Result =
        withContext(Dispatchers.IO) {
            val ctx = applicationContext

            val sourcePaths =
                inputData.getStringArray(TAG_COPY_SOURCES)
                    ?: return@withContext Result.failure()
            val targetPath =
                inputData.getString(TAG_COPY_TARGET)
                    ?: return@withContext Result.failure()
            val openModeOrdinal = inputData.getInt(TAG_COPY_OPEN_MODE, OpenMode.UNKNOWN.ordinal)
            val move = inputData.getBoolean(TAG_COPY_MOVE, false)
            val isRootExplorer = inputData.getBoolean(TAG_IS_ROOT_EXPLORER, false)
            val openMode = OpenMode.getOpenMode(openModeOrdinal)

            // Reconstruct HybridFileParcelable list from paths
            val sourceFiles = ArrayList<HybridFileParcelable>()
            for (path in sourcePaths) {
                val file = HybridFileParcelable(path)
                file.generateMode(ctx)
                // Populate isDirectory field from filesystem since simple constructor doesn't set it
                file.setDirectory(file.isDirectory(ctx))
                sourceFiles.add(file)
            }

            if (sourceFiles.isEmpty()) return@withContext Result.failure()

            // Calculate total size
            val totalSize = FileUtils.getTotalBytes(sourceFiles, ctx)
            progressHandler.setSourceSize(sourceFiles.size)
            progressHandler.totalSize = totalSize
            progressHandler.setProgressListener { speed ->
                kotlinx.coroutines.runBlocking {
                    publishProgressData(speed, false, move)
                }
            }

            // Execute copy operation
            val failedOps = ArrayList<HybridFile>()
            val toDelete = ArrayList<HybridFileParcelable>()

            if (FileProperties.checkFolder(targetPath, ctx) == 1) {
                var sourceProgress = 0
                for (i in sourceFiles.indices) {
                    sourceProgress = i
                    val sourceFile = sourceFiles[i]

                    if (isStopped || progressHandler.cancelled) break

                    try {
                        val hFile =
                            if (targetPath.contains(ctx.externalCacheDir?.path ?: "")) {
                                HybridFile(
                                    OpenMode.FILE,
                                    targetPath,
                                    sourceFile.getName(ctx),
                                    sourceFile.isDirectory,
                                )
                            } else {
                                HybridFile(
                                    openMode,
                                    targetPath,
                                    sourceFile.getName(ctx),
                                    sourceFile.isDirectory,
                                )
                            }

                        if ((sourceFile.mode == OpenMode.ROOT || openMode == OpenMode.ROOT) &&
                            isRootExplorer
                        ) {
                            LOG.debug("either source or target are in root")
                            progressHandler.sourceFilesProcessed = ++sourceProgress
                            copyRoot(sourceFile, hFile, move, failedOps, ctx)
                            continue
                        }

                        progressHandler.sourceFilesProcessed = ++sourceProgress
                        copyFiles(sourceFile, hFile, failedOps, ctx)
                    } catch (e: Exception) {
                        LOG.error("Got exception checkout: ${sourceFile.path}", e)
                        failedOps.add(sourceFiles[i])
                        for (j in i + 1 until sourceFiles.size) {
                            failedOps.add(sourceFiles[j])
                        }
                        break
                    }
                }
            } else if (isRootExplorer) {
                var sourceProgress = 0
                for (i in sourceFiles.indices) {
                    if (isStopped || progressHandler.cancelled) break

                    val hFile =
                        HybridFile(
                            openMode,
                            targetPath,
                            sourceFiles[i].getName(ctx),
                            sourceFiles[i].isDirectory,
                        )
                    progressHandler.sourceFilesProcessed = ++sourceProgress
                    progressHandler.fileName = sourceFiles[i].getName(ctx)
                    copyRoot(sourceFiles[i], hFile, move, failedOps, ctx)
                }
            } else {
                failedOps.addAll(sourceFiles)
            }

            // Perform delete for move operations
            if (move && !progressHandler.cancelled && !isStopped) {
                val filesToDelete = ArrayList<HybridFileParcelable>()
                for (file in sourceFiles) {
                    if (!failedOps.contains(file)) {
                        filesToDelete.add(file)
                    }
                }
                if (filesToDelete.isNotEmpty()) {
                    DeleteTask(ctx, true).execute(filesToDelete)
                }
            }

            // Update encrypted entries
            if (failedOps.isEmpty()) {
                for (sourceFile in sourceFiles) {
                    try {
                        findAndReplaceEncryptedEntry(
                            sourceFile,
                            isRootExplorer,
                            targetPath,
                            move,
                            ctx,
                        )
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.encryption_fail_copy),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }

            // Publish final progress
            publishProgressData(0L, true, move)

            // Send broadcast to reload list
            val intent =
                Intent(MainActivity.KEY_INTENT_LOAD_LIST).apply {
                    putExtra(MainActivity.KEY_INTENT_LOAD_LIST_FILE, targetPath)
                    setPackage(ctx.packageName)
                }
            ctx.sendBroadcast(intent)

            if (failedOps.isEmpty()) Result.success() else Result.failure()
        }

    private fun copyRoot(
        sourceFile: HybridFileParcelable,
        targetFile: HybridFile,
        move: Boolean,
        failedOps: ArrayList<HybridFile>,
        ctx: Context,
    ) {
        try {
            if (!move) {
                CopyFilesCommand.copyFiles(sourceFile.path, targetFile.path)
            } else {
                MoveFileCommand.moveFile(sourceFile.path, targetFile.path)
            }
            position.addAndGet(sourceFile.getSize())
        } catch (e: ShellNotRunningException) {
            LOG.warn(
                "failed to copy root file source: {} dest: {}",
                sourceFile.path,
                targetFile.path,
                e,
            )
            failedOps.add(sourceFile)
        }
        MediaConnectionUtils.scanFile(ctx, arrayOf(targetFile))
    }

    @Throws(IOException::class)
    private fun copyFiles(
        sourceFile: HybridFileParcelable,
        targetFile: HybridFile,
        failedOps: ArrayList<HybridFile>,
        ctx: Context,
    ) {
        if (progressHandler.cancelled || isStopped) return

        if (sourceFile.isDirectory) {
            if (!targetFile.exists()) {
                targetFile.mkdir(ctx)
            }

            if (!Operations.isFileNameValid(sourceFile.getName(ctx)) ||
                Operations.isCopyLoopPossible(sourceFile, targetFile)
            ) {
                failedOps.add(sourceFile)
                return
            }
            targetFile.setLastModified(sourceFile.lastModified())

            if (progressHandler.cancelled || isStopped) return

            sourceFile.forEachChildrenFile(
                ctx,
                false,
            ) { file ->
                val destFile =
                    HybridFile(
                        targetFile.mode,
                        targetFile.path,
                        file.getName(ctx),
                        file.isDirectory,
                    )
                try {
                    copyFiles(file, destFile, failedOps, ctx)
                    destFile.setLastModified(file.lastModified())
                } catch (e: IOException) {
                    throw IllegalStateException(e)
                }
            }
        } else {
            if (!Operations.isFileNameValid(sourceFile.getName(ctx))) {
                failedOps.add(sourceFile)
                return
            }

            val copyUtil = GenericCopyUtil(ctx, progressHandler)
            progressHandler.fileName = sourceFile.getName(ctx)
            copyUtil.copy(
                sourceFile,
                targetFile,
                {
                    AppConfig.toast(ctx, ctx.getString(R.string.copy_low_memory))
                },
                updatePosition,
            )
            targetFile.setLastModified(sourceFile.lastModified())
        }
    }

    /**
     * Iterates through every file to find an encrypted file and update/add metadata
     * in the database.
     */
    private fun findAndReplaceEncryptedEntry(
        sourceFile: HybridFileParcelable,
        isRootExplorer: Boolean,
        targetPath: String,
        move: Boolean,
        ctx: Context,
    ) {
        if (sourceFile.isDirectory && !sourceFile.getName(ctx).endsWith(CryptUtil.CRYPT_EXTENSION)) {
            sourceFile.forEachChildrenFile(ctx, isRootExplorer) { file ->
                findAndReplaceEncryptedEntry(file, isRootExplorer, targetPath, move, ctx)
            }
        } else {
            if (sourceFile.getName(ctx).endsWith(CryptUtil.CRYPT_EXTENSION)) {
                try {
                    val oldEntry = CryptHandler.findEntry(sourceFile.path)
                    if (oldEntry != null) {
                        val newEntry = EncryptedEntry()
                        newEntry.password = oldEntry.password
                        newEntry.path = "$targetPath/${sourceFile.getName(ctx)}"

                        if (move) {
                            newEntry.id = oldEntry.id
                            CryptHandler.updateEntry(oldEntry, newEntry)
                        } else {
                            CryptHandler.addEntry(newEntry)
                        }
                    }
                } catch (e: Exception) {
                    LOG.warn("failed to find and replace encrypted entry after copy", e)
                }
            }
        }
    }
}
