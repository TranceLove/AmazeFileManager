package com.amaze.filemanager.ui.activities

import android.annotation.TargetApi
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.JELLY_BEAN_MR1
import android.os.Build.VERSION_CODES.KITKAT
import android.os.Build.VERSION_CODES.M
import android.os.Build.VERSION_CODES.N
import android.os.Environment
import android.os.storage.StorageManager
import android.text.TextUtils
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import com.amaze.filemanager.R
import com.amaze.filemanager.adapters.data.StorageDirectoryParcelable
import com.amaze.filemanager.fileoperations.filesystem.StorageNaming
import com.amaze.filemanager.fileoperations.filesystem.usb.SingletonUsbOtg
import com.amaze.filemanager.filesystem.ExternalSdCardOperation
import com.amaze.filemanager.filesystem.files.FileUtils
import com.amaze.filemanager.ui.activities.MainActivity.DIR_SEPARATOR
import com.amaze.filemanager.ui.strings.StorageNamingHelper
import com.amaze.filemanager.utils.OTGUtil
import com.amaze.filemanager.utils.Utils
import java.io.File

private const val INTERNAL_SHARED_STORAGE = "Internal shared storage"
private const val DEFAULT_FALLBACK_STORAGE_PATH = "/storage/sdcard0"

/**
 * @return All available storage volumes (including internal storage, SD-Cards and USB devices)
 */
@TargetApi(N)
@Synchronized
internal fun MainActivity.getStorageDirectoriesNew(): ArrayList<StorageDirectoryParcelable> {
    val volumes = ArrayList<StorageDirectoryParcelable>()
    val storageManager = getSystemService(StorageManager::class.java)
    for (volume in storageManager.storageVolumes) {
        if (volume.state.lowercase() != Environment.MEDIA_MOUNTED &&
            volume.state.lowercase() != Environment.MEDIA_MOUNTED_READ_ONLY
        ) {
            continue
        }
        val path = Utils.getVolumeDirectory(volume)
        var name = volume.getDescription(this)
        if (INTERNAL_SHARED_STORAGE.lowercase() == name.lowercase()) {
            name = getString(R.string.storage_internal)
        }
        val icon =
            if (!volume.isRemovable) {
                R.drawable.ic_phone_android_white_24dp
            } else {
                // HACK: There is no reliable way to distinguish USB and SD external storage
                // However it is often enough to check for "USB" String
                if (name.uppercase().contains("USB") || path.path.uppercase().contains("USB")) {
                    R.drawable.ic_usb_white_24dp
                } else {
                    R.drawable.ic_sd_storage_white_24dp
                }
            }
        volumes.add(StorageDirectoryParcelable(path.path, name, icon))
    }
    return volumes
}

