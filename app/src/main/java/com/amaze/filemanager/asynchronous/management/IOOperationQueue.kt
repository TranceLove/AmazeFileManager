package com.amaze.filemanager.asynchronous.management

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import com.amaze.filemanager.application.AppConfig
import com.amaze.filemanager.asynchronous.asynctasks.DeleteTask
import com.amaze.filemanager.asynchronous.services.CopyService
import com.amaze.filemanager.asynchronous.services.ExtractService
import com.amaze.filemanager.asynchronous.services.ZipService
import com.amaze.filemanager.filesystem.Operations
import com.amaze.filemanager.filesystem.files.EncryptDecryptUtils
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.utils.registerReceiverCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

object IOOperationQueue {
    fun interface QueueStateListener {
        fun onQueueStateChanged(entries: List<QueueEntry>)
    }

    enum class Status {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
    }

    data class QueueEntry(
        val id: Long,
        val operation: IOOperation,
        val status: Status,
        val error: String? = null,
        val lastUpdatedAtMillis: Long = System.currentTimeMillis(),
    )

    private data class EnqueuedOperation(
        val id: Long,
        val context: Context,
        val operation: IOOperation,
        val completion: CompletableDeferred<Result<Unit>>,
    )

    private data class ActiveCompletion(
        val completion: CompletableDeferred<Result<Unit>>,
        val expectedAction: String,
        val expectedPath: String?,
    )

    private val operationIdGenerator = AtomicLong(0)
    private val queue = Channel<EnqueuedOperation>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueEntries = MutableStateFlow<List<QueueEntry>>(emptyList())
    private val queueStateListeners = LinkedHashSet<QueueStateListener>()

    private val stateLock = Any()

    @Volatile private var activeCompletion: ActiveCompletion? = null

    @Volatile private var isInitialized = false

    val queueState: StateFlow<List<QueueEntry>> = queueEntries.asStateFlow()

    @JvmStatic
    fun addQueueStateListener(listener: QueueStateListener) {
        val snapshot =
            synchronized(stateLock) {
                queueStateListeners.add(listener)
                queueEntries.value
            }
        listener.onQueueStateChanged(snapshot)
    }

    @JvmStatic
    fun removeQueueStateListener(listener: QueueStateListener) {
        synchronized(stateLock) { queueStateListeners.remove(listener) }
    }

    private val completionReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val active = activeCompletion ?: return
                val action = intent.action ?: return

                if (action == MainActivity.TAG_INTENT_FILTER_GENERAL) {
                    @Suppress("DEPRECATION")
                    val failedOps =
                        intent.getParcelableArrayListExtra<Parcelable>(
                            MainActivity.TAG_INTENT_FILTER_FAILED_OPS,
                        )
                    if (!failedOps.isNullOrEmpty()) {
                        active.completion.complete(
                            Result.failure(
                                IllegalStateException(
                                    "Operation failed with ${failedOps.size} failed item(s)",
                                ),
                            ),
                        )
                    }
                    return
                }

                if (action != active.expectedAction) {
                    return
                }

                if (active.expectedPath != null) {
                    val actionPath = intent.getStringExtra(MainActivity.KEY_INTENT_LOAD_LIST_FILE)
                    if (actionPath != active.expectedPath) {
                        return
                    }
                }

