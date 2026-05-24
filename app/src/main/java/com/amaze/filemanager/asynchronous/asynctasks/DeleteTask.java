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

package com.amaze.filemanager.asynchronous.asynctasks;

import static com.amaze.filemanager.ui.activities.MainActivity.TAG_INTENT_FILTER_FAILED_OPS;
import static com.amaze.filemanager.ui.activities.MainActivity.TAG_INTENT_FILTER_GENERAL;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amaze.filemanager.R;
import com.amaze.filemanager.application.AppConfig;
import com.amaze.filemanager.asynchronous.management.IOOperation;
import com.amaze.filemanager.asynchronous.management.IOOperationQueue;
import com.amaze.filemanager.database.CryptHandler;
import com.amaze.filemanager.fileoperations.exceptions.ShellNotRunningException;
import com.amaze.filemanager.fileoperations.filesystem.OpenMode;
import com.amaze.filemanager.filesystem.HybridFile;
import com.amaze.filemanager.filesystem.HybridFileParcelable;
import com.amaze.filemanager.filesystem.SafRootHolder;
import com.amaze.filemanager.filesystem.cloud.CloudUtil;
import com.amaze.filemanager.filesystem.files.CryptUtil;
import com.amaze.filemanager.filesystem.files.FileUtils;
import com.amaze.filemanager.filesystem.files.MediaConnectionUtils;
import com.amaze.filemanager.ui.activities.MainActivity;
import com.amaze.filemanager.ui.fragments.CompressedExplorerFragment;
import com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants;
import com.amaze.filemanager.ui.notifications.NotificationConstants;
import com.amaze.filemanager.utils.DataUtils;
import com.amaze.filemanager.utils.OTGUtil;
import com.cloudrail.si.interfaces.CloudStorage;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import jcifs.smb.SmbException;

public class DeleteTask {

  private static final Logger LOG = LoggerFactory.getLogger(DeleteTask.class);

  private final Context applicationContext;
  private CompressedExplorerFragment compressedExplorerFragment;

  private boolean doDeletePermanently;

  public DeleteTask(@NonNull Context applicationContext, @NonNull boolean doDeletePermanently) {
    this.applicationContext = applicationContext.getApplicationContext();
    this.doDeletePermanently = doDeletePermanently;
  }

  public DeleteTask(
      @NonNull Context applicationContext, CompressedExplorerFragment compressedExplorerFragment) {
    this.applicationContext = applicationContext.getApplicationContext();
    this.doDeletePermanently = false;
    this.compressedExplorerFragment = compressedExplorerFragment;
  }

  @SafeVarargs
  public final void execute(final ArrayList<HybridFileParcelable>... p1) {
    if (p1 == null || p1.length == 0 || p1[0] == null) {
      return;
    }

    IOOperationQueue.enqueue(
        applicationContext,
        new IOOperation.Delete(p1[0], doDeletePermanently, compressedExplorerFragment));
  }

  public static void runDeleteOperation(
      @NonNull final Context applicationContext,
      @NonNull final ArrayList<HybridFileParcelable> files,
      final boolean doDeletePermanently,
      final CompressedExplorerFragment compressedExplorerFragment) {
    final boolean rootMode =
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getBoolean(PreferencesConstants.PREFERENCE_ROOTMODE, false);

    boolean wasDeleted = true;
    if (files.size() == 0) return;

    for (HybridFileParcelable file : files) {
      try {
        wasDeleted = doDeleteFile(applicationContext, file, doDeletePermanently, rootMode);
        if (!wasDeleted) break;
      } catch (Exception e) {
        wasDeleted = false;
        break;
      }

      // delete file from media database
      if (!file.isSmb() && !file.isSftp()) {
        MediaConnectionUtils.scanFiles(
            applicationContext, files.toArray(new HybridFile[files.size()]));

        if (FileUtils.NOMEDIA_FILE.equals(file.getName()))
          MediaConnectionUtils.scanFile(applicationContext, file.getParent(applicationContext));
      }

      // delete file entry from encrypted database
      if (file.getName(applicationContext).endsWith(CryptUtil.CRYPT_EXTENSION)) {
        CryptHandler handler = CryptHandler.INSTANCE;
        handler.clear(file.getPath());
      }
    }

    if (files.size() > 0) {
      String path = files.get(0).getParent(applicationContext);
      Intent intent = new Intent(MainActivity.KEY_INTENT_LOAD_LIST);
      intent.putExtra(MainActivity.KEY_INTENT_LOAD_LIST_FILE, path);
      intent.setPackage(applicationContext.getPackageName());
      applicationContext.sendBroadcast(intent);
    }

    if (!wasDeleted) {
      applicationContext.sendBroadcast(
          new Intent(TAG_INTENT_FILTER_GENERAL)
              .putParcelableArrayListExtra(TAG_INTENT_FILTER_FAILED_OPS, files));
    } else if (compressedExplorerFragment == null) {
      AppConfig.getInstance()
          .runInApplicationThread(
              () -> Toast.makeText(applicationContext, R.string.done, Toast.LENGTH_SHORT).show());
    }

    if (compressedExplorerFragment != null) {
      AppConfig.getInstance()
          .runInApplicationThread(() -> compressedExplorerFragment.files.clear());
    }

    // cancel any processing notification because of cut/paste operation
    NotificationManager notificationManager =
        (NotificationManager) applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.cancel(NotificationConstants.COPY_ID);
  }

  private static boolean doDeleteFile(
      @NonNull Context applicationContext,
      @NonNull HybridFileParcelable file,
      boolean doDeletePermanently,
      boolean rootMode)
      throws Exception {
    switch (file.getMode()) {
      case OTG:
        DocumentFile documentFile =
            OTGUtil.getDocumentFile(file.getPath(), applicationContext, false);
        return documentFile.delete();
      case DOCUMENT_FILE:
        documentFile =
            OTGUtil.getDocumentFile(
                file.getPath(),
                SafRootHolder.getUriRoot(),
                applicationContext,
                OpenMode.DOCUMENT_FILE,
                false);
        return documentFile.delete();
      case DROPBOX:
      case BOX:
      case GDRIVE:
      case ONEDRIVE:
        CloudStorage cloudStorage = DataUtils.getInstance().getAccount(file.getMode());
        try {
          cloudStorage.delete(CloudUtil.stripPath(file.getMode(), file.getPath()));
          return true;
        } catch (Exception e) {
          LOG.warn("failed to delete cloud files", e);
          return false;
        }
      default:
        try {
          /* SMB and SFTP (or any remote files that may support in the future) should not be
           * supported by recycle bin. - TranceLove
           */
          if (!doDeletePermanently
              && !OpenMode.SMB.equals(file.getMode())
              && !OpenMode.SFTP.equals(file.getMode())) {
            return file.moveToBin(applicationContext);
          }
          return file.delete(applicationContext, rootMode);
        } catch (ShellNotRunningException | SmbException e) {
          LOG.warn("failed to delete files", e);
          throw e;
        }
    }
  }
}
