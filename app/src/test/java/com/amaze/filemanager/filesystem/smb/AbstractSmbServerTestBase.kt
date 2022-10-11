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

package com.amaze.filemanager.filesystem.smb

import android.os.Build.VERSION_CODES.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.SSH_DEFAULT_PORT
import com.amaze.filemanager.shadows.ShadowMultiDex
import jcifs.SmbConstants
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile

/**
 * Base class for SMB server related tests.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    shadows = [ShadowMultiDex::class],
    sdk = [JELLY_BEAN, KITKAT, P]
)
abstract class AbstractSmbServerTestBase {

    protected lateinit var host: String

    protected var port: Int = SSH_DEFAULT_PORT

    companion object {

        protected const val USERNAME = "testuser"
        protected const val PASSWORD = "testpassword"

        @JvmStatic
        private lateinit var server: GenericContainer<Nothing>

        @BeforeClass
        @JvmStatic
        fun bootstrap() {
            server = GenericContainer<Nothing>(
                ImageFromDockerfile().withDockerfileFromBuilder { builder ->
                    builder.from("dperson/samba")
                }
            ).withCreateContainerCmdModifier { cmd ->
                cmd.withCmd("-u $USERNAME;$PASSWORD")
            }
            server.apply {
                // Refer to https://hub.docker.com/r/dperson/samba README
                withExposedPorts(139, 445)
                waitingFor(Wait.forListeningPort())
                start()
            }
        }

        @AfterClass
        @JvmStatic
        fun shutdown() {
            if (server.isRunning) {
                server.stop()
            }
        }
    }

    @Before
    open fun setUp() {
        host = server.host
        port = server.getMappedPort(SmbConstants.DEFAULT_PORT)
    }
}
