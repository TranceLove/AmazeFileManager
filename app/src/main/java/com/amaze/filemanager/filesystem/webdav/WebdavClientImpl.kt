package com.amaze.filemanager.filesystem.webdav

import com.amaze.filemanager.filesystem.ftp.NetCopyClient
import com.thegrizzlylabs.sardineandroid.Sardine
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class WebdavClientImpl(private val webdavClient: Sardine) : NetCopyClient<Sardine> {
    companion object {
        @JvmStatic
        private val logger: Logger = LoggerFactory.getLogger(WebdavClientImpl::class.java)
    }

    override fun getClientImpl(): Sardine = webdavClient

    override fun isConnectionValid(): Boolean = false

    override fun expire() {
        TODO("Not yet implemented")
    }
}