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

package com.amaze.filemanager.filesystem.compressed

import android.content.Context
import com.amaze.filemanager.filesystem.compressed.createcontents.Compressor
import com.amaze.filemanager.filesystem.compressed.createcontents.helpers.ZipCompressor
import java.io.File

enum class CompressionFormat(
    val extension: String,
    val displayName: String,
    val mimeType: String,
) {
    ZIP(".zip", "ZIP", "application/zip"),
    TAR_GZ(".tar.gz", "TAR.GZ", "application/gzip"),
    TAR_BZ2(".tar.bz2", "TAR.BZ2", "application/x-bzip2"),
    TAR_XZ(".tar.xz", "TAR.XZ", "application/x-xz"),
    SEVEN_ZIP(".7z", "7Z", "application/x-7z-compressed"),
    ;

    companion object {
        @JvmStatic
        fun fromOrdinal(ordinal: Int): CompressionFormat {
            return values().find { it.ordinal == ordinal } ?: ZIP
        }

        @JvmStatic
        fun getCompressor(
            format: CompressionFormat,
            context: Context,
            outputPath: String,
            files: List<File>,
            onUpdate: Compressor.OnUpdate,
        ): Compressor {
            return when (format) {
                ZIP -> ZipCompressor(context, outputPath, files, onUpdate)
                else -> throw UnsupportedOperationException("Compression format not implemented: $format")
            }
        }
    }
}


