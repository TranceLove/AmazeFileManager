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

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for [WorkerInteractionBridge].
 */
class WorkerInteractionBridgeTest {
    @After
    fun tearDown() {
        WorkerInteractionBridge.clearForTesting()
    }

    @Test
    fun `requestPassword returns supplied password`() =
        runTest {
            val workId = UUID.randomUUID()

            val deferred =
                async {
                    WorkerInteractionBridge.requestPassword(workId)
                }

            // Give the coroutine time to register the request
            delay(50)
            assertTrue(WorkerInteractionBridge.hasPendingRequest(workId))

            WorkerInteractionBridge.supplyPassword(workId, "secret123")
            assertEquals("secret123", deferred.await())
        }

    @Test
    fun `cancel returns null password`() =
        runTest {
            val workId = UUID.randomUUID()

            val deferred =
                async {
                    WorkerInteractionBridge.requestPassword(workId)
                }

            delay(50)
            assertTrue(WorkerInteractionBridge.hasPendingRequest(workId))

            WorkerInteractionBridge.cancel(workId)
            assertNull(deferred.await())
        }

    @Test
    fun `hasPendingRequest returns false for unknown id`() {
        assertFalse(WorkerInteractionBridge.hasPendingRequest(UUID.randomUUID()))
    }

    @Test
    fun `clearForTesting removes all pending requests`() =
        runTest {
            val id1 = UUID.randomUUID()
            val id2 = UUID.randomUUID()

            val d1 = async { WorkerInteractionBridge.requestPassword(id1) }
            val d2 = async { WorkerInteractionBridge.requestPassword(id2) }

            delay(50)
            assertTrue(WorkerInteractionBridge.hasPendingRequest(id1))
            assertTrue(WorkerInteractionBridge.hasPendingRequest(id2))

            WorkerInteractionBridge.clearForTesting()
            assertFalse(WorkerInteractionBridge.hasPendingRequest(id1))
            assertFalse(WorkerInteractionBridge.hasPendingRequest(id2))

            // Cancel deferred to clean up (they'll throw CancellationException)
            d1.cancel()
            d2.cancel()
        }
}
