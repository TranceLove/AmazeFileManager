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

package com.amaze.filemanager.filesystem.files

import com.amaze.filemanager.fileoperations.utils.UpdatePosition
import com.amaze.filemanager.test.DummyFileGenerator
import com.amaze.filemanager.utils.ProgressHandler
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.Channels
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Tests for [GenericCopyUtil]
 */
@Suppress("StringLiteralDuplication")
@RunWith(RobolectricTestRunner::class)
class GenericCopyUtilTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var progressHandler: ProgressHandler
    private lateinit var copyUtil: GenericCopyUtil
    private lateinit var file1: File
    private lateinit var file2: File

    /**
     * Pre-test setup.
     */
    @Before
    fun setUp() {
        progressHandler = ProgressHandler()
        copyUtil = GenericCopyUtil(RuntimeEnvironment.getApplication(), progressHandler)
        file1 = tempFolder.newFile("test1.bin")
        file2 = tempFolder.newFile("test2.bin")
    }

    /**
     * Post test clean up.
     */
    @After
    fun tearDown() {
        try {
            if (file1.exists()) file1.delete()
            if (file2.exists()) file2.delete()
        } catch (_: Exception) {
            // Ignore cleanup errors - TemporaryFolder will handle remaining files
        }
    }

    /**
     * Test copy small file
     */
    @Test
    fun testDoCopySmallFile() {
        verifyDoCopy(512)
    }

    /**
     * Test copy large file
     */
    @Test
    fun testDoCopyLargeFile() {
        verifyDoCopy(10 * 1024 * 1024)
    }

    /**
     * Test copy empty file
     */
    @Test
    fun testDoCopyEmptyFile() {
        verifyDoCopy(0)
    }

    private fun verifyDoCopy(size: Int) {
        val checksum = DummyFileGenerator.createFile(file1, size)
        val progressUpdates = mutableListOf<Long>()
        val updatePosition = UpdatePosition { progressUpdates.add(it) }
        FileInputStream(file1).channel.use { fin ->
            Channels.newChannel(FileOutputStream(file2)).use { fout ->
                copyUtil.doCopy(
                    fin,
                    fout,
                    updatePosition,
                )
            }
        }
        assertEquals(file1.length(), file2.length())
        if (size > 0) {
            assertSha1Equals(checksum, file2)
        }
        assertEquals("Progress sum should equal file size", file1.length(), progressUpdates.sum())
    }

    /**
     * Test copy small file using FileChannel
     */
    @Test
    fun testCopyFileChannelSmallFile() {
        verifyCopyFileChannel(512)
    }

    /**
     * Test copy large file using FileChannel
     */
    @Test
    fun testCopyFileChannelLargeFile() {
        verifyCopyFileChannel(10 * 1024 * 1024)
    }

    /**
     * Test copy empty file using FileChannel
     */
    @Test
    fun testCopyFileChannelEmptyFile() {
        verifyCopyFileChannel(0)
    }

    private fun verifyCopyFileChannel(size: Int) {
        val checksum = DummyFileGenerator.createFile(file1, size)
        val progressUpdates = mutableListOf<Long>()
        val updatePosition = UpdatePosition { progressUpdates.add(it) }
        FileInputStream(file1).channel.use { fin ->
            FileOutputStream(file2).channel.use { fout ->
                copyUtil.copyFile(
                    fin,
                    fout,
                    updatePosition,
                )
            }
        }
        assertEquals(file1.length(), file2.length())
        if (size > 0) {
            assertSha1Equals(checksum, file2)
        }
        assertEquals("Progress sum should equal file size", file1.length(), progressUpdates.sum())
    }

    /**
     * Test copy small file using Buffered Streams
     */
    @Test
    fun testCopyBufferedStreamsSmallFile() {
        verifyCopyBufferedStreams(512)
    }

    /**
     * Test copy large file using Buffered Streams
     */
    @Test
    fun testCopyBufferedStreamsLargeFile() {
        verifyCopyBufferedStreams(10 * 1024 * 1024) // 10 MB
    }

    /**
     * Test copy empty file using Buffered Streams
     */
    @Test
    fun testCopyBufferedStreamsEmptyFile() {
        verifyCopyBufferedStreams(0)
    }

    private fun verifyCopyBufferedStreams(size: Int) {
        val checksum = DummyFileGenerator.createFile(file1, size)
        val progressUpdates = mutableListOf<Long>()
        val updatePosition = UpdatePosition { progressUpdates.add(it) }
        BufferedInputStream(FileInputStream(file1)).use { fin ->
            BufferedOutputStream(FileOutputStream(file2)).use { fout ->
                copyUtil.copyFile(
                    fin,
                    fout,
                    updatePosition,
                )
            }
        }
        assertEquals(file1.length(), file2.length())
        if (size > 0) {
            assertSha1Equals(checksum, file2)
        }
        assertEquals("Progress sum should equal file size", file1.length(), progressUpdates.sum())
    }

    /**
     * Test copy small file using FileChannel to BufferedOutputStream
     */
    @Test
    fun testCopyFileChannelToBufferedOutputStreamSmallFile() {
        verifyCopyFileChannelToBufferedOutputStream(512)
    }

    /**
     * Test copy large file using FileChannel to BufferedOutputStream
     */
    @Test
    fun testCopyFileChannelToBufferedOutputStreamLargeFile() {
        verifyCopyFileChannelToBufferedOutputStream(10 * 1024 * 1024) // 10 MB
    }

    /**
     * Test copy empty file using FileChannel to BufferedOutputStream
     */
    @Test
    fun testCopyFileChannelToBufferedOutputStreamEmptyFile() {
        verifyCopyFileChannelToBufferedOutputStream(0)
    }

    private fun verifyCopyFileChannelToBufferedOutputStream(size: Int) {
        val checksum = DummyFileGenerator.createFile(file1, size)
        val progressUpdates = mutableListOf<Long>()
        val updatePosition = UpdatePosition { progressUpdates.add(it) }
        FileInputStream(file1).channel.use { fin ->
            BufferedOutputStream(FileOutputStream(file2)).use { fout ->
                copyUtil.copyFile(
                    fin,
                    fout,
                    updatePosition,
                )
            }
        }
        assertEquals(file1.length(), file2.length())
        if (size > 0) {
            assertSha1Equals(checksum, file2)
        }
        assertEquals("Progress sum should equal file size", file1.length(), progressUpdates.sum())
    }

    /**
     * Test copy small file using BufferedInputStream to FileChannel
     */
    @Test
    fun testCopyBufferedInputStreamToFileChannelSmallFile() {
        verifyCopyBufferedInputStreamToFileChannel(512)
    }

    /**
     * Test copy large file using BufferedInputStream to FileChannel
     */
    @Test
    fun testCopyBufferedInputStreamToFileChannelLargeFile() {
        verifyCopyBufferedInputStreamToFileChannel(10 * 1024 * 1024)
    }

    private fun verifyCopyBufferedInputStreamToFileChannel(size: Int) {
        val checksum = DummyFileGenerator.createFile(file1, size)
        val progressUpdates = mutableListOf<Long>()
        val updatePosition = UpdatePosition { progressUpdates.add(it) }
        BufferedInputStream(FileInputStream(file1)).use { fin ->
            FileOutputStream(file2).channel.use { fout ->
                copyUtil.copyFile(
                    fin,
                    fout,
                    updatePosition,
                )
            }
        }
        assertEquals(file1.length(), file2.length())
        if (size > 0) {
            assertSha1Equals(checksum, file2)
        }
        assertEquals("Progress sum should equal file size", file1.length(), progressUpdates.sum())
    }

    /**
     * Test cancellation of copy operation
     */
    @Test
    fun testCancellation() {
        // Create a larger file so there's time to cancel
        val size = 10 * 1024 * 1024
        DummyFileGenerator.createFile(file1, size)

        progressHandler.setCancelled(true)

        val progressUpdates = mutableListOf<Long>()
        val updatePosition = UpdatePosition { progressUpdates.add(it) }
        FileInputStream(file1).channel.use { fin ->
            Channels.newChannel(FileOutputStream(file2)).use { fout ->
                copyUtil.doCopy(
                    fin,
                    fout,
                    updatePosition,
                )
            }
        }

        // When cancelled before starting, nothing or very little should be copied
        assertTrue(
            "Cancelled copy should write less than full size",
            file2.length() < file1.length(),
        )
    }

    /**
     * Test progress updates batched for large files
     */
    @Test
    fun testBatchedProgressLargeFileFileChannelPath() {
        val size = 10 * 1024 * 1024 // 10 MB
        DummyFileGenerator.createFile(file1, size)
        val progressUpdates = mutableListOf<Long>()
        val updatePosition = UpdatePosition { progressUpdates.add(it) }
        FileInputStream(file1).channel.use { fin ->
            FileOutputStream(file2).channel.use { fout ->
                copyUtil.copyFile(
                    fin,
                    fout,
                    updatePosition,
                )
            }
        }
        assertEquals("Progress sum should equal file size", file1.length(), progressUpdates.sum())
        // With 10 MB file and 4 MB threshold, we expect ~2-3 callbacks, not 10
        assertTrue(
            "Batched progress should have fewer callbacks than unbatched (got ${progressUpdates.size})",
            progressUpdates.size <= 5,
        )
    }

    /**
     * Test progress updates batched for large files when using ByteBuffer copy path
     */
    @Test
    fun testBatchedProgressLargeFileByteBufferPath() {
        val size = 10 * 1024 * 1024 // 10 MB
        DummyFileGenerator.createFile(file1, size)
        val progressUpdates = mutableListOf<Long>()
        val updatePosition = UpdatePosition { progressUpdates.add(it) }
        FileInputStream(file1).channel.use { fin ->
            Channels.newChannel(FileOutputStream(file2)).use { fout ->
                copyUtil.doCopy(
                    fin,
                    fout,
                    updatePosition,
                )
            }
        }
        assertEquals("Progress sum should equal file size", file1.length(), progressUpdates.sum())
        // With 10 MB file and 4 MB threshold, we expect ~2-3 callbacks, not 10
        assertTrue(
            "Batched progress should have fewer callbacks than unbatched (got ${progressUpdates.size})",
            progressUpdates.size <= 5,
        )
    }

    private fun assertSha1Equals(
        expected: ByteArray,
        file: File,
    ) {
        val md = MessageDigest.getInstance("SHA-1")
        DigestInputStream(FileInputStream(file), md).use { din ->
            val buffer = ByteArray(GenericCopyUtil.DEFAULT_BUFFER_SIZE)
            while (din.read(buffer) > -1) { /* consume */ }
        }
        assertArrayEquals(expected, md.digest())
    }
}
