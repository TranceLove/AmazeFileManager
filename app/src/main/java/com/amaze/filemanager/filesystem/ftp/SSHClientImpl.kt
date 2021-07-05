package com.amaze.filemanager.filesystem.ftp

import com.amaze.filemanager.filesystem.ssh.SshClientUtils
import net.schmizz.sshj.SSHClient

class SSHClientImpl(private val sshClient: SSHClient): NetCopyClient {

    override fun getClientImpl() = sshClient

    override fun isConnectionValid(): Boolean =
        sshClient.isConnected && sshClient.isAuthenticated

    override fun expire() = SshClientUtils.tryDisconnect(sshClient)
}