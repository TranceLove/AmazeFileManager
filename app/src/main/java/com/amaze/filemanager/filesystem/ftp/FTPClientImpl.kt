package com.amaze.filemanager.filesystem.ftp

import org.apache.commons.net.ftp.FTPClient

class FTPClientImpl(private val ftpClient: FTPClient): NetCopyClient {

    private var isAuthenticated: Boolean = false

    override fun getClientImpl(): FTPClient = ftpClient

    override fun isConnectionValid(): Boolean = ftpClient.isConnected && isAuthenticated

    override fun expire() {
        if(isAuthenticated)
            ftpClient.logout()

        ftpClient.disconnect()
    }

}