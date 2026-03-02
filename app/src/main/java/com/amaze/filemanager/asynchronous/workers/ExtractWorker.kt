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
import android.text.TextUtils
import androidx.work.Data
import androidx.work.WorkerParameters
import com.amaze.filemanager.R
import com.amaze.filemanager.application.AppConfig
import com.amaze.filemanager.fileoperations.filesystem.compressed.ArchivePasswordCache
import com.amaze.filemanager.filesystem.compressed.CompressedHelper
import com.amaze.filemanager.filesystem.compressed.extractcontents.Extractor
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.ui.notifications.NotificationConstants
import com.github.junrar.exception.UnsupportedRarV5Exception
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.PasswordRequiredException
import org.slf4j.LoggerFactory
import org.tukaani.xz.CorruptedInputException
import java.io.File
import java.io.IOException

/**
 * Worker for extracting archives, replacing [com.amaze.filemanager.asynchronous.services.ExtractService].
 * Integrates with [WorkerInteractionBridge] for password-protected archive handling.
 */
class ExtractWorker(
    context: Context,
    params: WorkerParameters,
) : AbstractProgressiveWorker(context, params) {
    companion object {
        private val LOG = LoggerFactory.getLogger(ExtractWorker::class.java)

        const val KEY_PATH_ZIP = "zip"
        const val KEY_ENTRIES_ZIP = "entries"
        const val KEY_PATH_EXTRACT = "extractpath"
    }

    override fun getNotificationId(): Int = NotificationConstants.EXTRACT_ID

    override fun getTitle(move: Boolean): Int = R.string.extracting

    override fun getSmallIcon(): Int = R.drawable.ic_zip_box_grey

    override fun getServiceType(): Int = SERVICE_EXTRACT

    override fun cleanUpOnStop() {
        // Cancel any pending password request
        WorkerInteractionBridge.cancel(id)
    }

    override suspend fun doProgressiveWork(): Result =
        withContext(Dispatchers.IO) {
            val ctx = applicationContext
            val compressedPath =
                inputData.getString(KEY_PATH_ZIP)
                    ?: return@withContext Result.failure()
            var extractionPath =
                inputData.getString(KEY_PATH_EXTRACT)
                    ?: return@withContext Result.failure()
            val entries = inputData.getStringArray(KEY_ENTRIES_ZIP)

            val compressedFile = File(compressedPath)
            if (!compressedFile.exists()) {
                LOG.warn("archive file does not exist: {}", compressedPath)
                return@withContext Result.failure()
            }

            val totalSize = compressedFile.length()
            progressHandler.setSourceSize(1)
            progressHandler.totalSize = totalSize
            progressHandler.setProgressListener { speed ->
                kotlinx.coroutines.runBlocking {
                    publishProgressData(speed, false, false)
                }
            }

            var passwordProtected = false
            var entriesToExtract = entries

            while (!isStopped) {
                val extractDirName = CompressedHelper.getFileName(compressedFile.name)

                val currentExtractionPath =
                    if (compressedPath == extractionPath) {
                        // custom extraction path not set, extract at default path
                        compressedFile.parent + "/" + extractDirName
                    } else {
                        if (extractionPath.endsWith("/")) {
                            extractionPath + extractDirName
                        } else if (!passwordProtected) {
                            extractionPath + "/" + extractDirName
                        } else {
                            extractionPath
                        }
                    }
                extractionPath = currentExtractionPath

                val actualEntries =
                    if (entriesToExtract != null && entriesToExtract!!.isEmpty()) {
                        null
                    } else {
                        entriesToExtract
                    }

                val extractor =
                    CompressedHelper.getExtractorInstance(
                        ctx,
                        compressedFile,
                        extractionPath,
                        object : Extractor.OnUpdate {
                            private var sourceFilesProcessed = 0

                            override fun onStart(
                                totalBytes: Long,
                                firstEntryName: String,
                            ) {
                                progressHandler.totalSize = totalBytes
                            }

                            override fun onUpdate(entryPath: String) {
                                progressHandler.fileName = entryPath
                                if (actualEntries != null) {
                                    progressHandler.sourceFilesProcessed = sourceFilesProcessed++
                                }
                            }

                            override fun onFinish() {
                                if (actualEntries == null) {
                                    progressHandler.sourceFilesProcessed = 1
                                }
                            }

                            override fun isCancelled(): Boolean = progressHandler.cancelled
                        },
                        updatePosition,
                    )

                if (extractor == null) {
                    AppConfig.toast(ctx, R.string.error_cant_decompress_that_file)
                    return@withContext Result.failure()
                }

                try {
                    if (actualEntries != null) {
                        extractor.extractFiles(actualEntries)
                    } else {
                        extractor.extractEverything()
                    }

                    val hasInvalidEntries = extractor.invalidArchiveEntries.size == 0

                    // Broadcast to reload list
                    val intent =
                        Intent(MainActivity.KEY_INTENT_LOAD_LIST).apply {
                            putExtra(MainActivity.KEY_INTENT_LOAD_LIST_FILE, extractionPath)
                            setPackage(ctx.packageName)
                        }
                    ctx.sendBroadcast(intent)

                    ArchivePasswordCache.getInstance().remove(compressedPath)
                    return@withContext if (hasInvalidEntries) Result.success() else Result.success()
                } catch (e: Extractor.EmptyArchiveNotice) {
                    LOG.error("Archive $compressedPath is an empty archive")
                    AppConfig.toast(
                        ctx,
                        ctx.getString(R.string.error_empty_archive, compressedPath),
                    )
                    ArchivePasswordCache.getInstance().remove(compressedPath)
                    return@withContext Result.success()
                } catch (e: Extractor.BadArchiveNotice) {
                    LOG.error("Archive $compressedPath is a corrupted archive.", e)
                    AppConfig.toast(
                        ctx,
                        if (e.cause != null && TextUtils.isEmpty(e.cause?.message)) {
                            ctx.getString(R.string.error_bad_archive_without_info, compressedPath)
                        } else {
                            ctx.getString(R.string.error_bad_archive_with_info, compressedPath, e.message)
                        },
                    )
                    ArchivePasswordCache.getInstance().remove(compressedPath)
                    return@withContext Result.success()
                } catch (_: CorruptedInputException) {
                    ArchivePasswordCache.getInstance().remove(compressedPath)
                    return@withContext Result.failure()
                } catch (e: IOException) {
                    if (PasswordRequiredException::class.java.isAssignableFrom(e::class.java)) {
                        LOG.debug("Archive is password protected.", e)
                        if (ArchivePasswordCache.getInstance().containsKey(compressedPath)) {
                            ArchivePasswordCache.getInstance().remove(compressedPath)
                            AppConfig.toast(
                                ctx,
                                ctx.getString(R.string.error_archive_password_incorrect),
                            )
                        }
                        passwordProtected = true

                        // Signal UI that we need a password
                        val passwordData =
                            Data.Builder()
                                .putInt(KEY_SERVICE_TYPE, getServiceType())
                                .putBoolean(KEY_NEEDS_PASSWORD, true)
                                .putString(KEY_ARCHIVE_PATH, compressedPath)
                                .build()
                        setProgress(passwordData)

                        // Suspend until UI supplies password
                        val password = WorkerInteractionBridge.requestPassword(id)

                        if (password == null) {
                            ArchivePasswordCache.getInstance().remove(compressedPath)
                            return@withContext Result.failure()
                        }

                        ArchivePasswordCache.getInstance().put(compressedPath, password)
                        // Loop back to retry extraction with password
                        continue
                    } else if (e.cause != null &&
                        UnsupportedRarV5Exception::class.java.isAssignableFrom(e.cause!!::class.java)
                    ) {
                        LOG.error("RAR $compressedPath is unsupported V5 archive", e)
                        AppConfig.toast(
                            ctx,
                            ctx.getString(R.string.error_unsupported_v5_rar, compressedPath),
                        )
                        ArchivePasswordCache.getInstance().remove(compressedPath)
                        return@withContext Result.failure()
                    } else {
                        LOG.error("Error while extracting file $compressedPath", e)
                        AppConfig.toast(ctx, ctx.getString(R.string.error))
                        ArchivePasswordCache.getInstance().remove(compressedPath)
                        return@withContext Result.failure()
                    }
                } catch (e: Throwable) {
                    LOG.error("Unhandled exception thrown", e)
                    ArchivePasswordCache.getInstance().remove(compressedPath)
                    return@withContext Result.failure()
                }
            }

            // If we reached here, worker was stopped
            ArchivePasswordCache.getInstance().remove(compressedPath)
            Result.failure()
        }
}
