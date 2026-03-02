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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.amaze.filemanager.fileoperations.filesystem.OpenMode
import com.amaze.filemanager.shadows.ShadowMultiDex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for [DecryptWorker].
 *
 * These are lightweight tests that verify input validation and failure cases.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [LOLLIPOP], shadows = [ShadowMultiDex::class])
class DecryptWorkerTest {
    @Test
    fun `worker fails with missing source path`() {
        val data =
            Data.Builder()
                .putString(DecryptWorker.TAG_DECRYPT_PATH, "/tmp/output")
                .putInt(
                    AbstractProgressiveWorker.KEY_SERVICE_TYPE,
                    AbstractProgressiveWorker.SERVICE_DECRYPT,
                )
                .build()

        val worker =
            TestListenableWorkerBuilder<DecryptWorker>(
                context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            ).setInputData(data).build()

        runBlocking {
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }
    }

    @Test
    fun `worker fails with non-existent source file`() {
        val data =
            Data.Builder()
                .putString(DecryptWorker.TAG_SOURCE_PATH, "/nonexistent/file.aze")
                .putString(DecryptWorker.TAG_SOURCE_NAME, "file.aze")
                .putLong(DecryptWorker.TAG_SOURCE_SIZE, 100L)
                .putBoolean(DecryptWorker.TAG_SOURCE_DIRECTORY, false)
                .putInt(DecryptWorker.TAG_SOURCE_MODE, OpenMode.FILE.ordinal)
                .putString(DecryptWorker.TAG_DECRYPT_PATH, "/tmp/output")
                .putString(DecryptWorker.TAG_PASSWORD, "password123")
                .putInt(
                    AbstractProgressiveWorker.KEY_SERVICE_TYPE,
                    AbstractProgressiveWorker.SERVICE_DECRYPT,
                )
                .build()

        val worker =
            TestListenableWorkerBuilder<DecryptWorker>(
                context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            ).setInputData(data).build()

        runBlocking {
            val result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }
    }
}
