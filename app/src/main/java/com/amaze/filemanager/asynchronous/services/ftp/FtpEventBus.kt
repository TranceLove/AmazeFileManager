package com.amaze.filemanager.asynchronous.services.ftp

import com.amaze.filemanager.ui.fragments.FtpServerFragment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Replacement event bus to handle [FtpService] events using Kotlin's Flow.
 *
 * Original idea: https://mirchev.medium.com/its-21st-century-stop-using-eventbus-3ff5d9c6a00f
 *
 * @see [FtpService]
 * @see [FtpTileService]
 * @see [FtpServerFragment]
 */
object FtpEventBus {
    private val _events = MutableSharedFlow<FtpService.FtpReceiverActions>(replay = 0)
    val events = _events.asSharedFlow()

    /**
     * Emit the event signal to the event bus as [MutableSharedFlow].
     *
     * @param event The event to be emitted.
     */
    suspend fun emit(event: FtpService.FtpReceiverActions) {
        _events.emit(event)
    }
}
