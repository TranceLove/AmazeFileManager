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

package com.amaze.filemanager.filesystem.ssh

import android.os.Build.VERSION_CODES.*
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amaze.filemanager.asynchronous.asynctasks.ssh.GetSshHostFingerprintTaskCallable
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.SSH_DEFAULT_PORT
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.SSH_URI_PREFIX
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.getConnection
import com.amaze.filemanager.shadows.ShadowMultiDex
import net.schmizz.sshj.common.SecurityUtils
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile

/**
 * Base class for SSH server related tests.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    shadows = [ShadowMultiDex::class],
    sdk = [JELLY_BEAN, KITKAT, P]
)
abstract class AbstractSshServerTestBase {

    protected lateinit var host: String

    protected var port: Int = SSH_DEFAULT_PORT

    companion object {

        @JvmStatic
        protected val USERNAME = "testuser"

        @JvmStatic
        protected val PASSWORD = "testpassword"

        @JvmStatic
        private lateinit var server: GenericContainer<Nothing>

        @BeforeClass
        @JvmStatic
        fun bootstrap() {
            server = GenericContainer<Nothing>(
                ImageFromDockerfile().withDockerfileFromBuilder { builder ->
                    builder.from("sickp/alpine-sshd:7.5-r2")
                        .run(
                            "passwd -d root && " +
                                "adduser -D -s /bin/ash $USERNAME && " +
                                "echo \"$USERNAME:$PASSWORD\" | chpasswd"
                        )
                        .build()
                }
            ).apply {
                withExposedPorts(SSH_DEFAULT_PORT)
                withFileSystemBind(
                    Environment.getExternalStorageDirectory().absolutePath,
                    Environment.getExternalStorageDirectory().absolutePath
                )
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
        port = server.getMappedPort(SSH_DEFAULT_PORT)
        prepareSshConnection()
    }

    protected fun prepareSshConnection() {
        val hostFingerprint: String = GetSshHostFingerprintTaskCallable(host, port, true)
            .call().let {
                SecurityUtils.getFingerprint(it)
            }
        getConnection(
            SSH_URI_PREFIX,
            host,
            port,
            hostFingerprint,
            USERNAME,
            PASSWORD,
            null
        )
    }
}
