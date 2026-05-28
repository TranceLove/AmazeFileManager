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

package com.amaze.filemanager.filesystem.compressed.createcontents.helpers

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import com.amaze.filemanager.filesystem.FileUtil
import com.amaze.filemanager.filesystem.compressed.createcontents.Compressor
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipCompressor(
    context: Context,
    outputPath: String,
    files: List<File>,
    onUpdate: OnUpdate,
) : Compressor(context, outputPath, files, onUpdate) {
    @Throws(IOException::class)
    override fun compress() {
        val outputFile = File(outputPath)
        val outputStream = FileUtil.getOutputStream(outputFile, context)
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOutputStream ->
            runWithProgress { file, parentPath ->
                zipOutputStream.putNextEntry(createZipEntry(file, parentPath))
                writeContents(file) { buffer, len ->
                    zipOutputStream.write(buffer, 0, len)
                }
                zipOutputStream.closeEntry()
            }
        }
    }

    private fun createZipEntry(
        file: File,
        parentPath: String,
    ): ZipEntry {
        val entryPath = "${createEntryPrefixWith(parentPath)}${file.name}"
        return ZipEntry(entryPath).apply {
            if (SDK_INT >= O) {
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
}

