/*
 * Copyright (C) 2014-2023 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
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

package com.amaze.filemanager.ui.fragments.preferencefragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.LOLLIPOP
import android.os.Bundle
import android.text.InputType
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.folderselector.FolderChooserDialog
import com.afollestad.materialdialogs.folderselector.FolderChooserDialog.FolderCallback
import com.amaze.filemanager.R
import com.amaze.filemanager.asynchronous.services.ftp.FtpService
import com.amaze.filemanager.asynchronous.services.ftp.FtpService.Companion.KEY_PREFERENCE_PASSWORD
import com.amaze.filemanager.asynchronous.services.ftp.FtpService.Companion.KEY_PREFERENCE_PATH
import com.amaze.filemanager.asynchronous.services.ftp.FtpService.Companion.KEY_PREFERENCE_TIMEOUT
import com.amaze.filemanager.asynchronous.services.ftp.FtpService.Companion.PORT_PREFERENCE_KEY
import com.amaze.filemanager.filesystem.files.FileUtils
import com.amaze.filemanager.ui.fragments.FtpServerFragment
import com.amaze.filemanager.ui.runIfDocumentsUIExists

/**
 * FTP server preferences.
 */
class FtpServerPrefsFragment : BasePrefsFragment(), FolderCallback {

    override val title: Int = R.string.ftp

    private val defaultPathFromPreferences: String
        get() {
            return preferenceManager.sharedPreferences
                ?.getString(KEY_PREFERENCE_PATH, FtpService.defaultPath(requireContext()))!!
        }

    private val activityResultHandlerOnFtpServerPathUpdate = createOpenDocumentTreeIntentCallback {
            directoryUri ->
        changeFTPServerPath(directoryUri.toString())
    }

    private fun changeFTPServerPath(path: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
        if (FileUtils.isRunningAboveStorage(path)) {
            preferences.putBoolean(FtpService.KEY_PREFERENCE_ROOT_FILESYSTEM, true)
        }
        preferences.putString(KEY_PREFERENCE_PATH, path)
        preferences.apply()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.frp_server_prefs, rootKey)

        findPreference<EditTextPreference>(PORT_PREFERENCE_KEY)?.apply {
            setOnBindEditTextListener { txtField ->
                txtField.inputType = InputType.TYPE_CLASS_NUMBER
            }
        }

        findPreference<EditTextPreference>(KEY_PREFERENCE_TIMEOUT)?.apply {
            setOnBindEditTextListener { txtField ->
                txtField.inputType = InputType.TYPE_CLASS_NUMBER
            }
        }

        findPreference<EditTextPreference>(KEY_PREFERENCE_PASSWORD)?.apply {
            setOnBindEditTextListener { txtField ->
                txtField.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
        }

        findPreference<Preference>(KEY_PREFERENCE_PATH)?.apply {
            setOnPreferenceClickListener {
                if (shouldUseSafFileSystem()) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.runIfDocumentsUIExists(requireActivity()) {
                        activityResultHandlerOnFtpServerPathUpdate.launch(intent)
                    }
                } else {
                    val dialogBuilder = FolderChooserDialog.Builder(requireActivity())
                    dialogBuilder
                        .chooseButton(R.string.choose_folder)
                        .initialPath(defaultPathFromPreferences)
                        .goUpLabel(getString(R.string.folder_go_up_one_level))
                        .cancelButton(R.string.cancel)
                        .tag(FtpServerFragment.TAG)
                        .build()
                        .show(activity)
                }
                true
            }
        }
    }

    @Suppress("LabeledExpression")
    private fun createOpenDocumentTreeIntentCallback(callback: (directoryUri: Uri) -> Unit):
        ActivityResultLauncher<Intent> {
        return registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode == Activity.RESULT_OK && SDK_INT >= LOLLIPOP) {
                val directoryUri = it.data?.data ?: return@registerForActivityResult
                requireContext().contentResolver.takePersistableUriPermission(
                    directoryUri,
                    FtpServerFragment.GRANT_URI_RW_PERMISSION
                )
                callback.invoke(directoryUri)
            }
        }
    }

    private fun shouldUseSafFileSystem(): Boolean {
        return (
            false == preferenceManager.sharedPreferences?.getBoolean(
                FtpService.KEY_PREFERENCE_LEGACY_FILESYSTEM,
                true
            )
            ) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    companion object {
        private const val OPEN_DOCUMENT_TREE_REQUEST_CODE = 23456
    }
}
