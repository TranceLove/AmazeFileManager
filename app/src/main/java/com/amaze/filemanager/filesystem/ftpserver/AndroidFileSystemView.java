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

import java.io.File;
import java.io.IOException;

import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

public class AndroidFileSystemView implements FileSystemView {

  private static final String FILESYSTEM_ROOT = "/";

  private static final String CURRENT_DIR = "./";

  private final Context context;

  private final String fileSystemViewRoot;

  private String currentDir;

  public AndroidFileSystemView(@NonNull Context context, @NonNull String fileSystemViewRoot) {
    this.context = context;
    this.fileSystemViewRoot = fileSystemViewRoot;
    this.currentDir = FILESYSTEM_ROOT;
  }

  /**
   * Does the file system support random file access?
   *
   * @return false
   * @throws FtpException
   */
  @Override
  public boolean isRandomAccessible() {
    return false;
  }

  @Override
  public FtpFile getFile(String file) {
    switch (file) {
      case FILESYSTEM_ROOT:
        return createFtpFileFromRoot();
      case CURRENT_DIR:
        return createFtpFileFrom(currentDir);
      default:
        return createFtpFileFrom(file.startsWith(FILESYSTEM_ROOT) ? file : ("/" + file));
    }
  }

  @Override
  public FtpFile getWorkingDirectory() {
    return getFile(currentDir);
  }

  @Override
  public boolean changeWorkingDirectory(String dir) {
    try {
      if (dir.equals(FILESYSTEM_ROOT)) {
        currentDir = FILESYSTEM_ROOT;
        return true;
      } else {
        File newDir = new File(new File(fileSystemViewRoot, currentDir), dir).getCanonicalFile();
        if (!newDir.exists()) {
          return false;
        } else {
          if (!newDir.getAbsolutePath().startsWith(fileSystemViewRoot)) {
            currentDir = FILESYSTEM_ROOT;
          } else {
            String newDirPath = newDir.getAbsolutePath().substring(fileSystemViewRoot.length());
            currentDir = "".equals(newDirPath) ? FILESYSTEM_ROOT : newDirPath;
          }
          return true;
        }
      }
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public FtpFile getHomeDirectory() {
    return createFtpFileFrom(FILESYSTEM_ROOT);
  }

  @Override
  public void dispose() {}

  private FtpFile createFtpFileFromRoot() {
    return new AndroidSafFtpFile(
        context, FILESYSTEM_ROOT, DocumentFile.fromFile(new File(fileSystemViewRoot)));
  }

  private FtpFile createFtpFileFrom(String virtualPath) {
    return new AndroidSafFtpFile(
        context, virtualPath, DocumentFile.fromFile(new File(fileSystemViewRoot + virtualPath)));
  }
}
