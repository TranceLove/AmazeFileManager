package com.amaze.filemanager.asynchronous.management

import android.content.Intent
import com.amaze.filemanager.filesystem.HybridFile
import com.amaze.filemanager.filesystem.HybridFileParcelable
import com.amaze.filemanager.filesystem.Operations
import com.amaze.filemanager.ui.fragments.CompressedExplorerFragment

/** Mutating I/O operations serialized by the centralized queue. */
sealed class IOOperation {
    data class Copy(val intent: Intent) : IOOperation()

    data class Move(val intent: Intent) : IOOperation()

    data class Extract(val intent: Intent) : IOOperation()

    data class Compress(val intent: Intent) : IOOperation()

    data class Encrypt(val intent: Intent) : IOOperation()

    data class Decrypt(val intent: Intent) : IOOperation()

    data class Mkdir(
        val parentFile: HybridFile,
        val file: HybridFile,
        val rootMode: Boolean,
        val errorCallBack: Operations.ErrorCallBack,
    ) : IOOperation()

    data class MkFile(
        val parentFile: HybridFile,
        val file: HybridFile,
        val rootMode: Boolean,
        val errorCallBack: Operations.ErrorCallBack,
    ) : IOOperation()

    data class Rename(
        val oldFile: HybridFile,
        val newFile: HybridFile,
        val rootMode: Boolean,
        val errorCallBack: Operations.ErrorCallBack,
    ) : IOOperation()

    data class Delete(
        val files: ArrayList<HybridFileParcelable>,
        val doDeletePermanently: Boolean,
        val compressedExplorerFragment: CompressedExplorerFragment?,
    ) : IOOperation()
}
