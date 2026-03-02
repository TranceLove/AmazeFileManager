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
import androidx.work.WorkerParameters
import com.amaze.filemanager.R
import com.amaze.filemanager.filesystem.FileProperties
import com.amaze.filemanager.filesystem.HybridFile
import com.amaze.filemanager.filesystem.HybridFileParcelable
import com.amaze.filemanager.filesystem.files.CryptUtil
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.ui.notifications.NotificationConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Worker for encrypting files, replacing [com.amaze.filemanager.asynchronous.services.EncryptService].
 */
class EncryptWorker(
    context: Context,
    params: WorkerParameters,
) : AbstractProgressiveWorker(context, params) {
    companion object {
        private val LOG = LoggerFactory.getLogger(EncryptWorker::class.java)

        const val TAG_SOURCE_PATH = "crypt_source_path"
        const val TAG_SOURCE_NAME = "crypt_source_name"
        const val TAG_SOURCE_SIZE = "crypt_source_size"
        const val TAG_SOURCE_DIRECTORY = "crypt_source_directory"
        const val TAG_SOURCE_MODE = "crypt_source_mode"
        const val TAG_ENCRYPT_TARGET = "crypt_target"
        const val TAG_AESCRYPT = "use_aescrypt"
        const val TAG_PASSWORD = "password"
    }

    override fun getNotificationId(): Int = NotificationConstants.ENCRYPT_ID

    override fun getTitle(move: Boolean): Int = R.string.crypt_encrypting

    override fun getSmallIcon(): Int = R.drawable.ic_folder_lock_white_36dp

    override fun getServiceType(): Int = SERVICE_ENCRYPT

    override suspend fun doProgressiveWork(): Result =
        withContext(Dispatchers.IO) {
            val ctx = applicationContext
            val sourcePath = inputData.getString(TAG_SOURCE_PATH) ?: return@withContext Result.failure()
            val sourceName = inputData.getString(TAG_SOURCE_NAME) ?: ""
            val sourceSize = inputData.getLong(TAG_SOURCE_SIZE, 0L)
            val sourceIsDirectory = inputData.getBoolean(TAG_SOURCE_DIRECTORY, false)
            val sourceMode = inputData.getInt(TAG_SOURCE_MODE, 0)
            val targetFilename =
                inputData.getString(TAG_ENCRYPT_TARGET)
                    ?: return@withContext Result.failure()
            val useAesCrypt = inputData.getBoolean(TAG_AESCRYPT, false)
            val password = inputData.getString(TAG_PASSWORD)

            val baseFile = HybridFileParcelable(sourcePath)
            val failedOps = ArrayList<HybridFile>()

            val totalSize =
                if (sourceIsDirectory) {
                    baseFile.folderSize(ctx)
                } else {
                    baseFile.length(ctx)
                }

            progressHandler.setSourceSize(1)
            progressHandler.totalSize = totalSize
            progressHandler.setProgressListener { speed ->
                kotlinx.coroutines.runBlocking {
                    publishProgressData(speed, false, false)
                }
            }

            if (FileProperties.checkFolder(baseFile.path, ctx) == 1) {
                try {
                    CryptUtil(
                        ctx,
                        baseFile,
                        progressHandler,
                        failedOps,
                        targetFilename,
                        useAesCrypt,
                        password,
                        updatePosition,
                    )
                } catch (e: Exception) {
                    LOG.warn("failed to get crypt util instance", e)
                    failedOps.add(baseFile)
                }
            } else {
                LOG.warn("source file does not exist: {}", baseFile.path)
                failedOps.add(baseFile)
            }

            // Send broadcast to reload list
            val intent =
                Intent(MainActivity.KEY_INTENT_LOAD_LIST).apply {
                    putExtra(MainActivity.KEY_INTENT_LOAD_LIST_FILE, "")
                    setPackage(ctx.packageName)
                }
            ctx.sendBroadcast(intent)

            if (failedOps.isEmpty()) Result.success() else Result.failure()
        }
}
