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
import android.net.Uri
import android.os.Build
import androidx.work.WorkerParameters
import com.amaze.filemanager.R
import com.amaze.filemanager.filesystem.FileUtil
import com.amaze.filemanager.filesystem.HybridFileParcelable
import com.amaze.filemanager.filesystem.files.FileUtils
import com.amaze.filemanager.filesystem.files.GenericCopyUtil
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.ui.notifications.NotificationConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

/**
 * Worker for zip compression, replacing [com.amaze.filemanager.asynchronous.services.ZipService].
 *
 * Input data:
 * - [KEY_COMPRESS_PATH]: String path for the output zip file
 * - [KEY_COMPRESS_FILES]: String array of file paths to compress
 */
class CompressWorker(
    context: Context,
    params: WorkerParameters,
) : AbstractProgressiveWorker(context, params) {
    companion object {
        private val LOG = LoggerFactory.getLogger(CompressWorker::class.java)

        const val KEY_COMPRESS_PATH = "zip_path"
        const val KEY_COMPRESS_FILES = "zip_files"
    }

    private var zipPath: String = ""

    override fun getNotificationId(): Int = NotificationConstants.ZIP_ID

    override fun getTitle(move: Boolean): Int = R.string.compressing

    override fun getSmallIcon(): Int = R.drawable.ic_zip_box_grey

    override fun getServiceType(): Int = SERVICE_COMPRESS

    override fun cleanUpOnStop() {
        // Delete incomplete zip file on cancellation
        val zipFile = File(zipPath)
        if (zipFile.exists()) zipFile.delete()
    }

    override suspend fun doProgressiveWork(): Result =
        withContext(Dispatchers.IO) {
            val ctx = applicationContext
            zipPath = inputData.getString(KEY_COMPRESS_PATH) ?: return@withContext Result.failure()
            val filePaths =
                inputData.getStringArray(KEY_COMPRESS_FILES)
                    ?: return@withContext Result.failure()

            val zipFile = File(zipPath)
            if (!zipFile.exists()) {
                try {
                    zipFile.createNewFile()
                } catch (e: IOException) {
                    LOG.warn("failed to create zip file", e)
                    return@withContext Result.failure()
                }
            }

            val baseFiles = filePaths.map { File(it) }.let { ArrayList(it) }

            // Calculate total size
            val hybridFiles = filePaths.map { HybridFileParcelable(it) }.let { ArrayList(it) }
            val totalBytes = FileUtils.getTotalBytes(hybridFiles, ctx)
            progressHandler.sourceSize = baseFiles.size
            progressHandler.totalSize = totalBytes
            progressHandler.setProgressListener { speed ->
                kotlinx.coroutines.runBlocking {
                    publishProgressData(speed, false, false)
                }
            }

            execute(ctx, baseFiles, zipPath)

            // Broadcast to reload list
            val intent =
                Intent(MainActivity.KEY_INTENT_LOAD_LIST).apply {
                    putExtra(MainActivity.KEY_INTENT_LOAD_LIST_FILE, zipPath)
                    setPackage(ctx.packageName)
                }
            ctx.sendBroadcast(intent)

            Result.success()
        }

    private fun execute(
        context: Context,
        baseFiles: ArrayList<File>,
        zipPath: String,
    ) {
        val zipDirectory = File(zipPath)
        var zos: ZipOutputStream? = null
        try {
            val out: OutputStream? = FileUtil.getOutputStream(zipDirectory, context)
            zos = ZipOutputStream(BufferedOutputStream(out))
            for ((fileProgress, file) in baseFiles.withIndex()) {
                if (isStopped) return
                progressHandler.fileName = file.name
                progressHandler.sourceFilesProcessed = fileProgress + 1
                compressFile(zos, file, "")
            }
        } catch (e: IOException) {
            LOG.warn("failed to zip file", e)
        } finally {
            try {
                zos?.flush()
                zos?.close()
                context.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                        .setData(Uri.fromFile(zipDirectory)),
                )
            } catch (e: IOException) {
                LOG.warn("failed to close zip streams", e)
            }
        }
    }

    @Throws(IOException::class, NullPointerException::class, ZipException::class)
    private fun compressFile(
        zos: ZipOutputStream,
        file: File,
        path: String,
    ) {
        if (progressHandler.cancelled) return
        if (!file.isDirectory) {
            zos.putNextEntry(createZipEntry(file, path))
            val buf = ByteArray(GenericCopyUtil.DEFAULT_BUFFER_SIZE)
            var len: Int
            BufferedInputStream(FileInputStream(file)).use { bufferedInputStream ->
                while (bufferedInputStream.read(buf).also { len = it } > 0) {
                    if (!progressHandler.cancelled) {
                        zos.write(buf, 0, len)
                        position.addAndGet(len.toLong())
                    } else {
                        break
                    }
                }
            }
            return
        }
        file.listFiles()?.forEach {
            compressFile(zos, it, "${createZipEntryPrefixWith(path)}${file.name}")
        }
    }

    private fun createZipEntryPrefixWith(path: String): String = if (path.isEmpty()) path else "$path/"

    private fun createZipEntry(
        file: File,
        path: String,
    ): ZipEntry =
        ZipEntry("${createZipEntryPrefixWith(path)}${file.name}").apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs =
                    Files.readAttributes(
                        Paths.get(file.absolutePath),
                        BasicFileAttributes::class.java,
                    )
                setCreationTime(attrs.creationTime())
                    .setLastAccessTime(attrs.lastAccessTime())
                    .lastModifiedTime = attrs.lastModifiedTime()
            } else {
                time = file.lastModified()
            }
        }
}
