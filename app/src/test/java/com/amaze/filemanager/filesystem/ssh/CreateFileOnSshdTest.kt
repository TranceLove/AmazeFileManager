/*
 * Copyright (C) 2014-2020 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
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

package com.amaze.filemanager.filesystem.ssh

import androidx.test.core.app.ApplicationProvider
import com.amaze.filemanager.fileoperations.filesystem.OpenMode
import com.amaze.filemanager.filesystem.HybridFile
import com.amaze.filemanager.filesystem.Operations
import com.amaze.filemanager.filesystem.OperationsTest.AbstractErrorCallback
import org.junit.Test
import java.util.concurrent.CountDownLatch

class CreateFileOnSshdTest : AbstractSshServerTestBase() {

    private lateinit var parent: HybridFile

    override fun setUp() {
        super.setUp()
        parent = HybridFile(OpenMode.SFTP, "ssh://$USERNAME:$PASSWORD@$host:$port/home/testuser")
    }

    @Test
    fun testCreateFileNormal() {
        val latch = CountDownLatch(1)
        Operations.mkfile(
            parent,
            HybridFile(
                OpenMode.SFTP,
                "ssh://$USERNAME:$PASSWORD@$host:$port/home/testuser/newfile.txt"
            ),
            ApplicationProvider.getApplicationContext(),
            false,
            object : AbstractErrorCallback() {
                override fun done(hFile: HybridFile?, b: Boolean) {
                    latch.countDown()
                }
            }
        )
        latch.await()
    }
}
