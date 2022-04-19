package com.amaze.filemanager.test

import android.content.Context
import android.content.Intent
import android.content.UriPermission
import android.os.Build
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.amaze.filemanager.BuildConfig
import org.junit.Assert.*
import java.io.File

object AndroidDirectoryAccessPermissionHelper {

    private const val LAUNCH_TIMEOUT = 5000L

    const val DOCUMENTSUI_PACKAGE = "com.google.android.documentsui"

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.R)
    fun obtainAndroidDataDirectoryAccessPermission(subdir: String) {
        if (findUriPermissionAtAndroidDirectory(subdir) == null) {
            getInstrumentation().let { instrumentation ->
                UiDevice.getInstance(instrumentation).let { device ->
                    device.pressHome()

                    val launcherPackage: String = device.launcherPackageName
                    assertNotNull(launcherPackage)
                    device.wait(
                        Until.hasObject(By.pkg(launcherPackage).depth(0)),
                        LAUNCH_TIMEOUT
                    )

                    // Launch the app
                    val context = ApplicationProvider.getApplicationContext<Context>()
                    val intent = context.packageManager.getLaunchIntentForPackage(
                        BuildConfig.APPLICATION_ID)?.apply {
                        // Clear out any previous instances
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    context.startActivity(intent)

                    // Wait for the app to appear
                    device.wait(
                        Until.hasObject(By.pkg(BuildConfig.APPLICATION_ID).depth(0)),
                        LAUNCH_TIMEOUT
                    )

                    val path = device.findObject(UiSelector().amazeResourceId("fullpath"))

                    // Ensure go back to MMC storage
                    device.findObject(UiSelector().amazeResourceId("home")).click()
                    assertEquals(Environment.getExternalStorageDirectory().absolutePath, path.text)
                    performAccess(device, subdir)
                }
            }
        }
    }

    private fun performAccess(device: UiDevice, subdir: String) {

        val path = device.findObject(UiSelector().amazeResourceId("fullpath"))

        // Android
        device.findObject(
            UiSelector().className(TextView::class.java)
                .text("Android")
                .amazeResourceId("firstline")
        ).click()

        assertEquals(File(Environment.getExternalStorageDirectory(), "Android").absolutePath,
            path.text)

        // Android/data
        device.findObject(
            UiSelector().className(TextView::class.java)
                .text(subdir)
                .amazeResourceId("firstline")
        ).click()

        // Grant SAF access dialog
        device.findObject(
            UiSelector().className(TextView::class.java).text("OK")
                .amazeResourceId("md_buttonDefaultPositive")
        ).click()

        device.wait(
            Until.hasObject(By.pkg(DOCUMENTSUI_PACKAGE).depth(0)),
            LAUNCH_TIMEOUT
        )

        device.findObject(UiSelector().documentsUiResourceId("toolbar"))
        .getChild(UiSelector().className(TextView::class.java)).let {
            assertTrue(it.exists())
            assertEquals(subdir, it.text)
        }

        device.findObject(
            UiSelector().packageName(DOCUMENTSUI_PACKAGE)
                .text("use this folder".uppercase())
                .className(Button::class.java)
                .resourceId("android:id/button1")
        ).click()

        device.findObject(
            UiSelector().packageName(DOCUMENTSUI_PACKAGE)
                .documentsUiResourceId("alertTitle")
                .className(TextView::class.java)
        ).text.let {
            assertTrue(it.contains("Amaze"))
            assertTrue(it.contains(subdir))
        }

        device.findObject(
            UiSelector().packageName(DOCUMENTSUI_PACKAGE)
                .resourceId("android:id/button1")
                .className(Button::class.java)
                .text("allow".uppercase())
        ).click()

        assertNotNull(findUriPermissionAtAndroidDirectory(subdir))
    }

    private fun findUriPermissionAtAndroidDirectory(subdir: String): UriPermission? {
        val uriPermissions = getInstrumentation().context.contentResolver.persistedUriPermissions
        return if (uriPermissions.isNotEmpty()) {
            uriPermissions.find {
                it.isReadPermission &&
                it.isWritePermission &&
                it.uri.scheme == "content" &&
                it.uri.authority == "com.android.externalstorage.documents" &&
                it.uri.path == "/tree/primary:Android/$subdir"
            }
        } else {
            null
        }
    }
}

fun UiSelector.documentsUiResourceId(id: String): UiSelector =
    resourceId("${AndroidDirectoryAccessPermissionHelper.DOCUMENTSUI_PACKAGE}:id/$id")

fun UiSelector.amazeResourceId(id: String): UiSelector =
    resourceId("${BuildConfig.APPLICATION_ID}:id/$id")