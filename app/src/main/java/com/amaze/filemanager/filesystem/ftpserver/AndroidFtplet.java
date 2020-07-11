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

import java.io.IOException;

import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.ftplet.FtpletResult;

import com.amaze.filemanager.filesystem.HybridFile;
import com.amaze.filemanager.utils.OpenMode;
import com.amaze.filemanager.utils.files.FileUtils;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * {@link Ftplet} implementation based on {@link DefaultFtplet}, overriding <code>onUploadEnd</code>
 * method to trigger media store refresh.
 *
 * {@see {@link DefaultFtplet#onUploadEnd(FtpSession, FtpRequest)}}
 * {@see {@link FileUtils#scanFile(Context, HybridFile[])}
 */
public class AndroidFtplet extends DefaultFtplet {

  private final Context context;

  private final String fileSystemRoot;

  public AndroidFtplet(@NonNull Context context, @NonNull String fileSystemRoot) {
    this.context = context;
    this.fileSystemRoot = fileSystemRoot;
  }

  /**
   * Overrides parent class with trigger MediaStore refresh by broadcast Intent.
   *
   * @return {@link DefaultFtplet#onUploadEnd(FtpSession, FtpRequest)}
   */
  @Override
  public FtpletResult onUploadEnd(FtpSession session, FtpRequest request)
      throws FtpException, IOException {
    FileUtils.scanFile(
        context,
        new HybridFile[] {
          new HybridFile(OpenMode.FILE, this.fileSystemRoot + request.getArgument())
        });
    return super.onUploadEnd(session, request);
  }
}