/**
* Returns all available SD-Cards in the system (include emulated)
*
* <p>Warning: Hack! Based on Android source code of version 4.3 (API 18) Because there was no
* standard way to get it before android N
*
* @return All available SD-Cards in the system (include emulated)
*/
@Synchronized
internal fun MainActivity.getStorageDirectoriesLegacy(): ArrayList<StorageDirectoryParcelable> {
    val rv = ArrayList<String>()

    // Primary physical SD-CARD (not emulated)
    val rawExternalStorage = System.getenv("EXTERNAL_STORAGE")
    // All Secondary SD-CARDs (all exclude primary) separated by ":"
    val rawSecondaryStoragesStr = System.getenv("SECONDARY_STORAGE")
    // Primary emulated SD-CARD
    val rawEmulatedStorageTarget = System.getenv("EMULATED_STORAGE_TARGET")
    if (TextUtils.isEmpty(rawEmulatedStorageTarget)) {
        // Device has physical external storage use plain paths.
        if (TextUtils.isEmpty(rawExternalStorage)) {
            // EXTERNAL_STORAGE undefined falling back to default.
            // Check for actual existence of the directory before adding to list
            if (File(DEFAULT_FALLBACK_STORAGE_PATH).exists()) {
                rv.add(DEFAULT_FALLBACK_STORAGE_PATH)
            } else {
                // We know nothing else, use Environment's fallback
                rv.add(Environment.getExternalStorageDirectory().getAbsolutePath())
            }
        } else {
            rv.add(rawExternalStorage)
        }
    } else {
        // Device has emulated storage external storage paths should have
        // userId burned into them.
        val rawUserId =
            if (SDK_INT < JELLY_BEAN_MR1) {
                ""
            } else {
                val path = Environment.getExternalStorageDirectory().absolutePath
                val folders = DIR_SEPARATOR.split(path)
                val lastFolder = folders[folders.size - 1]
                val isDigit =
                    runCatching {
                        Integer.valueOf(lastFolder)
                        true
                    }.getOrElse {
                        false
                    }
                if (isDigit) lastFolder else ""
            }
        // /storage/emulated/0[1,2,...]
        if (TextUtils.isEmpty(rawUserId)) {
            rv.add(rawEmulatedStorageTarget)
        } else {
            rv.add(rawEmulatedStorageTarget + File.separator + rawUserId)
        }
    }
    // Add all secondary storages
    if (!TextUtils.isEmpty(rawSecondaryStoragesStr)) {
        // All Secondary SD-CARDs splited into array
        val rawSecondaryStorages = rawSecondaryStoragesStr.split(File.pathSeparator)
        rv.addAll(rawSecondaryStorages)
    }
    if (SDK_INT >= M && checkStoragePermission()) rv.clear()
    if (SDK_INT >= KITKAT) {
        val strings = ExternalSdCardOperation.getExtSdCardPathsForActivity(this)
        for (s in strings) {
            val f = File(s)
            if (!rv.contains(s) && FileUtils.canListFiles(f)) rv.add(s)
        }
    }
    val usb = getUsbDrive()
    if (usb != null && !rv.contains(usb.path)) rv.add(usb.path)

    if (SDK_INT >= KITKAT) {
        if (SingletonUsbOtg.getInstance().isDeviceConnected) {
            rv.add(OTGUtil.PREFIX_OTG + "/")
        }
    }

    // Assign a label and icon to each directory
    val volumes = ArrayList<StorageDirectoryParcelable>()
    for (file in rv) {
        val f = File(file)

        @DrawableRes val icon =
            if ("/storage/emulated/legacy" == file ||
                "/storage/emulated/0" == file ||
                "/mnt/sdcard" == file
            ) {
                R.drawable.ic_phone_android_white_24dp
            } else if ("/storage/sdcard1" == file) {
                R.drawable.ic_sd_storage_white_24dp
            } else if ("/" == file) {
                R.drawable.ic_drawer_root_white
            } else {
                R.drawable.ic_sd_storage_white_24dp
            }

        @StorageNaming.DeviceDescription val deviceDescription =
            StorageNaming.getDeviceDescriptionLegacy(f)
        val name = StorageNamingHelper.getNameForDeviceDescription(this, f, deviceDescription)
        volumes.add(StorageDirectoryParcelable(file, name, icon))
    }

    return volumes
}

/** Updates everything related to USB devices MUST ALWAYS be called after onResume() */
@RequiresApi(api = KITKAT)
internal fun MainActivity.updateUsbInformation() {
    var isInformationUpdated = false
    val connectedDevices = OTGUtil.getMassStorageDevicesConnected(this)

    if (connectedDevices.isNotEmpty()) {
        if (SingletonUsbOtg.getInstance().usbOtgRoot != null &&
            OTGUtil.isUsbUriAccessible(this)
        ) {
            for (device in connectedDevices) {
                if (SingletonUsbOtg.getInstance().checkIfRootIsFromDevice(device)) {
                    isInformationUpdated = true
                    break
                }
            }

            if (!isInformationUpdated) {
                SingletonUsbOtg.getInstance().resetUsbOtgRoot()
            }
        }

        if (!isInformationUpdated) {
            SingletonUsbOtg.getInstance().setConnectedDevice(connectedDevices.get(0))
            isInformationUpdated = true
        }
    }

    if (!isInformationUpdated) {
        SingletonUsbOtg.getInstance().resetUsbOtgRoot()
        drawer.refreshDrawer()
    }

    // Registering intent filter for OTG
    val otgFilter = IntentFilter()
    otgFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
    otgFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
    registerReceiver(mOtgReceiver, otgFilter)
}
