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
import android.os.Build.VERSION_CODES.R
import androidx.test.core.app.ApplicationProvider
import com.amaze.filemanager.filesystem.compressed.createcontents.Compressor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [R])
class ZipCompressorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val createdRoots = mutableListOf<File>()

    @After
    fun tearDown() {
        createdRoots.forEach { it.deleteRecursively() }
        createdRoots.clear()
    }

    @Test
    fun compress_nestedTree_reportsProgressAndPaths() {
        val root = newTestRoot()
        val sourceDir = File(root, "source").apply { mkdirs() }
        val nestedDir = File(sourceDir, "nested/leaf").apply { mkdirs() }
        val topFile = createFileWithBytes(sourceDir, "top.txt", 3)
        val midFile = createFileWithBytes(File(sourceDir, "nested"), "mid.bin", 5)
        val leafFile = createFileWithBytes(nestedDir, "leaf.dat", 7)
        val extraFile = createFileWithBytes(root, "extra.bin", 4)

        val archive = File(root, "output.zip")
        val listener = RecordingOnUpdate(cancelAfterFirstUpdate = false)

        ZipCompressor(
            context = context,
            outputPath = archive.absolutePath,
            files = listOf(sourceDir, extraFile),
            onUpdate = listener,
        ).compress()

        assertTrue(archive.exists())
        assertEquals(sourceDir.name, listener.startedWithFirstName)
        assertEquals(
            topFile.length() + midFile.length() + leafFile.length() + extraFile.length(),
            listener.startedWithTotalBytes,
        )
        assertEquals(1, listener.finishCalls)

        ZipFile(archive).use { zipFile ->
            val entries = zipFile.entries().asSequence().map { it.name }.toList()
            val expected =
                setOf(
                    "source/top.txt",
                    "source/nested/mid.bin",
                    "source/nested/leaf/leaf.dat",
                    "extra.bin",
                )
            assertEquals(expected, entries.toSet())
            // Updates should mirror exactly the emitted archive entry paths.
            assertEquals(entries, listener.updates)
        }
    }

    @Test
    fun compress_cancelledAfterFirstUpdate_stopsTraversalAndStillFinishes() {
        val root = newTestRoot()
        val sourceDir = File(root, "source").apply { mkdirs() }
        createFileWithBytes(sourceDir, "first.txt", 8)
        createFileWithBytes(sourceDir, "second.txt", 8)

        val archive = File(root, "cancelled.zip")
        val listener = RecordingOnUpdate(cancelAfterFirstUpdate = true)

        ZipCompressor(
            context = context,
            outputPath = archive.absolutePath,
            files = listOf(sourceDir),
            onUpdate = listener,
        ).compress()

        assertTrue(archive.exists())
        assertEquals(sourceDir.name, listener.startedWithFirstName)
        assertEquals(1, listener.updates.size)
        assertEquals(1, listener.finishCalls)

        ZipFile(archive).use { zipFile ->
            val entries = zipFile.entries().asSequence().toList()
            assertEquals(1, entries.size)
            val writtenPath = entries.first().name
            assertEquals(listener.updates.first(), writtenPath)
            val writtenSize = zipFile.getInputStream(entries.first()).use { it.readBytes().size }
            assertEquals(0, writtenSize)
        }

        assertFalse(listener.startedWithTotalBytes <= 0L)
    }

    private fun newTestRoot(): File {
        val root = File(context.cacheDir, "zip-compressor-test-${System.nanoTime()}")
        root.mkdirs()
        createdRoots.add(root)
        return root
    }

    private fun createFileWithBytes(
        parent: File,
        name: String,
        size: Int,
    ): File {
        if (!parent.exists()) {
            parent.mkdirs()
        }
        return File(parent, name).apply {
            writeBytes(ByteArray(size) { idx -> idx.toByte() })
        }
    }

    private class RecordingOnUpdate(
        private val cancelAfterFirstUpdate: Boolean,
    ) : Compressor.OnUpdate {
        var startedWithTotalBytes: Long = -1
        var startedWithFirstName: String = ""
        val updates = mutableListOf<String>()
        var finishCalls: Int = 0

        override fun onStart(
            totalBytes: Long,
            firstName: String,
        ) {
            startedWithTotalBytes = totalBytes
            startedWithFirstName = firstName
        }

        override fun onUpdate(entryPath: String) {
            updates.add(entryPath)
        }

        override fun onFinish() {
            finishCalls++
        }

        override fun isCancelled(): Boolean = cancelAfterFirstUpdate && updates.isNotEmpty()
    }
}

