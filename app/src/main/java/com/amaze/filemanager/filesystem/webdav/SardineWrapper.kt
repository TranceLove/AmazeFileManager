package com.amaze.filemanager.filesystem.webdav

import com.amaze.filemanager.filesystem.ftp.NetCopyConnectionInfo
import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine

internal class SardineWrapper(
    private val baseUri: String,
    private val webdavClient: Sardine) {

    init {
        NetCopyConnectionInfo(baseUri).run {
            webdavClient.setCredentials(this.username, this.password)
        }
    }

    fun list(): List<DavResource> =
        webdavClient.list(baseUri)

    fun create(folder: String) =
        webdavClient.createDirectory("$baseUri/$folder")

    fun delete(path: String) {
        webdavClient.delete(path)
    }
}