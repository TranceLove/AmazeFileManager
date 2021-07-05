package com.amaze.filemanager.filesystem.ftp

interface NetCopyClient {

    fun getClientImpl(): Any

    fun isConnectionValid(): Boolean

    fun expire(): Unit
}