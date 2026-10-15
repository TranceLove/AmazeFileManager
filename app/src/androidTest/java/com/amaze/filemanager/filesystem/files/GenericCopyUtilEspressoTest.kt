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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amaze.filemanager.fileoperations.utils.UpdatePosition
import com.amaze.filemanager.test.DummyFileGenerator
import com.amaze.filemanager.utils.ProgressHandler
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.Channels
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Instrumented tests for [GenericCopyUtil] to verify the correctness of file copying
 * and progress updates.
 */
@Suppress("StringLiteralDuplication")
@RunWith(AndroidJUnit4::class)
class GenericCopyUtilEspressoTest {
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
        copyUtil =
            GenericCopyUtil(
                InstrumentationRegistry.getInstrumentation().targetContext,
                progressHandler,
            )
        file1 = File.createTempFile("test", "bin").also { it.deleteOnExit() }
        file2 = File.createTempFile("test", "bin").also { it.deleteOnExit() }
    }

    /**
     * Post test clean up.
     */
    @After
    fun tearDown() {
        if (file1.exists()) file1.delete()
        if (file2.exists()) file2.delete()
    }

    /**
     * Test doCopy with small file
     */
    @Test
    fun testDoCopySmallFile() {
        verifyDoCopy(512)
    }

    /**
     * Test doCopy with large file
     */
    @Test
    fun testDoCopyLargeFile() {
        verifyDoCopy(10 * 1024 * 1024)
    }

    /**
     * Test doCopy with empty file
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
     * Test copyFile(FileChannel, FileChannel) with small file
     */
    @Test
    fun testCopyFileChannelSmallFile() {
        verifyCopyFileChannel(512)
    }

    /**
     * Test copyFile(FileChannel, FileChannel) with large file
     */
    @Test
    fun testCopyFileChannelLargeFile() {
        verifyCopyFileChannel(10 * 1024 * 1024)
    }

    /**
     * Test copyFile(FileChannel, FileChannel) with empty file
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
     * Test copyFile(BufferedInputStream, BufferedOutputStream) with small file
     */
    @Test
    fun testCopyBufferedStreamsSmallFile() {
        verifyCopyBufferedStreams(512)
    }

    /**
     * Test copyFile(BufferedInputStream, BufferedOutputStream) with large file
     */
    @Test
    fun testCopyBufferedStreamsLargeFile() {
        verifyCopyBufferedStreams(10 * 1024 * 1024)
    }

    /**
     * Test copyFile(BufferedInputStream, BufferedOutputStream) with empty file
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
     * Test copyFile(FileChannel, BufferedOutputStream) for small files
     */
    @Test
    fun testCopyFileChannelToBufferedOutputStreamSmallFile() {
        verifyCopyFileChannelToBufferedOutputStream(512)
    }

    /**
     * Test copyFile(FileChannel, BufferedOutputStream) for large files
     */
    @Test
    fun testCopyFileChannelToBufferedOutputStreamLargeFile() {
        verifyCopyFileChannelToBufferedOutputStream(10 * 1024 * 1024)
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
     * Test copy cancelled
     */
    @Test
    fun testCancellation() {
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

        assertTrue(
            "Cancelled copy should write less than full size",
            file2.length() < file1.length(),
        )
    }

    /**
     * Test when copying a large file using the transferTo path, progress updates are batched
     */
    @Test
    fun testBatchedProgress_transferToPath() {
        val size = 10 * 1024 * 1024
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
        assertTrue(
            "Batched progress should have fewer callbacks (got ${progressUpdates.size})",
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
