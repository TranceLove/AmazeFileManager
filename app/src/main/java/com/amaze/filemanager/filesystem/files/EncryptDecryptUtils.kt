/*
 * Copyright (C) 2014-2024 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
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
package com.amaze.filemanager.filesystem.files

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.appcompat.widget.AppCompatEditText
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.amaze.filemanager.R
import com.amaze.filemanager.asynchronous.workers.AbstractProgressiveWorker
import com.amaze.filemanager.asynchronous.workers.DecryptWorker
import com.amaze.filemanager.asynchronous.workers.EncryptWorker
import com.amaze.filemanager.database.CryptHandler
import com.amaze.filemanager.database.CryptHandler.addEntry
import com.amaze.filemanager.database.models.explorer.EncryptedEntry
import com.amaze.filemanager.fileoperations.filesystem.OpenMode
import com.amaze.filemanager.filesystem.HybridFileParcelable
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.ui.dialogs.DecryptFingerprintDialog.show
import com.amaze.filemanager.ui.dialogs.GeneralDialogCreation
import com.amaze.filemanager.ui.fragments.MainFragment
import com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants
import com.amaze.filemanager.ui.provider.UtilitiesProvider
import com.amaze.filemanager.utils.PasswordUtil.decryptPassword
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Provides useful interfaces and methods for encryption/decryption
 *
 * @author Emmanuel on 25/5/2017, at 16:55.
 */
object EncryptDecryptUtils {
    const val DECRYPT_BROADCAST: String = "decrypt_broadcast"

    // Intent data-carrier key for source Parcelable (matches legacy EncryptService.TAG_SOURCE)
    const val INTENT_TAG_SOURCE = "crypt_source"

    // Intent data-carrier key for open mode (not used by workers, only for data carrying)
    const val INTENT_TAG_OPEN_MODE = "open_mode"

    private val LOG: Logger = LoggerFactory.getLogger(EncryptDecryptUtils::class.java)

    /**
     * Queries database to map path and password. Starts the encryption process after database query
     *
     * @param path the path of file to encrypt
     * @param password the password in plaintext
     * @throws GeneralSecurityException Errors on encrypting file/folder
     * @throws IOException I/O errors on encrypting file/folder
     */
    @JvmStatic
    @Throws(GeneralSecurityException::class, IOException::class)
    fun startEncryption(
        c: Context?,
        path: String,
        password: String?,
        intent: Intent,
    ) {
        val encryptTarget = intent.getStringExtra(EncryptWorker.TAG_ENCRYPT_TARGET) ?: ""
        val destPath = path.substring(0, path.lastIndexOf('/') + 1) + encryptTarget
        val useAesCrypt = intent.getBooleanExtra(EncryptWorker.TAG_AESCRYPT, false)

        if (!useAesCrypt) {
            val encryptedEntry = EncryptedEntry(destPath, password)
            addEntry(encryptedEntry)
        }

        @Suppress("DEPRECATION")
        val sourceFile: HybridFileParcelable? = intent.getParcelableExtra(INTENT_TAG_SOURCE)

        val data =
            Data.Builder()
                .putString(EncryptWorker.TAG_SOURCE_PATH, sourceFile?.path ?: path)
                .putString(EncryptWorker.TAG_SOURCE_NAME, sourceFile?.name ?: "")
                .putLong(EncryptWorker.TAG_SOURCE_SIZE, sourceFile?.getSize() ?: 0L)
                .putBoolean(EncryptWorker.TAG_SOURCE_DIRECTORY, sourceFile?.isDirectory ?: false)
                .putInt(
                    EncryptWorker.TAG_SOURCE_MODE,
                    sourceFile?.mode?.ordinal ?: OpenMode.FILE.ordinal,
                )
                .putString(EncryptWorker.TAG_ENCRYPT_TARGET, encryptTarget)
                .putBoolean(EncryptWorker.TAG_AESCRYPT, useAesCrypt)
                .putString(EncryptWorker.TAG_PASSWORD, password ?: "")
                .putInt(
                    AbstractProgressiveWorker.KEY_SERVICE_TYPE,
                    AbstractProgressiveWorker.SERVICE_ENCRYPT,
                )
                .build()

        val request =
            OneTimeWorkRequestBuilder<EncryptWorker>()
                .setInputData(data)
                .addTag(AbstractProgressiveWorker.TAG_PROGRESSIVE_WORK)
                .build()

        WorkManager.getInstance(c!!)
            .enqueueUniqueWork("encrypt_work", ExistingWorkPolicy.APPEND, request)
    }

