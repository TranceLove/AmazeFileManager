/*
 * Copyright (C) 2014-2026 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
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

package com.amaze.filemanager.ui.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatSpinner
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.amaze.filemanager.R
import com.amaze.filemanager.filesystem.FileProperties
import com.amaze.filemanager.filesystem.HybridFileParcelable
import com.amaze.filemanager.filesystem.compressed.CompressedHelper
import com.amaze.filemanager.filesystem.compressed.CompressionFormat
import com.amaze.filemanager.ui.openKeyboard
import com.amaze.filemanager.ui.views.WarnableTextInputLayout
import com.amaze.filemanager.ui.views.WarnableTextInputValidator
import java.util.Locale

class CompressDialogFragment : DialogFragment() {
    interface CompressDialogListener {
        fun onCompressConfirmed(
            outputPath: String,
            format: CompressionFormat,
            files: ArrayList<HybridFileParcelable>,
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = layoutInflater.inflate(R.layout.dialog_compress, null)
        val formatSpinner: AppCompatSpinner = dialogView.findViewById(R.id.compress_format_spinner)
        val etFilename: AppCompatEditText = dialogView.findViewById(R.id.compress_filename_input)
        val tilFilename: WarnableTextInputLayout =
            dialogView.findViewById(R.id.compress_filename_warnabletextinputlayout)

        val files = getFiles()
        val currentPath = requireArguments().getString(ARG_CURRENT_PATH).orEmpty()

        val formats =
            CompressionFormat.entries
                .filter { CompressedHelper.isCreatable(it) }
                .ifEmpty { listOf(CompressionFormat.ZIP) }
        var selectedFormat = CompressionFormat.ZIP

        val adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                formats.map {
                    when (it) {
                        CompressionFormat.ZIP -> getString(R.string.compress_format_zip)
                        CompressionFormat.TAR_GZ -> getString(R.string.compress_format_tar_gz)
                        CompressionFormat.TAR_BZ2 -> getString(R.string.compress_format_tar_bz2)
                        CompressionFormat.TAR_XZ -> getString(R.string.compress_format_tar_xz)
                        CompressionFormat.SEVEN_ZIP -> getString(R.string.compress_format_7z)
                    }
                },
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        formatSpinner.adapter = adapter

        etFilename.hint = getString(R.string.enterzipname)
        etFilename.setSingleLine()
        etFilename.setText(selectedFormat.extension)

        formatSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    val oldFormat = selectedFormat
                    selectedFormat = formats[position]
                    updateFilenameExtension(etFilename, oldFormat, selectedFormat)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        dialogView.post {
            etFilename.openKeyboard(requireContext().applicationContext)
        }

        val dialog =
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.compress)
                .setView(dialogView)
                .setPositiveButton(R.string.create) { _: DialogInterface, _: Int ->
                    val name = etFilename.text?.toString().orEmpty()
                    val listener = parentFragment as? CompressDialogListener
                        ?: activity as? CompressDialogListener
                    listener?.onCompressConfirmed("$currentPath/$name", selectedFormat, files)
                }
                .setNegativeButton(R.string.cancel, null)
                .create()

        dialog.setOnShowListener {
            val positiveButton =
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            val validator =
                WarnableTextInputValidator(
                    requireContext(),
                    etFilename,
                    tilFilename,
                    positiveButton,
                ) { text ->
                    val isValidFilename = FileProperties.isValidFilename(text)
                    if (!isValidFilename) {
                        return@WarnableTextInputValidator WarnableTextInputValidator.ReturnState(
                            WarnableTextInputValidator.ReturnState.STATE_ERROR,
                            R.string.invalid_name,
                        )
                    }
                    if (text.isEmpty()) {
                        return@WarnableTextInputValidator WarnableTextInputValidator.ReturnState(
                            WarnableTextInputValidator.ReturnState.STATE_ERROR,
                            R.string.field_empty,
                        )
                    }
                    if (!text.lowercase(Locale.ROOT).endsWith(selectedFormat.extension)) {
                        return@WarnableTextInputValidator WarnableTextInputValidator.ReturnState(
                            WarnableTextInputValidator.ReturnState.STATE_WARNING,
                            R.string.compress_file_suggest_zip_extension,
                        )
                    }
                    WarnableTextInputValidator.ReturnState()
                }
            validator.afterTextChanged(etFilename.editableText)
        }

        return dialog
    }

    private fun updateFilenameExtension(
        editText: AppCompatEditText,
        oldFormat: CompressionFormat,
        newFormat: CompressionFormat,
    ) {
        val currentText = editText.text?.toString().orEmpty()
        val lowerCurrentText = currentText.lowercase(Locale.ROOT)
        val nameWithoutExtension =
            if (lowerCurrentText.endsWith(oldFormat.extension)) {
                currentText.dropLast(oldFormat.extension.length)
            } else {
                CompressionFormat.entries
                    .firstOrNull { lowerCurrentText.endsWith(it.extension) }
                    ?.let { currentText.dropLast(it.extension.length) }
                    ?: currentText
            }

        val updatedText = nameWithoutExtension + newFormat.extension
        editText.setText(updatedText)
        editText.setSelection(nameWithoutExtension.length.coerceAtMost(updatedText.length))
    }

    private fun getFiles(): ArrayList<HybridFileParcelable> {
        return if (SDK_INT >= TIRAMISU) {
            requireArguments().getParcelableArrayList(ARG_FILES, HybridFileParcelable::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelableArrayList(ARG_FILES)!!
        }
    }

    companion object {
        private const val ARG_FILES = "compress_dialog_files"
        private const val ARG_CURRENT_PATH = "compress_dialog_current_path"
        private const val TAG = "compress_dialog"

        @JvmStatic
        fun show(
            fragmentManager: FragmentManager,
            files: ArrayList<HybridFileParcelable>,
            currentPath: String,
        ) {
            CompressDialogFragment().apply {
                arguments =
                    Bundle().apply {
                        putParcelableArrayList(ARG_FILES, files)
                        putString(ARG_CURRENT_PATH, currentPath)
                    }
            }.show(fragmentManager, TAG)
        }
    }
}

