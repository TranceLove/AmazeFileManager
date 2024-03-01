package com.amaze.filemanager.filesystem

import android.os.Build.VERSION_CODES
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amaze.filemanager.fileoperations.filesystem.OpenMode
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool
import com.amaze.filemanager.shadows.ShadowMultiDex
import com.amaze.filemanager.shadows.jcifs.smb.ShadowSmbFile
import com.amaze.filemanager.test.ShadowPasswordUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStorageManager
import java.net.Inet4Address

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [VERSION_CODES.R],
    shadows = [
        ShadowMultiDex::class,
        ShadowStorageManager::class,
        ShadowPasswordUtil::class,
        ShadowSmbFile::class,
    ],
)
class HybridFileSshIntegrationTest {

    @Before
    fun setUp() {

    }

    @Test
    fun testConnect() {
        assertEquals(2, 1+1)

        val f = HybridFile(OpenMode.SFTP, "ssh://airwave:tr604d3zp@192.168.191.191:6845/home/airwave/.bashrc")
        val b = f.getInputStream(InstrumentationRegistry.getInstrumentation().context)?.readBytes()
        assertNotNull(b)

        NetCopyClientConnectionPool.shutdown()
    }
}
