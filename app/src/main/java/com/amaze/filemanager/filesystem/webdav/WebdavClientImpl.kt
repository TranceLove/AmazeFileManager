package com.amaze.filemanager.filesystem.webdav

import com.amaze.filemanager.filesystem.ftp.NetCopyClient
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine

class WebdavClientImpl(private val url: String) : NetCopyClient<Sardine> {

    private val davClient = OkHttpSardine()

    override fun getClientImpl(): Sardine = davClient

    // WebDAV is stateless... no cookie should be involved at all.
    override fun isConnectionValid() = true


    override fun expire() = Unit
}