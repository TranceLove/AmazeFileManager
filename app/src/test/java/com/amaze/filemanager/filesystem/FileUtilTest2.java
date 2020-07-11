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

package com.amaze.filemanager.filesystem;

import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.O_MR1;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import com.amaze.filemanager.BuildConfig;
import com.amaze.filemanager.shadows.ShadowFileProvider;
import com.amaze.filemanager.shadows.ShadowFileUtil;
import com.amaze.filemanager.shadows.ShadowMultiDex;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

@RunWith(RobolectricTestRunner.class)
@Config(
    constants = BuildConfig.class,
    shadows = {ShadowMultiDex.class, ShadowFileProvider.class, ShadowFileUtil.class},
    maxSdk = 27)
/**
 * This unit test mainly tests {@link FileUtil#isWritableNormalOrSaf(File, Context)}, hence a
 * special {@link ShadowFileUtil} is accompanying, for reproducing the expected behaviour of the
 * method's collaborators.
 */
public class FileUtilTest2 {

  @Test
  @Config(minSdk = KITKAT, maxSdk = O_MR1)
  public void testIsWritableNormalOrSaf() {
    File tmpFolder = Environment.getExternalStoragePublicDirectory("external");
    File tmpFile = new File(tmpFolder, "test.txt");

    assertTrue(FileUtil.isOnExtSdCard(tmpFile, RuntimeEnvironment.application));
    assertTrue(
        FileUtil.isWritableNormalOrSaf(
            Environment.getExternalStorageDirectory(), RuntimeEnvironment.application));
    assertFalse(FileUtil.isWritableNormalOrSaf(tmpFolder, RuntimeEnvironment.application));
    assertFalse(FileUtil.isWritableNormalOrSaf(tmpFile, RuntimeEnvironment.application));

    Uri uri =
        FileProvider.getUriForFile(
            RuntimeEnvironment.application,
            RuntimeEnvironment.application.getPackageName(),
            tmpFolder);
    shadowOf(RuntimeEnvironment.application.getContentResolver())
        .takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

    assertTrue(FileUtil.isWritableNormalOrSaf(tmpFolder, RuntimeEnvironment.application));
  }
}
