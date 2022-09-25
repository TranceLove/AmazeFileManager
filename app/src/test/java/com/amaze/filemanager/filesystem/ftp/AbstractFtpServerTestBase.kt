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

import android.os.Build.VERSION_CODES.JELLY_BEAN
import android.os.Build.VERSION_CODES.KITKAT
import android.os.Build.VERSION_CODES.P
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.FTP_DEFAULT_PORT
import com.amaze.filemanager.filesystem.ssh.test.TestUtils
import com.amaze.filemanager.shadows.ShadowMultiDex
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.apache.commons.net.ftp.FTPClient
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.net.ServerSocket
import java.util.*

@RunWith(AndroidJUnit4::class)
@Config(
    shadows = [ShadowMultiDex::class],
    sdk = [JELLY_BEAN, KITKAT, P]
)
@Suppress("StringLiteralDuplication")
abstract class AbstractFtpServerTestBase {

    private val IGNORED_FILES = Arrays.asList(".dockerenv", "entrypoint.sh")

    protected var ftpHost: String? = null
        get

    protected var ftpPort: Int = FTP_DEFAULT_PORT

    protected open val ftpPrefix: String
        get() = NetCopyClientConnectionPool.FTP_URI_PREFIX

    protected open val ftpUrl: String
        get() = NetCopyClientUtils.encryptFtpPathAsNecessary(
            "$ftpPrefix$USERNAME:$PASSWORD@$ftpHost:$ftpPort"
        )

    companion object {
        @JvmStatic
        private lateinit var ftpServer: GenericContainer<Nothing>

        const val USERNAME = "ftpuser"
        const val PASSWORD = "passw0rD"

        @BeforeClass
        @JvmStatic
        fun bootstrap() {
            var freePort: Int
            ServerSocket(0).use { socket ->
                freePort = socket.localPort
                socket.close()
            }
            ftpServer = FixedHostPortGenericContainer<Nothing>("timoreymann/chrooted-ftp").apply {
                withExposedPorts(FTP_DEFAULT_PORT)
                withFixedExposedPort(freePort, freePort)
                withEnv("PASSIVE_MIN_PORT", freePort.toString())
                withEnv("PASSIVE_MAX_PORT", freePort.toString())
                withEnv("PUBLIC_HOST", "0.0.0.0")
                withClasspathResourceMapping(
                    "ftpusers",
                    "/opt/chrooted-ftp/users",
                    BindMode.READ_WRITE
                )
                waitingFor(Wait.forListeningPort())
                start()
            }
            RxJavaPlugins.reset()
            RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
            RxAndroidPlugins.reset()
            RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        }

        @AfterClass
        @JvmStatic
        fun shutdown() {
            if (ftpServer.isRunning) {
                ftpServer.stop()
            }
        }
    }

    @Before
    open fun setUp() {
        ftpHost = ftpServer.host
        ftpPort = ftpServer.getMappedPort(FTP_DEFAULT_PORT)
//        prepareSshConnection()
    }

    @After
    open fun tearDown() = Unit

    protected open fun saveConnectionSettings() =
        TestUtils.saveFtpConnectionSettings(USERNAME, PASSWORD)

    protected open fun createConnection(): NetCopyClient<FTPClient>? {
        return NetCopyClientConnectionPool.getConnection(ftpUrl)
    }
}
