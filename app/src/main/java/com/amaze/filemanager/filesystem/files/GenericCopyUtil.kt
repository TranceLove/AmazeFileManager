/*
 * Copyright (C) 2014-2026 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
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

package com.amaze.filemanager.filesystem.files

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.KITKAT
import android.os.Build.VERSION_CODES.LOLLIPOP
import androidx.annotation.VisibleForTesting
import com.amaze.filemanager.fileoperations.filesystem.OpenMode
import com.amaze.filemanager.fileoperations.utils.OnLowMemory
import com.amaze.filemanager.fileoperations.utils.UpdatePosition
import com.amaze.filemanager.filesystem.ExternalSdCardOperation
import com.amaze.filemanager.filesystem.FileProperties
import com.amaze.filemanager.filesystem.HybridFile
import com.amaze.filemanager.filesystem.HybridFileParcelable
import com.amaze.filemanager.filesystem.MediaStoreHack
import com.amaze.filemanager.filesystem.SafRootHolder
import com.amaze.filemanager.filesystem.cloud.CloudUtil
import com.amaze.filemanager.utils.DataUtils
import com.amaze.filemanager.utils.OTGUtil
import com.amaze.filemanager.utils.ProgressHandler
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

/** Base class to handle file copy. */
@Suppress("ComplexMethod", "LongMethod")
class GenericCopyUtil(
    private val context: Context,
    private val progressHandler: ProgressHandler,
) {
    private var sourceFile: HybridFileParcelable? = null
    private var targetFile: HybridFile? = null
    private val dataUtils = DataUtils.getInstance()

    companion object {
        @JvmStatic
        private val LOG = LoggerFactory.getLogger(GenericCopyUtil::class.java)

        const val DEFAULT_BUFFER_SIZE = 8192

        // Defines the block size per transfer over NIO channels.
        // Cannot modify DEFAULT_BUFFER_SIZE since it's used by other classes, will have undesired
        // effect on other functions
        @JvmStatic
        private val DEFAULT_TRANSFER_QUANTUM = 1024 * 1024

        @JvmStatic
        private val PROGRESS_UPDATE_THRESHOLD = 4L * 1024 * 1024
    }

    /**
     * Starts copy of file Supports : [File], [jcifs.smb.SmbFile], [DocumentFile],
     * [CloudStorage]
     *
     * @param lowOnMemory defines whether system is running low on memory, in which case we'll switch
     *     to using streams instead of channel which maps the whole buffer in memory.
     */
    @Throws(IOException::class)
    private fun startCopy(
        lowOnMemory: Boolean,
        onLowMemory: OnLowMemory,
        updatePosition: UpdatePosition,
    ) {
        var inChannel: ReadableByteChannel? = null
        var outChannel: WritableByteChannel? = null
        var bufferedInputStream: BufferedInputStream? = null
        var bufferedOutputStream: BufferedOutputStream? = null

        try {
            val sourceFile = requireNotNull(sourceFile)
            val targetFile = requireNotNull(targetFile)

            // initializing the input channels based on file types
            when {
                sourceFile.isOtgFile || sourceFile.isDocumentFile -> {
                    val contentResolver = context.contentResolver
                    val documentSourceFile =
                        if (sourceFile.isDocumentFile) {
                            OTGUtil.getDocumentFile(
                                sourceFile.path,
                                SafRootHolder.uriRoot!!,
                                context,
                                if (sourceFile.isOtgFile) OpenMode.OTG else OpenMode.DOCUMENT_FILE,
                                false,
                            )
                        } else {
                            OTGUtil.getDocumentFile(sourceFile.path, context, false)
                        }
                    bufferedInputStream =
                        BufferedInputStream(
                            contentResolver.openInputStream(documentSourceFile!!.uri),
                            DEFAULT_TRANSFER_QUANTUM,
                        )
                }
                sourceFile.isSmb || sourceFile.isSftp || sourceFile.isFtp -> {
                    bufferedInputStream =
                        BufferedInputStream(
                            sourceFile.getInputStream(context),
                            DEFAULT_TRANSFER_QUANTUM,
                        )
                }
                sourceFile.isDropBoxFile || sourceFile.isBoxFile ||
                    sourceFile.isGoogleDriveFile || sourceFile.isOneDriveFile -> {
                    val openMode = sourceFile.mode
                    val cloudStorage = dataUtils.getAccount(openMode)
                    bufferedInputStream =
                        BufferedInputStream(
                            cloudStorage.download(
                                CloudUtil.stripPath(openMode, sourceFile.path),
                            ),
                        )
                }
                else -> {
                    // source file is neither smb nor otg; getting a channel from direct file
                    val file = File(sourceFile.path)
                    if (FileProperties.isReadable(file)) {
                        if (targetFile.isCloudDriveFile || lowOnMemory
                        ) {
                            bufferedInputStream = BufferedInputStream(FileInputStream(file))
                        } else {
                            inChannel = RandomAccessFile(file, "r").channel
                        }
                    } else {
                        if (SDK_INT >= LOLLIPOP) {
                            val contentResolver = context.contentResolver
                            val documentSourceFile =
                                ExternalSdCardOperation.getDocumentFile(
                                    file,
                                    sourceFile.isDirectory,
                                    context,
                                )
                            bufferedInputStream =
                                BufferedInputStream(
                                    contentResolver.openInputStream(documentSourceFile!!.uri),
                                    DEFAULT_TRANSFER_QUANTUM,
                                )
                        } else if (SDK_INT == KITKAT) {
                            val inputStream =
                                MediaStoreHack.getInputStream(
                                    context,
                                    file,
                                    sourceFile.getSize(),
                                )
                            bufferedInputStream = BufferedInputStream(inputStream)
                        }
                    }
                }
            }

            // initializing the output channels based on file types
            when {
                targetFile.isOtgFile || targetFile.isDocumentFile -> {
                    val contentResolver = context.contentResolver
                    val documentTargetFile =
                        if (targetFile.isDocumentFile) {
                            OTGUtil.getDocumentFile(
                                targetFile.path,
                                SafRootHolder.uriRoot!!,
                                context,
                                if (targetFile.isOtgFile) OpenMode.OTG else OpenMode.DOCUMENT_FILE,
                                true,
                            )
                        } else {
                            OTGUtil.getDocumentFile(targetFile.path, context, true)
                        }
                    bufferedOutputStream =
                        BufferedOutputStream(
                            contentResolver.openOutputStream(documentTargetFile!!.uri),
                            DEFAULT_TRANSFER_QUANTUM,
                        )
                }
                targetFile.isFtp || targetFile.isSftp || targetFile.isSmb -> {
                    bufferedOutputStream =
                        BufferedOutputStream(
                            targetFile.getOutputStream(context),
                            DEFAULT_TRANSFER_QUANTUM,
                        )
                }
                targetFile.isCloudDriveFile -> {
                    if (bufferedInputStream == null) {
                        bufferedInputStream =
                            BufferedInputStream(
                                sourceFile.getInputStream(context),
                                DEFAULT_TRANSFER_QUANTUM,
                            )
                    }
                    cloudCopy(targetFile.mode, bufferedInputStream)
                    return
                }
                else -> {
                    val file = File(targetFile.path)
                    if (FileProperties.isWritable(file)) {
                        if (lowOnMemory) {
                            bufferedOutputStream = BufferedOutputStream(FileOutputStream(file))
                        } else {
                            outChannel =
                                RandomAccessFile(file, "rw").channel.also {
                                    it.truncate(0) // Ensure file is truncated before writing
                                }
                        }
                    } else {
                        if (SDK_INT >= LOLLIPOP) {
                            val contentResolver = context.contentResolver
                            val documentTargetFile =
                                ExternalSdCardOperation.getDocumentFile(
                                    file,
                                    targetFile.isDirectory(context),
                                    context,
                                )
                            bufferedOutputStream =
                                BufferedOutputStream(
                                    contentResolver.openOutputStream(documentTargetFile!!.uri),
                                    DEFAULT_TRANSFER_QUANTUM,
                                )
                        } else if (SDK_INT == KITKAT) {
                            bufferedOutputStream =
                                BufferedOutputStream(
                                    MediaStoreHack.getOutputStream(context, file.path),
                                )
                        }
                    }
                }
            }

            if (bufferedInputStream != null) {
                inChannel = Channels.newChannel(bufferedInputStream)
            }
            if (bufferedOutputStream != null) {
                outChannel = Channels.newChannel(bufferedOutputStream)
            }

            requireNotNull(inChannel) { "Input channel must not be null" }
            requireNotNull(outChannel) { "Output channel must not be null" }

            doCopy(inChannel, outChannel, updatePosition)
        } catch (e: IOException) {
            LOG.error("I/O Error copy {} to {}", sourceFile, targetFile, e)
            throw e
        } catch (e: OutOfMemoryError) {
            LOG.warn("low memory while copying {} to {}", sourceFile, targetFile, e)
            onLowMemory.onLowMemory()
            startCopy(true, onLowMemory, updatePosition)
        } finally {
            try {
                if (inChannel != null && inChannel.isOpen) inChannel.close()
                if (outChannel != null && outChannel.isOpen) outChannel.close()
                /*
                 * It does seem closing the inChannel/outChannel is already sufficient closing the below
                 * bufferedInputStream and bufferedOutputStream instances. These 2 lines prevented FTP
                 * copy from working, especially on Android 9 - TranceLove
                 */
            } catch (e: IOException) {
                LOG.warn("failed to close stream after copying", e)
            }

            // If target file is copied onto the device and copy was successful, trigger media store
            // rescan
            targetFile?.let {
                MediaConnectionUtils.scanFiles(context, arrayOf(it))
            }
        }
    }

    @Throws(IOException::class)
    private fun cloudCopy(
        openMode: OpenMode,
        bufferedInputStream: BufferedInputStream?,
    ) {
        val dataUtils = DataUtils.getInstance()
        val cloudStorage = dataUtils.getAccount(openMode)

        try {
            if (sourceFile?.mode == openMode) {
                cloudStorage.copy(
                    CloudUtil.stripPath(openMode, sourceFile!!.path),
                    CloudUtil.stripPath(openMode, targetFile!!.path),
                )
            } else {
                cloudStorage.upload(
                    CloudUtil.stripPath(openMode, targetFile!!.path),
                    bufferedInputStream!!,
                    sourceFile!!.getSize(),
                    true,
                )
            }
        } finally {
            try {
                bufferedInputStream?.close()
            } catch (e: IOException) {
                LOG.warn("Failed to close BufferedInputStream in cloudCopy", e)
            }
        }
    }

    /**
     * Method exposes this class to initiate copy
     *
     * @param sourceFile the source file, which is to be copied
     * @param targetFile the target file
     */
    @Throws(IOException::class)
    fun copy(
        sourceFile: HybridFileParcelable,
        targetFile: HybridFile,
        onLowMemory: OnLowMemory,
        updatePosition: UpdatePosition,
    ) {
        this@GenericCopyUtil.sourceFile = sourceFile
        this@GenericCopyUtil.targetFile = targetFile
        startCopy(false, onLowMemory, updatePosition)
    }

    /**
     * Calls [doCopy].
     *
     * @see Channels.newChannel
     * @param bufferedInputStream source
     * @param outChannel target
     */
    @VisibleForTesting
    @Throws(IOException::class)
    internal fun copyFile(
        bufferedInputStream: BufferedInputStream,
        outChannel: FileChannel,
        updatePosition: UpdatePosition,
    ) {
        doCopy(Channels.newChannel(bufferedInputStream), outChannel, updatePosition)
    }

    /**
     * Calls [doCopy].
     *
     * @param inChannel source
     * @param outChannel target
     */
    @VisibleForTesting
    @Throws(IOException::class)
    internal fun copyFile(
        inChannel: FileChannel,
        outChannel: FileChannel,
        updatePosition: UpdatePosition,
    ) {
        doCopy(inChannel, outChannel, updatePosition)
    }

    /**
     * Calls [doCopy].
     *
     * @see Channels.newChannel
     * @param bufferedInputStream source
     * @param bufferedOutputStream target
     */
    @VisibleForTesting
    @Throws(IOException::class)
    internal fun copyFile(
        bufferedInputStream: BufferedInputStream,
        bufferedOutputStream: BufferedOutputStream,
        updatePosition: UpdatePosition,
    ) {
        doCopy(
            Channels.newChannel(bufferedInputStream),
            Channels.newChannel(bufferedOutputStream),
            updatePosition,
        )
    }

    /**
     * Calls [doCopy].
     *
     * @see Channels.newChannel
     * @param inChannel source
     * @param bufferedOutputStream target
     */
    @VisibleForTesting
    @Throws(IOException::class)
    internal fun copyFile(
        inChannel: FileChannel,
        bufferedOutputStream: BufferedOutputStream,
        updatePosition: UpdatePosition,
    ) {
        doCopy(inChannel, Channels.newChannel(bufferedOutputStream), updatePosition)
    }

    /**
     * Core copy method. Uses [FileChannel.transferTo] for file-to-file copies (zero-copy
     * optimization on Linux/Android via sendfile syscall), falls back to a [ByteBuffer] loop
     * for other channel types. Progress updates are batched to reduce callback overhead.
     */
    @VisibleForTesting
    @Throws(IOException::class)
    internal fun doCopy(
        from: ReadableByteChannel,
        to: WritableByteChannel,
        updatePosition: UpdatePosition,
    ) {
        if (from is FileChannel && to is FileChannel) {
            val size = from.size()
            var position = 0L
            var pendingProgress = 0L
            var fallbackToBuffer = false
            while (position < size && !progressHandler.cancelled) {
                val remaining = size - position
                val chunk = minOf(DEFAULT_TRANSFER_QUANTUM.toLong(), remaining)
                val transferred = from.transferTo(position, chunk, to)

                if (transferred <= 0L) {
                    if (transferred == 0L) {
                        LOG.warn(
                            "transferTo returned 0, falling back to buffer copy at position {}",
                            position,
                        )
                    }
                    fallbackToBuffer = true
                    break
                }

                position += transferred
                pendingProgress += transferred
                if (pendingProgress >= PROGRESS_UPDATE_THRESHOLD) {
                    updatePosition.updatePosition(pendingProgress)
                    pendingProgress = 0L
                }
            }

            if (fallbackToBuffer && position < size && !progressHandler.cancelled) {
                from.position(position)
                to.position(position)

                val buffer = ByteBuffer.allocateDirect(DEFAULT_TRANSFER_QUANTUM)

                while (position < size && !progressHandler.cancelled) {
                    buffer.clear()
                    val maxRead = minOf(buffer.capacity().toLong(), size - position).toInt()
                    buffer.limit(maxRead)

                    val read = from.read(buffer)
                    if (read < 0) break
                    if (read == 0) throw IOException("Copy stalled: no read progress in fallback")

                    buffer.flip()
                    var writtenInChunk = 0
                    while (buffer.hasRemaining()) {
                        val written = to.write(buffer)
                        if (written <= 0) throw IOException("Copy stalled: no write progress in fallback")
                        writtenInChunk += written
                    }

                    position += writtenInChunk.toLong()
                    pendingProgress += writtenInChunk.toLong()
                    if (pendingProgress >= PROGRESS_UPDATE_THRESHOLD) {
                        updatePosition.updatePosition(pendingProgress)
                        pendingProgress = 0L
                    }
                }
            }

            if (pendingProgress > 0L) {
                updatePosition.updatePosition(pendingProgress)
            }
        } else {
            val buffer = ByteBuffer.allocateDirect(DEFAULT_TRANSFER_QUANTUM)
            var pendingProgress = 0L
            while (!progressHandler.cancelled) {
                buffer.clear()
                val read = from.read(buffer)
                if (read < 0) break
                if (read == 0) throw IOException("Copy stalled: no read progress")

                buffer.flip()
                while (buffer.hasRemaining()) {
                    val written = to.write(buffer)
                    if (written <= 0) throw IOException("Copy stalled: no write progress")
                    pendingProgress += written.toLong()

                    if (pendingProgress >= PROGRESS_UPDATE_THRESHOLD) {
                        updatePosition.updatePosition(pendingProgress)
                        pendingProgress = 0L
                    }
                }
            }
            if (pendingProgress > 0L) {
                updatePosition.updatePosition(pendingProgress)
            }
        }
    }
}
