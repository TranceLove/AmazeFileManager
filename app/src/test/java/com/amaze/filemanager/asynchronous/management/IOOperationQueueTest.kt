package com.amaze.filemanager.asynchronous.management

import android.content.Intent
import android.os.Build
import android.os.Build.VERSION_CODES.LOLLIPOP
import android.os.Build.VERSION_CODES.P
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amaze.filemanager.fileoperations.filesystem.OpenMode
import com.amaze.filemanager.filesystem.HybridFile
import com.amaze.filemanager.filesystem.HybridFileParcelable
import com.amaze.filemanager.filesystem.Operations
import com.amaze.filemanager.shadows.ShadowMultiDex
import com.amaze.filemanager.ui.activities.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowMultiDex::class], sdk = [LOLLIPOP, P, Build.VERSION_CODES.R])
class IOOperationQueueTest {
    @Test
    fun addQueueStateListener_emitsSnapshotImmediately() {
        val snapshotReceived = CountDownLatch(1)
        val listener = IOOperationQueue.QueueStateListener { snapshotReceived.countDown() }

        IOOperationQueue.addQueueStateListener(listener)
        try {
            assertTrue(snapshotReceived.await(2, TimeUnit.SECONDS))
        } finally {
            IOOperationQueue.removeQueueStateListener(listener)
        }
    }

    @Test
    fun enqueue_mkdir_notifiesPendingRunningAndCompleted() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val invalidPath = context.cacheDir.absolutePath + "/invalid*" + System.nanoTime()
        val parentFile = HybridFile(OpenMode.FILE, context.cacheDir.absolutePath)
        val file = HybridFile(OpenMode.FILE, invalidPath)

        val seenStatuses = CopyOnWriteArrayList<IOOperationQueue.Status>()
        val completed = CountDownLatch(1)

        val listener =
            IOOperationQueue.QueueStateListener { entries ->
                entries
                    .firstOrNull {
                        val op = it.operation
                        op is IOOperation.Mkdir && op.file.path == invalidPath
                    }
                    ?.let {
                        seenStatuses.add(it.status)
                        if (it.status == IOOperationQueue.Status.COMPLETED) {
                            completed.countDown()
                        }
                    }
            }

        val callback =
            object : Operations.ErrorCallBack {
                override fun exists(file: HybridFile) = Unit

                override fun launchSAF(file: HybridFile) = Unit

                override fun launchSAF(
                    file: HybridFile,
                    file1: HybridFile,
                ) = Unit

                override fun done(
                    hFile: HybridFile,
                    b: Boolean,
                ) = Unit

                override fun invalidName(file: HybridFile) = Unit
            }

        IOOperationQueue.addQueueStateListener(listener)
        try {
            IOOperationQueue.enqueue(context, IOOperation.Mkdir(parentFile, file, false, callback))
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertTrue(seenStatuses.contains(IOOperationQueue.Status.PENDING))
            assertTrue(seenStatuses.contains(IOOperationQueue.Status.RUNNING))
            assertTrue(seenStatuses.contains(IOOperationQueue.Status.COMPLETED))
        } finally {
            IOOperationQueue.removeQueueStateListener(listener)
        }
    }

    @Test
    fun removeQueueStateListener_stopsFurtherNotifications() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val invalidPath = context.cacheDir.absolutePath + "/removed*" + System.nanoTime()
        val parentFile = HybridFile(OpenMode.FILE, context.cacheDir.absolutePath)
        val file = HybridFile(OpenMode.FILE, invalidPath)

        val notifiedAfterRemoval = CountDownLatch(1)
        val listener =
            IOOperationQueue.QueueStateListener { entries ->
                val hasTargetOperation =
                    entries.any {
                        val op = it.operation
                        op is IOOperation.Mkdir && op.file.path == invalidPath
                    }
                if (hasTargetOperation) {
                    notifiedAfterRemoval.countDown()
                }
            }

        val callback =
            object : Operations.ErrorCallBack {
                override fun exists(file: HybridFile) = Unit

                override fun launchSAF(file: HybridFile) = Unit

                override fun launchSAF(
                    file: HybridFile,
                    file1: HybridFile,
                ) = Unit

                override fun done(
                    hFile: HybridFile,
                    b: Boolean,
                ) = Unit

                override fun invalidName(file: HybridFile) = Unit
            }

        IOOperationQueue.addQueueStateListener(listener)
        IOOperationQueue.removeQueueStateListener(listener)

        IOOperationQueue.enqueue(context, IOOperation.Mkdir(parentFile, file, false, callback))

        assertFalse(notifiedAfterRemoval.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun failedOpsBroadcast_completesActiveOperationWithFailure() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val completion = CompletableDeferred<Result<Unit>>()

        val queueClass = IOOperationQueue::class.java
        val activeCompletionClass =
            Class.forName("com.amaze.filemanager.asynchronous.management.IOOperationQueue\$ActiveCompletion")
        val activeCompletionCtor =
            activeCompletionClass.getDeclaredConstructor(
                CompletableDeferred::class.java,
                String::class.java,
                String::class.java,
            )
        activeCompletionCtor.isAccessible = true
        val activeCompletion =
            activeCompletionCtor.newInstance(completion, MainActivity.KEY_INTENT_LOAD_LIST, null)

        val activeCompletionField = queueClass.getDeclaredField("activeCompletion")
        activeCompletionField.isAccessible = true

        val completionReceiverField = queueClass.getDeclaredField("completionReceiver")
        completionReceiverField.isAccessible = true
        val completionReceiver =
            completionReceiverField.get(IOOperationQueue) as android.content.BroadcastReceiver

        try {
            activeCompletionField.set(IOOperationQueue, activeCompletion)

            val failedBroadcast = Intent(MainActivity.TAG_INTENT_FILTER_GENERAL)
            failedBroadcast.putParcelableArrayListExtra(
                MainActivity.TAG_INTENT_FILTER_FAILED_OPS,
                arrayListOf(HybridFileParcelable("/failed/source")),
            )
            completionReceiver.onReceive(context, failedBroadcast)

            val result = runBlocking { completion.await() }
            assertTrue(result.isFailure)
        } finally {
            activeCompletionField.set(IOOperationQueue, null)
        }
    }
}
