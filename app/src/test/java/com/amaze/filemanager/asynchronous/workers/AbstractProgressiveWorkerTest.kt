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

import androidx.work.Data
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [AbstractProgressiveWorker] static utilities.
 */
class AbstractProgressiveWorkerTest {
    @Test
    fun `toDatapointParcelable converts progress Data correctly`() {
        val data =
            Data.Builder()
                .putInt(AbstractProgressiveWorker.KEY_SERVICE_TYPE, AbstractProgressiveWorker.SERVICE_COPY)
                .putString(AbstractProgressiveWorker.KEY_FILE_NAME, "test.txt")
                .putLong(AbstractProgressiveWorker.KEY_TOTAL_SIZE, 1024L)
                .putLong(AbstractProgressiveWorker.KEY_BYTE_PROGRESS, 512L)
                .putLong(AbstractProgressiveWorker.KEY_SPEED_RAW, 100L)
                .build()

        val dp = data.toDatapointParcelable()
        assertNotNull(dp)
        assertEquals("test.txt", dp!!.name)
        assertEquals(1024L, dp.totalSize)
        assertEquals(512L, dp.byteProgress)
        assertEquals(100L, dp.speedRaw)
    }

    @Test
    fun `toDatapointParcelable returns null for empty Data`() {
        val data = Data.EMPTY
        val dp = data.toDatapointParcelable()
        assertNull(dp)
    }

    @Test
    fun `toDatapointParcelable handles missing optional fields`() {
        val data =
            Data.Builder()
                .putString(AbstractProgressiveWorker.KEY_FILE_NAME, "minimal.txt")
                .build()

        val dp = data.toDatapointParcelable()
        assertNotNull(dp)
        assertEquals("minimal.txt", dp?.name)
        assertEquals(0L, dp?.totalSize)
        assertEquals(0L, dp?.byteProgress)
    }
}
