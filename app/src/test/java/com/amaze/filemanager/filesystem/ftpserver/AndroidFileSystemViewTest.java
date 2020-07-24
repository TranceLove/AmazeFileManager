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

package com.amaze.filemanager.filesystem.ftpserver;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import android.os.Environment;

@Config(minSdk = 14)
@RunWith(RobolectricTestRunner.class)
public class AndroidFileSystemViewTest {
  private static final String SHARED_PATH_ROOT =
      Environment.getExternalStorageDirectory().getAbsolutePath();

  private FileSystemView fileSystemView;

  @Before
  public void setUp() {
    fileSystemView = new AndroidFileSystemView(RuntimeEnvironment.application, SHARED_PATH_ROOT);
  }

  @After
  public void tearDown() {
    fileSystemView.dispose();
  }

  @Test
  public void testChangeDirectoryPastRoot() throws FtpException {
    assertEquals("/", fileSystemView.getWorkingDirectory().getAbsolutePath());
  }

  @Test
  public void testGetFtpFileSimple() throws FtpException {
    FtpFile result = fileSystemView.getFile("/1.txt");
    assertNotNull(result);
    assertEquals("/1.txt", result.getAbsolutePath());
    assertNotNull(result.getPhysicalFile());
    assertTrue(result.isWritable());
  }

  @Test
  public void testMultipleGetFtpFile() throws FtpException, IOException {
    assertNotNull(fileSystemView.getHomeDirectory());
    assertEquals("/", fileSystemView.getHomeDirectory().getAbsolutePath());
    assertNotNull(fileSystemView.getWorkingDirectory());
    assertEquals("/", fileSystemView.getWorkingDirectory().getAbsolutePath());

    Path newFolder = Paths.get(SHARED_PATH_ROOT, "DCIM", "Android");
    Files.createDirectories(newFolder);

    assertTrue(fileSystemView.changeWorkingDirectory("/DCIM/Android"));
    assertNotNull(fileSystemView.getWorkingDirectory());
    assertEquals("/DCIM/Android", fileSystemView.getWorkingDirectory().getAbsolutePath());

    assertNotNull(fileSystemView.getFile("/DCIM/Android/DSC10001.jpg"));
    assertEquals(
        "/DCIM/Android/DSC10001.jpg",
        fileSystemView.getFile("/DCIM/Android/DSC10001.jpg").getAbsolutePath());

    assertTrue(fileSystemView.changeWorkingDirectory("/"));
    assertNotNull(fileSystemView.getWorkingDirectory());
    assertEquals("/", fileSystemView.getWorkingDirectory().getAbsolutePath());

    Files.walk(newFolder).map(Path::toFile).forEach(File::delete);
  }
}