                active.completion.complete(Result.success(Unit))
            }
        }

    @JvmStatic
    fun enqueue(
        context: Context?,
        operation: IOOperation,
    ) {
        val appContext = context?.applicationContext ?: AppConfig.getInstance().applicationContext
        val id = operationIdGenerator.incrementAndGet()

        val deferred = CompletableDeferred<Result<Unit>>()
        val node = EnqueuedOperation(id, appContext, operation, deferred)

        initializeIfNeeded(appContext)
        addEntry(id, operation, Status.PENDING)
        queue.trySend(node)
    }

    private fun initializeIfNeeded(context: Context) {
        synchronized(stateLock) {
            if (isInitialized) {
                return
            }
            context.registerReceiverCompat(
                completionReceiver,
                IntentFilter(MainActivity.TAG_INTENT_FILTER_GENERAL),
            )
            context.registerReceiverCompat(
                completionReceiver,
                IntentFilter(MainActivity.KEY_INTENT_LOAD_LIST),
            )
            context.registerReceiverCompat(
                completionReceiver,
                IntentFilter(EncryptDecryptUtils.DECRYPT_BROADCAST),
            )

            scope.launch { consumeQueue() }
            isInitialized = true
        }
    }

    private suspend fun consumeQueue() {
        for (operation in queue) {
            updateStatus(operation.id, Status.RUNNING)
            val result = runCatching { runOperation(operation) }
            result.onSuccess {
                updateStatus(operation.id, Status.COMPLETED)
                operation.completion.complete(Result.success(Unit))
            }
            result.onFailure {
                updateStatus(operation.id, Status.FAILED, it.message)
                operation.completion.complete(Result.failure(it))
            }
            synchronized(stateLock) {
                activeCompletion = null
            }
        }
    }

    private suspend fun runOperation(operation: EnqueuedOperation) {
        when (val ioOperation = operation.operation) {
            is IOOperation.Copy -> runServiceOperation(operation, ioOperation.intent)
            is IOOperation.Move -> runServiceOperation(operation, ioOperation.intent)
            is IOOperation.Extract -> runServiceOperation(operation, ioOperation.intent)
            is IOOperation.Compress -> runServiceOperation(operation, ioOperation.intent)
            is IOOperation.Encrypt -> runServiceOperation(operation, ioOperation.intent)
            is IOOperation.Decrypt -> runServiceOperation(operation, ioOperation.intent)
            is IOOperation.Mkdir ->
                Operations.runMkdirOperation(
                    operation.context,
                    ioOperation.parentFile,
                    ioOperation.file,
                    ioOperation.rootMode,
                    ioOperation.errorCallBack,
                )
            is IOOperation.MkFile ->
                Operations.runMkFileOperation(
                    operation.context,
                    ioOperation.parentFile,
                    ioOperation.file,
                    ioOperation.rootMode,
                    ioOperation.errorCallBack,
                )
            is IOOperation.Rename ->
                Operations.runRenameOperation(
                    operation.context,
                    ioOperation.oldFile,
                    ioOperation.newFile,
                    ioOperation.rootMode,
                    ioOperation.errorCallBack,
                )
            is IOOperation.Delete ->
                DeleteTask.runDeleteOperation(
                    operation.context,
                    ioOperation.files,
                    ioOperation.doDeletePermanently,
                    ioOperation.compressedExplorerFragment,
                )
        }
    }

    private suspend fun runServiceOperation(
        operation: EnqueuedOperation,
        intent: Intent,
    ) {
        val (expectedAction, expectedPath) = completionSignal(operation.operation, intent)

        synchronized(stateLock) {
            activeCompletion = ActiveCompletion(operation.completion, expectedAction, expectedPath)
        }

        ServiceWatcherUtil.runService(operation.context, intent)
        operation.completion.await().getOrThrow()
    }

    private fun completionSignal(
        operation: IOOperation,
        intent: Intent,
    ): Pair<String, String?> {
        return when (operation) {
            is IOOperation.Copy,
            is IOOperation.Move,
            ->
                Pair(
                    MainActivity.KEY_INTENT_LOAD_LIST,
                    intent.getStringExtra(CopyService.TAG_COPY_TARGET),
                )
            is IOOperation.Extract ->
                Pair(
                    MainActivity.KEY_INTENT_LOAD_LIST,
                    intent.getStringExtra(ExtractService.KEY_PATH_EXTRACT),
                )
            is IOOperation.Compress ->
                Pair(
                    MainActivity.KEY_INTENT_LOAD_LIST,
                    intent.getStringExtra(ZipService.KEY_COMPRESS_PATH),
                )
            is IOOperation.Encrypt -> Pair(MainActivity.KEY_INTENT_LOAD_LIST, "")
            is IOOperation.Decrypt -> Pair(EncryptDecryptUtils.DECRYPT_BROADCAST, null)
            is IOOperation.Mkdir,
            is IOOperation.MkFile,
            is IOOperation.Rename,
            is IOOperation.Delete,
            -> Pair("", null)
        }
    }

    private fun addEntry(
        id: Long,
        operation: IOOperation,
        status: Status,
    ) {
        val updatedEntries: List<QueueEntry>
        val listeners: List<QueueStateListener>
        val now = System.currentTimeMillis()
        synchronized(stateLock) {
            updatedEntries =
                queueEntries.value +
                QueueEntry(id, operation, status, lastUpdatedAtMillis = now)
            queueEntries.value = updatedEntries
            listeners = queueStateListeners.toList()
        }
        listeners.forEach { it.onQueueStateChanged(updatedEntries) }
    }

    private fun updateStatus(
        id: Long,
        status: Status,
        error: String? = null,
    ) {
        val updatedEntries: List<QueueEntry>
        val listeners: List<QueueStateListener>
        val now = System.currentTimeMillis()
        synchronized(stateLock) {
            updatedEntries =
                queueEntries.value.map {
                    if (it.id == id) {
                        it.copy(status = status, error = error, lastUpdatedAtMillis = now)
                    } else {
                        it
                    }
                }
            queueEntries.value = updatedEntries
            listeners = queueStateListeners.toList()
        }
        listeners.forEach { it.onQueueStateChanged(updatedEntries) }
    }
}