    /**
     * Routine to decrypt file. Include branches for AESCrypt, password and fingerprint methods.
     */
    @JvmStatic
    fun decryptFile(
        c: Context,
        mainActivity: MainActivity,
        main: MainFragment,
        openMode: OpenMode,
        sourceFile: HybridFileParcelable,
        decryptPath: String?,
        utilsProvider: UtilitiesProvider,
        broadcastResult: Boolean,
    ) {
        // Use a plain Intent as a data carrier (no target service)
        val decryptIntent = Intent()
        decryptIntent.putExtra(INTENT_TAG_OPEN_MODE, openMode.ordinal)
        decryptIntent.putExtra(INTENT_TAG_SOURCE, sourceFile)
        decryptIntent.putExtra(DecryptWorker.TAG_DECRYPT_PATH, decryptPath)
        val preferences = PreferenceManager.getDefaultSharedPreferences(main.requireContext())

        if (sourceFile.path.endsWith(CryptUtil.AESCRYPT_EXTENSION)) {
            displayDecryptDialogForAescrypt(c, mainActivity, utilsProvider, decryptIntent, main)
        } else {
            val encryptedEntry: EncryptedEntry?

            try {
                encryptedEntry = findEncryptedEntry(sourceFile.path)
            } catch (e: GeneralSecurityException) {
                LOG.warn("failed to find encrypted entry while decrypting", e)
                // we couldn't find any entry in database or lost the key to decipher
                toastDecryptionFailure(main)
                return
            } catch (e: IOException) {
                LOG.warn("failed to find encrypted entry while decrypting", e)
                toastDecryptionFailure(main)
                return
            }

            val decryptButtonCallbackInterface: DecryptButtonCallbackInterface = createCallback(main)

            if (encryptedEntry == null && !sourceFile.path.endsWith(CryptUtil.AESCRYPT_EXTENSION)) {
                // couldn't find the matching path in database, we lost the password
                toastDecryptionFailure(main)
                return
            }

            when (encryptedEntry!!.password.value) {
                PreferencesConstants.ENCRYPT_PASSWORD_FINGERPRINT ->
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            show(
                                c,
                                mainActivity,
                                decryptIntent,
                                decryptButtonCallbackInterface,
                            )
                        } else {
                            throw IllegalStateException("API < M!")
                        }
                    } catch (e: GeneralSecurityException) {
                        LOG.warn("failed to form fingerprint dialog", e)
                        toastDecryptionFailure(main)
                    } catch (e: IOException) {
                        LOG.warn("failed to form fingerprint dialog", e)
                        toastDecryptionFailure(main)
                    } catch (e: IllegalStateException) {
                        LOG.warn("failed to form fingerprint dialog", e)
                        toastDecryptionFailure(main)
                    }

                PreferencesConstants.ENCRYPT_PASSWORD_MASTER ->
                    try {
                        displayDecryptDialogWithMasterPassword(
                            c,
                            mainActivity,
                            decryptIntent,
                            utilsProvider,
                            preferences,
                            decryptButtonCallbackInterface,
                        )
                    } catch (e: GeneralSecurityException) {
                        LOG.warn("failed to show decrypt dialog, e")
                        toastDecryptionFailure(main)
                    } catch (e: IOException) {
                        LOG.warn("failed to show decrypt dialog, e")
                        toastDecryptionFailure(main)
                    }

                else ->
                    GeneralDialogCreation.showDecryptDialog(
                        c,
                        mainActivity,
                        decryptIntent,
                        utilsProvider.appTheme,
                        encryptedEntry.password.value,
                        decryptButtonCallbackInterface,
                    )
            }
        }
    }

    private fun displayDecryptDialogWithMasterPassword(
        c: Context,
        mainActivity: MainActivity,
        decryptIntent: Intent,
        utilsProvider: UtilitiesProvider,
        preferences: SharedPreferences,
        decryptButtonCallbackInterface: DecryptButtonCallbackInterface,
    ) {
        GeneralDialogCreation.showDecryptDialog(
            c,
            mainActivity,
            decryptIntent,
            utilsProvider.appTheme,
            decryptPassword(
                c,
                preferences.getString(
                    PreferencesConstants.PREFERENCE_CRYPT_MASTER_PASSWORD,
                    PreferencesConstants.PREFERENCE_CRYPT_MASTER_PASSWORD_DEFAULT,
                )!!,
                Base64.DEFAULT,
            ),
            decryptButtonCallbackInterface,
        )
    }

    private fun displayDecryptDialogForAescrypt(
        c: Context?,
        mainActivity: MainActivity?,
        utilsProvider: UtilitiesProvider,
        decryptIntent: Intent,
        main: MainFragment,
    ) {
        GeneralDialogCreation.showPasswordDialog(
            c!!,
            mainActivity!!,
            utilsProvider.appTheme,
            R.string.crypt_decrypt,
            R.string.authenticate_password,
            { dialog: MaterialDialog, _: DialogAction? ->
                val editText =
                    dialog.view.findViewById<AppCompatEditText>(R.id.singleedittext_input)
                decryptIntent.putExtra(DecryptWorker.TAG_PASSWORD, editText.text.toString())
                enqueueDecryptWorker(main.requireContext(), decryptIntent)
                dialog.dismiss()
            },
            null,
        )
    }

    private fun createCallback(main: MainFragment): DecryptButtonCallbackInterface {
        return object : DecryptButtonCallbackInterface {
            override fun confirm(intent: Intent) {
                enqueueDecryptWorker(main.requireContext(), intent)
            }

            override fun failed() {
                Toast.makeText(
                    main.context,
                    main.requireMainActivity().getString(R.string.crypt_decryption_fail_password),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /**
     * Extracts data from a legacy Intent data-carrier and enqueues a [DecryptWorker].
     */
    private fun enqueueDecryptWorker(
        context: Context,
        intent: Intent,
    ) {
        @Suppress("DEPRECATION")
        val sourceFile: HybridFileParcelable? = intent.getParcelableExtra(INTENT_TAG_SOURCE)
        val decryptPath = intent.getStringExtra(DecryptWorker.TAG_DECRYPT_PATH) ?: ""
        val password = intent.getStringExtra(DecryptWorker.TAG_PASSWORD) ?: ""

        val data =
            Data.Builder()
                .putString(DecryptWorker.TAG_SOURCE_PATH, sourceFile?.path ?: "")
                .putString(DecryptWorker.TAG_SOURCE_NAME, sourceFile?.name ?: "")
                .putLong(DecryptWorker.TAG_SOURCE_SIZE, sourceFile?.getSize() ?: 0L)
                .putBoolean(DecryptWorker.TAG_SOURCE_DIRECTORY, sourceFile?.isDirectory ?: false)
                .putInt(
                    DecryptWorker.TAG_SOURCE_MODE,
                    sourceFile?.mode?.ordinal ?: OpenMode.FILE.ordinal,
                )
                .putString(DecryptWorker.TAG_DECRYPT_PATH, decryptPath)
                .putString(DecryptWorker.TAG_PASSWORD, password)
                .putInt(
                    AbstractProgressiveWorker.KEY_SERVICE_TYPE,
                    AbstractProgressiveWorker.SERVICE_DECRYPT,
                )
                .build()

        val request =
            OneTimeWorkRequestBuilder<DecryptWorker>()
                .setInputData(data)
                .addTag(AbstractProgressiveWorker.TAG_PROGRESSIVE_WORK)
                .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("decrypt_work", ExistingWorkPolicy.APPEND, request)
    }

    private fun toastDecryptionFailure(main: MainFragment) {
        Toast.makeText(
            main.context,
            main.requireMainActivity().getString(R.string.crypt_decryption_fail),
            Toast.LENGTH_LONG,
        ).show()
    }

    /**
     * Queries database to find entry for the specific path
     *
     * @param path the path to match with
     * @return the entry
     */
    @JvmStatic
    @Throws(GeneralSecurityException::class, IOException::class)
    private fun findEncryptedEntry(path: String): EncryptedEntry? {
        val handler = CryptHandler

        var matchedEntry: EncryptedEntry? = null
        // find closest path which matches with database entry
        for (encryptedEntry in handler.allEntries) {
            if (path.contains(encryptedEntry.path)) {
                if (matchedEntry == null ||
                    matchedEntry.path.length < encryptedEntry.path.length
                ) {
                    matchedEntry = encryptedEntry
                }
            }
        }
        return matchedEntry
    }

    interface EncryptButtonCallbackInterface {
        /**
         * Callback fired when user has entered a password for encryption Not called when we've a master
         * password set or enable fingerprint authentication
         *
         * @param password the password entered by user
         */
        @Throws(GeneralSecurityException::class, IOException::class)
        fun onButtonPressed(
            intent: Intent,
            password: String,
        ) {
        }
    }

    interface DecryptButtonCallbackInterface {
        /** Callback fired when we've confirmed the password matches the database  */
        fun confirm(intent: Intent) {}

        /** Callback fired when password doesn't match the value entered by user  */
        fun failed() {}
    }
}
