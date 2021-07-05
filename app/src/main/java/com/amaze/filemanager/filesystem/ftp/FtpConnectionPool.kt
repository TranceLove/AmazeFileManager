package com.amaze.filemanager.filesystem.ftp

import android.os.AsyncTask
import android.util.Log
import com.amaze.filemanager.application.AppConfig
import com.amaze.filemanager.asynchronous.asynctasks.ssh.PemToKeyPairTask
import com.amaze.filemanager.asynchronous.asynctasks.ssh.SshAuthenticationTask
import com.amaze.filemanager.filesystem.ssh.SshClientUtils
import net.schmizz.sshj.Config
import net.schmizz.sshj.SSHClient
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPConnectionClosedException
import org.apache.commons.net.ftp.FTPSClient
import java.security.KeyPair
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReference

object FtpConnectionPool {
    const val FTP_DEFAULT_PORT = 21
    const val SSH_DEFAULT_PORT = 22
    const val FTP_URI_PREFIX = "ftp://"
    const val FTPS_URI_PREFIX = "ftps://"
    const val SSH_URI_PREFIX = "ssh://"
    const val CONNECT_TIMEOUT = 30000

    private val TAG = FtpConnectionPool::class.java.simpleName

    private var connections: MutableMap<String, NetCopyClient> = ConcurrentHashMap()

    @JvmField
    var sshClientFactory: SSHClientFactory = DefaultSSHClientFactory()

    @JvmField
    var ftpClientFactory: FTPClientFactory = DefaultFTPClientFactory()

    /**
     * Obtain a [SSHClient] connection from the underlying connection pool.
     *
     *
     * Beneath it will return the connection if it exists; otherwise it will create a new one and
     * put it into the connection pool.
     *
     * @param url SSH connection URL, in the form of `
     * ssh://<username>:<password>@<host>:<port>` or `
     * ssh://<username>@<host>:<port>`
     * @return [SSHClient] connection, already opened and authenticated
     * @throws IOException IOExceptions that occur during connection setup
     */
    fun getConnection(url: String): NetCopyClient? {
        var client = connections[url]
        if (client == null) {
            client = createNetCopyClient.invoke(url)
            if (client != null) {
                connections[url] = client
            }
        } else {
            if (!validate(client)) {
                Log.d(TAG, "Connection no longer usable. Reconnecting...")
                expire(client)
                connections.remove(url)
                client = createNetCopyClient.invoke(url)
                if (client != null) {
                    connections[url] = client
                }
            }
        }
        return client
    }

    /**
     * Obtain a [SSHClient] connection from the underlying connection pool.
     *
     *
     * Beneath it will return the connection if it exists; otherwise it will create a new one and
     * put it into the connection pool.
     *
     *
     * Different from [.getConnection] above, this accepts broken down parameters as
     * convenience method during setting up SCP/SFTP connection.
     *
     * @param host host name/IP, required
     * @param port SSH server port, required
     * @param hostFingerprint expected host fingerprint, required
     * @param username username, required
     * @param password password, required if using password to authenticate
     * @param keyPair [KeyPair], required if using key-based authentication
     * @return [SSHClient] connection
     */
    @Suppress("LongParameterList")
    fun getConnection(
        host: String,
        port: Int,
        hostFingerprint: String,
        username: String,
        password: String?,
        keyPair: KeyPair?
    ): NetCopyClient? {
        val url = SshClientUtils.deriveSftpPathFrom(host, port, "", username, password, keyPair)
        var client = connections[url]
        if (client == null) {
            client =
                createSshClientInternal(host, port, hostFingerprint, username, password, keyPair)?.run {
                    SSHClientImpl(this)
                }
            if (client != null) connections[url] = client
        } else {
            if (!validate(client)) {
                Log.d(TAG, "Connection no longer usable. Reconnecting...")
                expire(client)
                connections.remove(url)
                client = createSshClientInternal(
                    host,
                    port,
                    hostFingerprint,
                    username,
                    password,
                    keyPair
                )?.run {
                    SSHClientImpl(this)
                }
                if (client != null) connections[url] = client
            }
        }
        return client
    }

    private val createNetCopyClient: (String) -> NetCopyClient? = { url ->
        if(url.startsWith(SSH_URI_PREFIX)) {
            createSshClient(url)
        } else {
            createFtpClient(url)
        }
    }

    /**
     * Remove a SSH connection from connection pool. Disconnects from server before removing.
     *
     * For updating SSH connection settings.
     *
     * This method will silently end without feedback if the specified SSH connection URI does not
     * exist in the connection pool.
     *
     * @param url SSH connection URI
     */
    fun removeConnection(url: String, callback: Runnable) {
        AsyncRemoveConnection(url, callback).execute()
    }

    /**
     * Kill any connection that is still in place. Used by MainActivity.
     *
     * @see MainActivity.onDestroy
     * @see MainActivity.exit
     */
    fun shutdown() {
        AppConfig.getInstance().runInBackground {
            if(connections.isNotEmpty()) {
                connections.values.forEach {
                    it.expire()
                }
                connections.clear()
            }
        }
    }

    private fun validate(client: NetCopyClient): Boolean = client.isConnectionValid()

    private fun expire(client: NetCopyClient) = client.expire()

