package com.amaze.filemanager.shadows

import android.content.SharedPreferences
import com.amaze.filemanager.filesystem.files.FileUtils
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Shadows [FileUtils].
 */
@Implements(FileUtils::class)
class ShadowFileUtils {
    companion object {
        /**
         * Convenience always true
         */
        @JvmStatic @Implementation
        fun isPathAccessible(
            dir: String?,
            pref: SharedPreferences,
        ) = true
    }
}
