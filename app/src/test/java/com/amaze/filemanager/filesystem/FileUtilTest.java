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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import com.amaze.filemanager.BuildConfig;
import com.amaze.filemanager.shadows.ShadowMultiDex;

import android.os.Environment;

@RunWith(RobolectricTestRunner.class)
@Config(
    constants = BuildConfig.class,
    shadows = {ShadowMultiDex.class},
    maxSdk = 27)
public class FileUtilTest {

  @Test
  public void testIsWritable() {
    assertFalse(FileUtil.isWritable(null));
  }

  @Test
  public void testIsReadable() {
    assertFalse(FileUtil.isReadable(null));
    assertFalse(FileUtil.isReadable(new File("/foo/bar/not/exist/file")));
  }

  @Test
  public void testIsValidFilename() {
    assertFalse(FileUtil.isValidFilename("."));
    assertFalse(FileUtil.isValidFilename(".."));
    assertFalse(FileUtil.isValidFilename("test.file?"));
    assertTrue(FileUtil.isValidFilename(".profile"));
    assertTrue(FileUtil.isValidFilename("test.txt"));
  }

  @Test
  public void testGetOutputStream() throws FileNotFoundException {
    assertNotNull(
        FileUtil.getOutputStream(
            new File(Environment.getExternalStorageDirectory(), "test.txt"),
            RuntimeEnvironment.application));
  }
}
