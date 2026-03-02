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

import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton bridge for coroutine-based interaction between workers and UI.
 * Used primarily by [ExtractWorker] to request passwords from the user.
 *
 * The worker suspends on [requestPassword] while the UI collects user input
 * and completes the deferred via [supplyPassword] or [cancel].
 */
object WorkerInteractionBridge {
    private val pendingRequests = ConcurrentHashMap<UUID, CompletableDeferred<String?>>()

    /**
     * Called by a worker to request a password from the user.
     * Suspends until the UI supplies a password or cancels.
     *
     * @param workId the unique work ID of the requesting worker
     * @return the password supplied by the user, or null if cancelled
     */
    suspend fun requestPassword(workId: UUID): String? {
        val deferred = CompletableDeferred<String?>()
        pendingRequests[workId] = deferred
        return deferred.await()
    }

    /**
     * Called by the UI to supply a password to a waiting worker.
     *
     * @param workId the unique work ID of the worker
     * @param password the password entered by the user
     */
    fun supplyPassword(
        workId: UUID,
        password: String?,
    ) {
        pendingRequests.remove(workId)?.complete(password)
    }

    /**
     * Called by the UI to cancel a pending password request.
     *
     * @param workId the unique work ID of the worker
     */
    fun cancel(workId: UUID) {
        pendingRequests.remove(workId)?.complete(null)
    }

    /**
     * Check if there is a pending password request for the given work ID.
     */
    fun hasPendingRequest(workId: UUID): Boolean = pendingRequests.containsKey(workId)

    /**
     * Test-only method to clear all pending requests between tests.
     */
    fun clearForTesting() {
        pendingRequests.keys.forEach { key ->
            pendingRequests.remove(key)?.complete(null)
        }
    }
}
