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

package com.amaze.filemanager.ui.activities.superclasses;

import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_ROOTMODE;

import com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants;
import com.amaze.filemanager.utils.PreferenceUtils;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

/**
 * @author Emmanuel on 24/8/2017, at 23:13.
 */
public class PreferenceActivity extends BasicActivity {

  private SharedPreferences sharedPrefs;

  @Override
  public void onCreate(final Bundle savedInstanceState) {
    // Fragments are created before the super call returns, so we must
    // initialize sharedPrefs before the super call otherwise it cannot be used by fragments
    sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

    super.onCreate(savedInstanceState);
  }

  @NonNull
  public SharedPreferences getPrefs() {
    return sharedPrefs;
  }

  public boolean isRootExplorer() {
    return getAppConfig().getBoolean(PREFERENCE_ROOTMODE);
  }

  public int getCurrentTab() {
    return getPrefs()
        .getInt(PreferencesConstants.PREFERENCE_CURRENT_TAB, PreferenceUtils.DEFAULT_CURRENT_TAB);
  }
}
