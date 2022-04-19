package com.amaze.filemanager.filesystem

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.amaze.filemanager.test.AndroidDirectoryAccessPermissionHelper
import com.amaze.filemanager.test.AndroidDirectoryAccessPermissionHelper.obtainAndroidDataDirectoryAccessPermission
import com.amaze.filemanager.test.StoragePermissionHelper
import com.amaze.filemanager.test.StoragePermissionHelper.obtainManageAppAllFileAccessPermissionAutomatically
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
class AndroidDataDirectoryAccessTest {
    @Test
    fun test1() {
        obtainManageAppAllFileAccessPermissionAutomatically()
        obtainAndroidDataDirectoryAccessPermission("data")
        obtainAndroidDataDirectoryAccessPermission("obb")
    }
}