    // Logic for creating SSH connection. Depends on password existence in given Uri password or
    // key-based authentication
    @Suppress("TooGenericExceptionThrown")
    private fun createSshClient(url: String): NetCopyClient? {
        val connInfo = ConnectionInfo(url)
        val utilsHandler = AppConfig.getInstance().utilsHandler
        val pem = utilsHandler.getSshAuthPrivateKey(url)
        val keyPair = AtomicReference<KeyPair?>(null)
        if (pem != null && !pem.isEmpty()) {
            try {
                val latch = CountDownLatch(1)
                PemToKeyPairTask(
                    pem
                ) { result: KeyPair? ->
                    keyPair.set(result)
                    latch.countDown()
                }
                    .execute()
                latch.await()
            } catch (e: InterruptedException) {
                throw RuntimeException("Error getting keypair from given PEM string", e)
            }
        }
        val hostKey = utilsHandler.getSshHostKey(url) ?: return null
        return createSshClientInternal(
            connInfo.host,
            connInfo.port,
            hostKey,
            connInfo.username,
            connInfo.password,
            keyPair.get()
        )?.run {
            SSHClientImpl(this)
        }
    }

    @Suppress("LongParameterList")
    private fun createSshClientInternal(
        host: String,
        port: Int,
        hostKey: String,
        username: String,
        password: String?,
        keyPair: KeyPair?
    ): SSHClient? {
        return try {
            val taskResult = SshAuthenticationTask(
                hostname = host,
                port = port,
                hostKey = hostKey,
                username = username,
                password = password,
                privateKey = keyPair
            ).execute().get()
            taskResult.result
        } catch (e: InterruptedException) {
            // FIXME: proper handling
            throw RuntimeException(e)
        } catch (e: ExecutionException) {
            // FIXME: proper handling
            throw RuntimeException(e)
        }
    }

    private fun createFtpClient(url: String): NetCopyClient? {
        return runCatching {
            val ftpClient = ftpClientFactory.create(url)
            val connInfo = ConnectionInfo(url)
            ftpClient.login(connInfo.username, connInfo.password)
            FTPClientImpl(ftpClient)
        }.onFailure {
            when {
                it is FTPConnectionClosedException ->
                    Log.e(TAG, "FTP login failed")
                else ->
                    Log.e(TAG, "IOException", it)
            }
        }.getOrNull()
    }

    /**
     * Container object for SSH URI, encapsulating logic for splitting information from given URI.
     * `Uri.parse()` only parse URI that is compliant to RFC2396, but we have to deal with
     * URI that is not compliant, since usernames and/or strong passwords usually have special
     * characters included, like `ssh://user@example.com:P@##w0rd@127.0.0.1:22`.
     *
     *
     * A design decision to keep database schema slim, by the way... -TranceLove
     */
    internal class ConnectionInfo(url: String) {
        val host: String
        val port: Int
        val username: String
        val password: String?
        protected var defaultPath: String? = null

        // FIXME: Crude assumption
        init {
            require(url.startsWith(SSH_URI_PREFIX) or
            url.startsWith(FTP_URI_PREFIX) or
            url.startsWith(FTPS_URI_PREFIX)) {
                "Argument is not a SSH URI: $url"
            }
            host = url.substring(url.lastIndexOf('@') + 1, url.lastIndexOf(':'))
            val portAndPath = url.substring(url.lastIndexOf(':') + 1)
            var port: Int
            if (portAndPath.contains("/")) {
                port = portAndPath.substring(0, portAndPath.indexOf('/')).toInt()
                defaultPath = portAndPath.substring(portAndPath.indexOf('/'))
            } else {
                port = portAndPath.toInt()
                defaultPath = null
            }
            // If the uri is fetched from the app's database storage, we assume it will never be empty
            val prefix = when {
                url.startsWith(SSH_URI_PREFIX) -> SSH_URI_PREFIX
                url.startsWith(FTPS_URI_PREFIX) -> FTPS_URI_PREFIX
                else -> FTP_URI_PREFIX
            }
            val authString = url.substring(prefix.length, url.lastIndexOf('@'))
            val userInfo = authString.split(":").toTypedArray()
            username = userInfo[0]
            password = if (userInfo.size > 1) userInfo[1] else null
            if (port < 0) port = if(url.startsWith(SSH_URI_PREFIX)) {
                SSH_DEFAULT_PORT
            } else {
                FTP_DEFAULT_PORT
            }
            this.port = port
        }
    }

    class AsyncRemoveConnection internal constructor(
        private var url: String,
        private val callback: Runnable?
    ) : AsyncTask<Unit, Unit, Unit>() {

        override fun doInBackground(vararg params: Unit) {
            url = SshClientUtils.extractBaseUriFrom(url)
            if (connections.containsKey(url)) {
                connections[url]?.expire()
                connections.remove(url)
            }
        }

        override fun onPostExecute(aVoid: Unit) {
            callback?.run()
        }
    }

    /**
     * Interface defining a factory class for creating [SSHClient] instances.
     *
     * In normal usage you won't need this; will be useful however when writing tests concerning
     * SSHClient, that mocked instances can be returned so tests can be run without a real SSH server.
     */
    interface SSHClientFactory {
        /**
         * Implement this to return [SSHClient] instances.
         */
        fun create(config: Config?): SSHClient
    }

    interface FTPClientFactory {
        fun create(uri: String): FTPClient
    }

    /** Default [SSHClientFactory] implementation.  */
    internal class DefaultSSHClientFactory : SSHClientFactory {
        override fun create(config: Config?): SSHClient {
            return SSHClient(config)
        }
    }

    internal class DefaultFTPClientFactory : FTPClientFactory {
        override fun create(uri: String): FTPClient {
            return if(uri.startsWith(FTPS_URI_PREFIX))
                FTPSClient()
            else
                FTPClient()
        }
    }
}