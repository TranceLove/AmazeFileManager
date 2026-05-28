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

package com.amaze.filemanager.filesystem.compressed.createcontents

import android.content.Context
import com.amaze.filemanager.asynchronous.management.ServiceWatcherUtil
import com.amaze.filemanager.filesystem.files.GenericCopyUtil
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

abstract class Compressor(
    protected val context: Context,
    protected val outputPath: String,
    protected val files: List<File>,
    protected val onUpdate: OnUpdate,
) {
    @Throws(IOException::class)
    abstract fun compress()

    protected fun createEntryPrefixWith(path: String): String =
        if (path.isEmpty()) {
            path
        } else {
            "$path/"
        }

    protected fun runWithProgress(writeFile: (file: File, parentPath: String) -> Unit) {
        val firstName = files.firstOrNull()?.name ?: ""
        onUpdate.onStart(files.sumOf { it.sizeRecursively() }, firstName)
        try {
            files.forEach { file ->
                if (onUpdate.isCancelled()) {
                    return@forEach
                }
                walkRecursively(file, "", writeFile)
            }
        } finally {
            onUpdate.onFinish()
        }
    }

    protected fun writeContents(
        file: File,
        writeChunk: (ByteArray, Int) -> Unit,
    ) {
        val buf = ByteArray(GenericCopyUtil.DEFAULT_BUFFER_SIZE)
        var len: Int
        BufferedInputStream(FileInputStream(file)).use { input ->
            while (input.read(buf).also { len = it } > 0) {
                if (onUpdate.isCancelled()) {
                    break
                }
                writeChunk(buf, len)
                ServiceWatcherUtil.position += len.toLong()
            }
        }
    }

    private fun walkRecursively(
        file: File,
        parentPath: String,
        writeFile: (file: File, parentPath: String) -> Unit,
    ) {
        if (onUpdate.isCancelled()) {
            return
        }
        if (!file.isDirectory) {
            onUpdate.onUpdate("${createEntryPrefixWith(parentPath)}${file.name}")
            writeFile(file, parentPath)
            return
        }
        file.listFiles()?.forEach {
            walkRecursively(it, "${createEntryPrefixWith(parentPath)}${file.name}", writeFile)
        }
    }

    private fun File.sizeRecursively(): Long {
        if (!exists()) return 0L
        if (!isDirectory) return length()
        return listFiles()?.sumOf { it.sizeRecursively() } ?: 0L
    }

    interface OnUpdate {
        fun onStart(
            totalBytes: Long,
            firstName: String,
        )

        fun onUpdate(entryPath: String)

        fun onFinish()

        fun isCancelled(): Boolean
    }
}

