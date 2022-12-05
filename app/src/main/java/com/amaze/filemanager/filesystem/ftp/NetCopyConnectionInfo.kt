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

import com.amaze.filemanager.application.AppConfig
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.AT
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.COLON
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.FTPS_URI_PREFIX
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.FTP_DEFAULT_PORT
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.FTP_URI_PREFIX
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.SLASH
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.SSH_DEFAULT_PORT
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool.SSH_URI_PREFIX
import com.amaze.filemanager.utils.PasswordUtil

/**
 * Container object for SSH URI, encapsulating logic for splitting information from given URI.
 * `Uri.parse()` only parse URI that is compliant to RFC2396, but we have to deal with
 * URI that is not compliant, since usernames and/or strong passwords usually have special
 * characters included, like `ssh://user@example.com:P@##w0rd@127.0.0.1:22`.
 *
 * A design decision to keep database schema slim, by the way... -TranceLove
 */
internal class NetCopyConnectionInfo(url: String) {
    val prefix: String
    val host: String
    val port: Int
    val username: String
    val password: String?
    var defaultPath: String? = null
    var queryString: String? = null

    // FIXME: Crude assumption
    init {
        require(
            url.startsWith(SSH_URI_PREFIX) or
                url.startsWith(FTP_URI_PREFIX) or
                url.startsWith(FTPS_URI_PREFIX)
        ) {
            "Argument is not a SSH URI: $url"
        }
        host = if (url.contains(AT)) {
            url.substring(
                url.lastIndexOf(AT) + 1,
                url.lastIndexOf(
                    COLON
                )
            )
        } else {
            url.substring(url.lastIndexOf("//") + 2, url.lastIndexOf(COLON))
        }
        val portAndPath = url.substring(url.lastIndexOf(COLON) + 1)
        var port: Int
        if (portAndPath.contains(SLASH)) {
            port = portAndPath.substring(0, portAndPath.indexOf(SLASH)).toInt()
            defaultPath = portAndPath.substring(portAndPath.indexOf(SLASH))
        } else {
            port = portAndPath.toInt()
            defaultPath = null
        }
        // If the uri is fetched from the app's database storage, we assume it will never be empty
        prefix = when {
            url.startsWith(SSH_URI_PREFIX) -> SSH_URI_PREFIX
            url.startsWith(FTPS_URI_PREFIX) -> FTPS_URI_PREFIX
            else -> FTP_URI_PREFIX
        }
        if (prefix != SSH_URI_PREFIX && !url.contains(
                AT
            )
        ) {
            username = ""
            password = ""
        } else {
            val authString = url.substring(
                prefix.length,
                url.lastIndexOf(
                    AT
                )
            )
            val userInfo = authString.split(":").toTypedArray()
            username = userInfo[0]
            password = if (userInfo.size > 1) {
                runCatching {
                    PasswordUtil.decryptPassword(AppConfig.getInstance(), userInfo[1])
                }.getOrElse {
                    /* Hack. It should only happen after creating new SSH connection settings
                     * and plain text password is sent in.
                     *
                     * Possible to encrypt password there as alternate solution.
                     */
                    userInfo[1]
                }
            } else {
                null
            }
        }
        if (port < 0) port = if (url.startsWith(SSH_URI_PREFIX)) {
            SSH_DEFAULT_PORT
        } else {
            FTP_DEFAULT_PORT
        }
        this.port = port
        this.queryString = if (url.contains('?')) {
            url.substringAfter('?')
        } else {
            null
        }
    }

    override fun toString(): String {
        return if (username != "") {
            "$prefix$username@$host:$port${defaultPath ?: ""}"
        } else {
            "$prefix$host:$port${defaultPath ?: ""}"
        }
    }
}
