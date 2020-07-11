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

import static android.os.Build.VERSION_CODES.KITKAT;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import java.io.File;
import java.io.IOException;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import com.amaze.filemanager.filesystem.FileUtil;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.UriPermission;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

@Implements(FileUtil.class)
public class ShadowFileUtil extends FileUtil {

  @Implementation
  public static boolean isWritable(final File file) {
    return file != null
        && file.getParentFile() != null
        && file.getAbsolutePath()
            .startsWith(Environment.getExternalStorageDirectory().getAbsolutePath());
  }

  @TargetApi(KITKAT)
  @Implementation
  public static boolean isOnExtSdCard(final File file, Context c) {
    return file != null
        && file.getAbsolutePath()
            .startsWith(
                Environment.getExternalStoragePublicDirectory("external").getAbsolutePath());
  }

  @Implementation
  public static DocumentFile getDocumentFile(
      final File file, final boolean isDirectory, Context context) {
    if (isOnExtSdCard(file, context)) {
      if (!isDirectory) {
        try {
          file.createNewFile();
          file.deleteOnExit();
        } catch (IOException ifTouchFileFailed) {
          return null;
        }
      }
    }
    DocumentFile backend =
        (Build.VERSION.SDK_INT < KITKAT)
            ? DocumentFile.fromFile(file)
            : DocumentFile.fromSingleUri(
                context,
                FileProvider.getUriForFile(
                    context, RuntimeEnvironment.application.getPackageName(), file));

    boolean actuallyCanWrite = false;
    for (UriPermission p :
        shadowOf(RuntimeEnvironment.application.getContentResolver())
            .getPersistedUriPermissions()) {
      if (backend.getUri().toString().startsWith(p.getUri().toString())) actuallyCanWrite = true;
    }

    DocumentFile retval = spy(backend);
    when(retval.canWrite()).thenReturn(actuallyCanWrite);

    return retval;
  }
}
