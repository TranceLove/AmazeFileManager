/*
 * Copyright (C) 2014-2020 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
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

package com.amaze.filemanager.shadows;

import java.io.File;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

@Implements(FileProvider.class)
public class ShadowFileProvider extends FileProvider {

  private static final PathStrategy strategy = new PathStrategy();

  @Implementation
  public static Uri getUriForFile(
      @NonNull Context context, @NonNull String authority, @NonNull File file) {
    return strategy.getUriForFile(file);
  }

  private static final class PathStrategy {

    private static final String prefix =
        "content://" + RuntimeEnvironment.application.getPackageName() + "/storage_root";

    Uri getUriForFile(File file) {
      return Uri.parse(prefix + file.getAbsolutePath());
    }

    File getFileForUri(Uri uri) {
      return new File(uri.toString().substring(prefix.length() - 1));
    }
  }
}
