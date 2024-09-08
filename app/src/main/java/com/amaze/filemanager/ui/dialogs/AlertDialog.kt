/*
 * Copyright (C) 2014-2022 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
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

import android.content.DialogInterface
import androidx.annotation.Nullable
import androidx.annotation.StringRes
import com.amaze.filemanager.ui.activities.superclasses.ThemedActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Alert Dialog.
 */
object AlertDialog {
    /**
     * Display an alert dialog. Optionally accepts a [DialogInterface.OnClickListener] to
     * provide additional behaviour when dialog button is pressed.
     *
     * Button default text is OK, but can be customized too.
     */
    @JvmStatic
    fun show(
        activity: ThemedActivity,
        @StringRes content: Int,
        @StringRes title: Int,
        @StringRes positiveButtonText: Int = android.R.string.ok,
        @Nullable onPositive: DialogInterface.OnClickListener? = null
    ) {
        MaterialAlertDialogBuilder(activity, activity.appTheme.materialDesignDialogTheme)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton(positiveButtonText, onPositive)
            .show()
    }
}
