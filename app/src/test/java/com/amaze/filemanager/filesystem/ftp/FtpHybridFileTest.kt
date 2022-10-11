/*
 * Copyright (C) 2014-2022 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
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

package com.amaze.filemanager.filesystem.ftp

import android.os.Build.VERSION_CODES.P
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import com.amaze.filemanager.application.AppConfig
import com.amaze.filemanager.fileoperations.filesystem.OpenMode
import com.amaze.filemanager.filesystem.HybridFile
import com.amaze.filemanager.filesystem.Operations
import com.amaze.filemanager.filesystem.OperationsTest
import com.amaze.filemanager.shadows.ShadowMultiDex
import com.amaze.filemanager.test.ShadowPasswordUtil
import org.awaitility.Awaitility.await
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [P],
    shadows = [ShadowPasswordUtil::class, ShadowMultiDex::class]
)
@Ignore
open class FtpHybridFileTest : AbstractFtpServerTestBase() {

    protected lateinit var tmpFile: File
    protected lateinit var hybridFile: HybridFile

    @Before
    override fun setUp() {
        super.setUp()
        tmpFile = File.createTempFile("test", ".bin")
        tmpFile.deleteOnExit()

        hybridFile = HybridFile(OpenMode.FTP, ftpUrl)
    }

    /**
     * Shutdown FTP server.
     */
    @After
    override fun tearDown() {
        if (tmpFile.exists()) {
            tmpFile.delete()
        }
        super.tearDown()
    }

//    /**
//     * Test list files
//     *
//     * @see HybridFile.forEachChildrenFile
//     * @see HybridFile.listFiles
//     */
//    @Test
//    @FlakyTest()
//    fun testListFile() {
//        val files = hybridFile.listFiles(AppConfig.getInstance(), false)
//        assertEquals(FtpServiceAndroidFileSystemIntegrationTest.directories.size, files.size)
//    }
//
    /**
     * Test create file
     *
     * @see Operations.mkfile
     */
    @Test
    @FlakyTest()
    fun testMkFile() {
        val newFile = HybridFile(OpenMode.FTP, "$ftpUrl/${tmpFile.name}")
        val latch = CountDownLatch(1)
        Operations.mkfile(
            hybridFile,
            newFile,
            AppConfig.getInstance(),
            false,
            object : OperationsTest.AbstractErrorCallback() {
                override fun done(file: HybridFile?, b: Boolean) {
                    assertTrue(true == file?.exists())
                    assertEquals(newFile.path, file?.path)
                    assertNotNull(file?.ftpFile)
                    latch.countDown()
                }
            }
        )
        latch.await()
    }

    /**
     * Test rename file
     *
     * @see Operations.rename
     */
    @Test
    @FlakyTest()
    fun testRenameFile() {
        val oldFile = HybridFile(OpenMode.FTP, "$ftpUrl/${tmpFile.name}")
        val newFile = HybridFile(OpenMode.FTP, "$ftpUrl/${tmpFile.name}-new")
        var latch = CountDownLatch(1)
        Operations.mkfile(
            hybridFile,
            oldFile,
            AppConfig.getInstance(),
            false,
            object : OperationsTest.AbstractErrorCallback() {
                override fun done(file: HybridFile?, b: Boolean) {
                    assertTrue(true == file?.exists())
                    assertEquals(oldFile.path, file?.path)
                    assertNotNull(file?.ftpFile)
                    latch.countDown()
                }
            }
        )
        latch.await()
        latch = CountDownLatch(1)
        Operations.rename(
            oldFile,
            newFile,
            false,
            AppConfig.getInstance(),
            object : OperationsTest.AbstractErrorCallback() {
                override fun done(file: HybridFile?, b: Boolean) {
                    assertTrue(true == file?.exists())
                    assertFalse(oldFile.exists())
                    assertTrue(newFile.exists())
                    assertEquals(newFile.path, file?.path)
                    assertNotNull(file?.ftpFile)
                    latch.countDown()
                }
            }
        )
        latch.await()
    }

    /**
     * Test file I/O.
     *
     * @see HybridFile.getOutputStream
     * @see HybridFile.getInputStream
     */
    @Test
    @FlakyTest()
    fun testFileIO() {
        val randomBytes = Random(System.currentTimeMillis()).nextBytes(32)
        val f = HybridFile(
            OpenMode.FTP,
            "$ftpUrl/${tmpFile.name}"
        )
        f.getOutputStream(AppConfig.getInstance())?.run {
            ByteArrayInputStream(randomBytes).copyTo(this)
            this.close()
        } ?: fail("Unable to get OutputStream")
        await().atMost(10, TimeUnit.SECONDS).until {
            randomBytes.size.toLong() == f.length(AppConfig.getInstance())
        }
        f.getInputStream(AppConfig.getInstance())?.run {
            val verify = this.readBytes()
            assertArrayEquals(randomBytes, verify)
        } ?: fail("Unable to get InputStream")
    }

    /**
     * Test create dir.
     *
     * @see Operations.mkdir
     */
    @Test
    @FlakyTest()
    fun testMkdir() {
        for (
            dir: String in arrayOf(
                "newfolder",
                "new folder 2",
                "new%20folder%203",
                "あいうえお",
                "multiple/levels/down the pipe"
            )
        ) {
            val newFile = HybridFile(OpenMode.FTP, "$ftpUrl/$dir")
            val latch = CountDownLatch(1)
            Operations.mkdir(
                hybridFile,
                newFile,
                AppConfig.getInstance(),
                false,
                object : OperationsTest.AbstractErrorCallback() {
                    override fun done(file: HybridFile?, b: Boolean) {
                        assertTrue(true == file?.exists())
                        assertEquals(newFile.path, file?.path)
                        latch.countDown()
                    }
                }
            )
            latch.await()
        }
    }

    protected open fun beforeCreateFtpServer() = Unit
}
