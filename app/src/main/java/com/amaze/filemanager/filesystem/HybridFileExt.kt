package com.amaze.filemanager.filesystem

import android.net.Uri
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.SLASH

private const val MULTI_SLASH = "/(?<!:)\\/+/"

/**
 * Strip the redundant slashes in path segments.
 */
internal fun HybridFile.sanitizePathAsNecessary() {
    Regex(MULTI_SLASH).let { regex ->
        if ((isSftp || isSmb || isFtp || isDocumentFile || isCloudDriveFile) &&
            path.substringAfter("://").contains(regex)
        ) {
            this.path = StringBuilder(path).replace(regex, SLASH.toString())
            val uri = Uri.parse(path)
            path = buildString {
                append(uri.scheme)
                append("://")
                append(uri.authority)
                append(SLASH)
                append(uri.pathSegments.joinToString(SLASH.toString()))
                uri.query?.run {
                    append("?")
                    append(this)
                }
            }
        } else {
            return
        }
    }
}
