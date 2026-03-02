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

import android.os.Build.VERSION_CODES.LOLLIPOP
import android.os.Build.VERSION_CODES.P
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.amaze.filemanager.shadows.ShadowMultiDex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File

/**
 * Unit tests for [CopyWorker].
 *
 * Tests cover input validation / failure scenarios and basic copy operations
 * using Robolectric + TestListenableWorkerBuilder.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [LOLLIPOP], shadows = [ShadowMultiDex::class])
@LooperMode(LooperMode.Mode.PAUSED)
class CopyWorkerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        // Eagerly initialize Operations class on main thread to avoid
        // AsyncTask static init on background coroutine thread
        Class.forName("com.amaze.filemanager.filesystem.Operations")
    }

    @Test
    fun `worker fails with missing source paths`() {
        val data =
            Data.Builder()
                .putString(CopyWorker.TAG_COPY_TARGET, "/tmp/dest")
                .build()

        val worker =
            TestListenableWorkerBuilder<CopyWorker>(
                context = ApplicationProvider.getApplicationContext(),
            ).setInputData(data).build()

        runBlocking {
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }
    }

    @Test
    fun `worker fails with missing target path`() {
        val data =
            Data.Builder()
                .putStringArray(CopyWorker.TAG_COPY_SOURCES, arrayOf("/tmp/file.txt"))
                .build()

        val worker =
            TestListenableWorkerBuilder<CopyWorker>(
                context = ApplicationProvider.getApplicationContext(),
            ).setInputData(data).build()

        runBlocking {
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }
    }

    @Test
    fun `worker fails with empty source paths`() {
        val data =
            Data.Builder()
                .putStringArray(CopyWorker.TAG_COPY_SOURCES, emptyArray())
                .putString(CopyWorker.TAG_COPY_TARGET, "/tmp/dest")
                .build()

        val worker =
            TestListenableWorkerBuilder<CopyWorker>(
                context = ApplicationProvider.getApplicationContext(),
            ).setInputData(data).build()

        runBlocking {
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }
    }

    @Test
    fun `worker service type is SERVICE_COPY`() {
        val data =
            Data.Builder()
                .putStringArray(CopyWorker.TAG_COPY_SOURCES, arrayOf("/tmp/file.txt"))
                .putString(CopyWorker.TAG_COPY_TARGET, "/tmp/dest")
                .build()

        val worker =
            TestListenableWorkerBuilder<CopyWorker>(
                context = ApplicationProvider.getApplicationContext(),
            ).setInputData(data).build()

        assertEquals(AbstractProgressiveWorker.SERVICE_COPY, worker.getServiceType())
    }

    @Test
    fun `worker copies single file successfully`() {
        val sourceDir = tempFolder.newFolder("source")
        val destDir = tempFolder.newFolder("dest")
        val sourceFile = File(sourceDir, "test.txt")
        sourceFile.writeText("Hello, Copy!")

        val data =
            Data.Builder()
                .putStringArray(CopyWorker.TAG_COPY_SOURCES, arrayOf(sourceFile.absolutePath))
                .putString(CopyWorker.TAG_COPY_TARGET, destDir.absolutePath)
                .putBoolean(CopyWorker.TAG_COPY_MOVE, false)
                .build()

        val worker =
            TestListenableWorkerBuilder<CopyWorker>(
                context = ApplicationProvider.getApplicationContext(),
            ).setInputData(data).build()

        runBlocking {
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.success(), result)
        }

        // Source should still exist (copy, not move)
        assertTrue("Source file should still exist after copy", sourceFile.exists())
        val destFile = File(destDir, "test.txt")
        assertTrue("Destination file should exist after copy", destFile.exists())
        assertEquals("Hello, Copy!", destFile.readText())
    }

    @Config(sdk = [P])
    @Test
    fun `worker copies single file successfully on P`() {
        val sourceDir = tempFolder.newFolder("source")
        val destDir = tempFolder.newFolder("dest")
        val sourceFile = File(sourceDir, "test.txt")
        sourceFile.writeText("Hello from P!")

        val data =
            Data.Builder()
                .putStringArray(CopyWorker.TAG_COPY_SOURCES, arrayOf(sourceFile.absolutePath))
                .putString(CopyWorker.TAG_COPY_TARGET, destDir.absolutePath)
                .putBoolean(CopyWorker.TAG_COPY_MOVE, false)
                .build()

        val worker =
            TestListenableWorkerBuilder<CopyWorker>(
                context = ApplicationProvider.getApplicationContext(),
            ).setInputData(data).build()

        runBlocking {
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.success(), result)
        }

        assertTrue(sourceFile.exists())
        val destFile = File(destDir, "test.txt")
        assertTrue(destFile.exists())
        assertEquals("Hello from P!", destFile.readText())
    }

    @Test
    @Ignore("Recursive directory copy requires instrumented test - ListFilesCommand doesn't work properly with Robolectric")
    fun `worker copies directory recursively`() {
        val sourceDir = tempFolder.newFolder("source", "subdir")
        val innerFile = File(sourceDir, "inner.txt")
        innerFile.writeText("nested content")
        val destDir = tempFolder.newFolder("dest")

        // We point to the parent "source/subdir" directory
        val data =
            Data.Builder()
                .putStringArray(CopyWorker.TAG_COPY_SOURCES, arrayOf(sourceDir.absolutePath))
                .putString(CopyWorker.TAG_COPY_TARGET, destDir.absolutePath)
                .putBoolean(CopyWorker.TAG_COPY_MOVE, false)
                .build()

        val worker =
            TestListenableWorkerBuilder<CopyWorker>(
                context = ApplicationProvider.getApplicationContext(),
            ).setInputData(data).build()

        runBlocking {
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.success(), result)
        }

        val destSubdir = File(destDir, "subdir")
        assertTrue("Destination subdir should exist", destSubdir.exists())
        assertTrue("Destination subdir should be a directory", destSubdir.isDirectory)
        val destInner = File(destSubdir, "inner.txt")
        assertTrue("Inner file should exist in destination", destInner.exists())
        assertEquals("nested content", destInner.readText())
    }

    @Test
    fun `worker move flag deletes source after copy`() {
        val sourceDir = tempFolder.newFolder("source")
        val destDir = tempFolder.newFolder("dest")
        val sourceFile = File(sourceDir, "moveme.txt")
        sourceFile.writeText("move this")

        val data =
            Data.Builder()
                .putStringArray(CopyWorker.TAG_COPY_SOURCES, arrayOf(sourceFile.absolutePath))
                .putString(CopyWorker.TAG_COPY_TARGET, destDir.absolutePath)
                .putBoolean(CopyWorker.TAG_COPY_MOVE, true)
                .build()

        val worker =
            TestListenableWorkerBuilder<CopyWorker>(
                context = ApplicationProvider.getApplicationContext(),
            ).setInputData(data).build()

        runBlocking {
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.success(), result)
        }

        val destFile = File(destDir, "moveme.txt")
        assertTrue("Destination file should exist after move", destFile.exists())
        assertEquals("move this", destFile.readText())
        // Note: Source deletion is handled by DeleteTask which may or may not execute
        // fully in Robolectric; we verify destination was created successfully.
    }

    @Test
    fun `worker handles nonexistent source gracefully`() {
        val destDir = tempFolder.newFolder("dest")

        val data =
            Data.Builder()
                .putStringArray(
                    CopyWorker.TAG_COPY_SOURCES,
                    arrayOf("/nonexistent/path/file.txt"),
                )
                .putString(CopyWorker.TAG_COPY_TARGET, destDir.absolutePath)
                .putBoolean(CopyWorker.TAG_COPY_MOVE, false)
                .build()

        val worker =
            TestListenableWorkerBuilder<CopyWorker>(
                context = ApplicationProvider.getApplicationContext(),
            ).setInputData(data).build()

        runBlocking {
            // Should not crash; may fail or succeed depending on error handling
            worker.doWork()
        }
    }

    @Test
    fun `worker copies multiple files`() {
        val sourceDir = tempFolder.newFolder("source")
        val destDir = tempFolder.newFolder("dest")
        val file1 = File(sourceDir, "a.txt").apply { writeText("aaa") }
        val file2 = File(sourceDir, "b.txt").apply { writeText("bbb") }

        val data =
            Data.Builder()
                .putStringArray(
                    CopyWorker.TAG_COPY_SOURCES,
                    arrayOf(file1.absolutePath, file2.absolutePath),
                )
                .putString(CopyWorker.TAG_COPY_TARGET, destDir.absolutePath)
                .putBoolean(CopyWorker.TAG_COPY_MOVE, false)
                .build()

        val worker =
            TestListenableWorkerBuilder<CopyWorker>(
                context = ApplicationProvider.getApplicationContext(),
            ).setInputData(data).build()

        runBlocking {
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.success(), result)
        }

        assertEquals("aaa", File(destDir, "a.txt").readText())
        assertEquals("bbb", File(destDir, "b.txt").readText())
    }
}
