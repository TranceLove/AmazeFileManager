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

package com.amaze.filemanager.application;

import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_BOOKMARKS_ADDED;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_CHANGEPATHS;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_COLORED_NAVIGATION;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_COLORIZE_ICONS;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_DISABLE_PLAYER_INTENT_FILTERS;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_ENABLE_MARQUEE_FILENAME;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_NEED_TO_SET_HOME;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_ROOTMODE;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_ROOT_LEGACY_LISTING;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_SHOW_DIVIDERS;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_SHOW_FILE_SIZE;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_SHOW_GOBACK_BUTTON;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_SHOW_HEADERS;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_SHOW_HIDDENFILES;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_SHOW_LAST_MODIFIED;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_SHOW_PERMISSIONS;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_SHOW_SIDEBAR_FOLDERS;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_SHOW_SIDEBAR_QUICKACCESSES;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_SHOW_THUMB;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_TEXTEDITOR_NEWSTACK;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_USE_CIRCULAR_IMAGES;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_VIEW;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.Callable;

import org.acra.ACRA;
import org.acra.annotation.AcraCore;
import org.acra.config.ACRAConfigurationException;
import org.acra.config.CoreConfiguration;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.data.StringFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amaze.filemanager.BuildConfig;
import com.amaze.filemanager.R;
import com.amaze.filemanager.crashreport.AcraReportSenderFactory;
import com.amaze.filemanager.crashreport.ErrorActivity;
import com.amaze.filemanager.database.ExplorerDatabase;
import com.amaze.filemanager.database.UtilitiesDatabase;
import com.amaze.filemanager.database.UtilsHandler;
import com.amaze.filemanager.fileoperations.exceptions.ShellNotRunningException;
import com.amaze.filemanager.fileoperations.filesystem.OpenMode;
import com.amaze.filemanager.filesystem.HybridFile;
import com.amaze.filemanager.filesystem.ssh.CustomSshJConfig;
import com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants;
import com.amaze.filemanager.ui.provider.UtilitiesProvider;
import com.amaze.filemanager.utils.ScreenUtils;
import com.amaze.trashbin.TrashBin;
import com.amaze.trashbin.TrashBinConfig;
import com.topjohnwu.superuser.Shell;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.StrictMode;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import jcifs.Config;
import jcifs.smb.SmbException;

@AcraCore(
    buildConfigClass = BuildConfig.class,
    reportSenderFactoryClasses = AcraReportSenderFactory.class)
public class AppConfig extends GlideApplication {

  private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

  private SharedPreferences sharedPreferences;

  private UtilitiesProvider utilsProvider;
  private UtilsHandler utilsHandler;

  private WeakReference<Context> mainActivityContext;
  private static ScreenUtils screenUtils;

  private static AppConfig instance;

  private UtilitiesDatabase utilitiesDatabase;

  private ExplorerDatabase explorerDatabase;

  private TrashBinConfig trashBinConfig;
  private TrashBin trashBin;
  private static final String TRASH_BIN_BASE_PATH =
      Environment.getExternalStorageDirectory().getPath() + File.separator + ".AmazeData";

  public UtilitiesProvider getUtilsProvider() {
    return utilsProvider;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    // selector in srcCompat isn't supported without this
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    instance = this;

    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    CustomSshJConfig.init();
    initializeInteractiveShell();

    explorerDatabase = ExplorerDatabase.initialize(this);
    utilitiesDatabase = UtilitiesDatabase.initialize(this);

    utilsProvider = new UtilitiesProvider(this);
    utilsHandler = new UtilsHandler(this, utilitiesDatabase);

    runInBackground(Config::registerSmbURLHandler);

    // disabling file exposure method check for api n+
    StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
    StrictMode.setVmPolicy(builder.build());
  }

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    initACRA();
  }

  @Override
  public void onTerminate() {
    super.onTerminate();
    closeInteractiveShell();
  }

  /**
   * Post a runnable to handler. Use this in case we don't have any restriction to execute after
   * this runnable is executed, and {@link #runInBackground(Runnable)} in case we need to execute
   * something after execution in background
   */
  public void runInBackground(Runnable runnable) {
    Completable.fromRunnable(runnable).subscribeOn(Schedulers.io()).subscribe();
  }

  /**
   * Shows a toast message
   *
   * @param context Any context belonging to this application
   * @param message The message to show
   */
  public static void toast(Context context, @StringRes int message) {
    // this is a static method so it is easier to call,
    // as the context checking and casting is done for you

    if (context == null) return;

    if (!(context instanceof Application)) {
      context = context.getApplicationContext();
    }

    if (context instanceof Application) {
      final Context c = context;
      final @StringRes int m = message;

      getInstance().runInApplicationThread(() -> Toast.makeText(c, m, Toast.LENGTH_LONG).show());
    }
  }

  /**
   * Shows a toast message
   *
   * @param context Any context belonging to this application
   * @param message The message to show
   */
  public static void toast(Context context, String message) {
    // this is a static method so it is easier to call,
    // as the context checking and casting is done for you

    if (context == null) return;

    if (!(context instanceof Application)) {
      context = context.getApplicationContext();
    }

    if (context instanceof Application) {
      final Context c = context;
      final String m = message;

      getInstance().runInApplicationThread(() -> Toast.makeText(c, m, Toast.LENGTH_LONG).show());
    }
  }

  /**
   * Run a {@link Runnable} in the main application thread
   *
   * @param r {@link Runnable} to run
   */
  public void runInApplicationThread(@NonNull Runnable r) {
    Completable.fromRunnable(r).subscribeOn(AndroidSchedulers.mainThread()).subscribe();
  }

  /**
   * Convenience method to run a {@link Callable} in the main application thread. Use when the
   * callable's return value is not processed.
   *
   * @param c {@link Callable} to run
   */
  public void runInApplicationThread(@NonNull Callable<Void> c) {
    Completable.fromCallable(c).subscribeOn(AndroidSchedulers.mainThread()).subscribe();
  }

  public static synchronized AppConfig getInstance() {
    return instance;
  }

  public UtilsHandler getUtilsHandler() {
    return utilsHandler;
  }

  public SharedPreferences getSharedPreferences() {
    return sharedPreferences;
  }

  public void setMainActivityContext(@NonNull Activity activity) {
    mainActivityContext = new WeakReference<>(activity);
    screenUtils = new ScreenUtils(activity);
  }

  public ScreenUtils getScreenUtils() {
    return screenUtils;
  }

  @Nullable
  public Context getMainActivityContext() {
    return mainActivityContext.get();
  }

  public ExplorerDatabase getExplorerDatabase() {
    return explorerDatabase;
  }

  public UtilitiesDatabase getUtilitiesDatabase() {
    return utilitiesDatabase;
  }

  public boolean isRootExplorer() {
    return getBoolean(PREFERENCE_ROOTMODE);
  }

  /**
   * Called in {@link #attachBaseContext(Context)} after calling the {@code super} method. Should be
   * overridden if MultiDex is enabled, since it has to be initialized before ACRA.
   */
  protected void initACRA() {
    if (ACRA.isACRASenderServiceProcess()) {
      return;
    }

    try {
      final CoreConfiguration acraConfig =
          new CoreConfigurationBuilder(this)
              .setBuildConfigClass(BuildConfig.class)
              .setReportFormat(StringFormat.JSON)
              .setSendReportsInDevMode(true)
              .setEnabled(true)
              .build();
      ACRA.init(this, acraConfig);
    } catch (final ACRAConfigurationException ace) {
      if (log != null) {
        log.warn("failed to initialize ACRA", ace);
      }
      ErrorActivity.reportError(
          this,
          ace,
          null,
          ErrorActivity.ErrorInfo.make(
              ErrorActivity.ERROR_UNKNOWN,
              "Could not initialize ACRA crash report",
              R.string.app_ui_crash));
    }
  }

  public TrashBin getTrashBinInstance() {
    if (trashBin == null) {
      trashBin =
          new TrashBin(
              getApplicationContext(),
              true,
              getTrashBinConfig(),
              s -> {
                runInBackground(
                    () -> {
                      HybridFile file = new HybridFile(OpenMode.TRASH_BIN, s);
                      try {
                        file.delete(getMainActivityContext(), false);
                      } catch (ShellNotRunningException | SmbException e) {
                        log.warn("failed to delete file in trash bin cleanup", e);
                      }
                    });
                return true;
              },
              null);
    }
    return trashBin;
  }

  public boolean getBoolean(String key) {
    boolean defaultValue;

    switch (key) {
      case PREFERENCE_SHOW_PERMISSIONS:
      case PREFERENCE_SHOW_GOBACK_BUTTON:
      case PREFERENCE_SHOW_HIDDENFILES:
      case PREFERENCE_BOOKMARKS_ADDED:
      case PREFERENCE_ROOTMODE:
      case PREFERENCE_COLORED_NAVIGATION:
      case PREFERENCE_TEXTEDITOR_NEWSTACK:
      case PREFERENCE_CHANGEPATHS:
      case PREFERENCE_ROOT_LEGACY_LISTING:
      case PREFERENCE_DISABLE_PLAYER_INTENT_FILTERS:
        defaultValue = false;
        break;
      case PREFERENCE_SHOW_FILE_SIZE:
      case PREFERENCE_SHOW_DIVIDERS:
      case PREFERENCE_SHOW_HEADERS:
      case PREFERENCE_USE_CIRCULAR_IMAGES:
      case PREFERENCE_COLORIZE_ICONS:
      case PREFERENCE_SHOW_THUMB:
      case PREFERENCE_SHOW_SIDEBAR_QUICKACCESSES:
      case PREFERENCE_NEED_TO_SET_HOME:
      case PREFERENCE_SHOW_SIDEBAR_FOLDERS:
      case PREFERENCE_VIEW:
      case PREFERENCE_SHOW_LAST_MODIFIED:
      case PREFERENCE_ENABLE_MARQUEE_FILENAME:
        defaultValue = true;
        break;
      default:
        throw new IllegalArgumentException("Please map \'" + key + "\'");
    }

    return getSharedPreferences().getBoolean(key, defaultValue);
  }

  private TrashBinConfig getTrashBinConfig() {
    if (trashBinConfig == null) {
      SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

      int days =
          sharedPrefs.getInt(
              PreferencesConstants.KEY_TRASH_BIN_RETENTION_DAYS,
              TrashBinConfig.RETENTION_DAYS_INFINITE);
      long bytes =
          sharedPrefs.getLong(
              PreferencesConstants.KEY_TRASH_BIN_RETENTION_BYTES,
              TrashBinConfig.RETENTION_BYTES_INFINITE);
      int numOfFiles =
          sharedPrefs.getInt(
              PreferencesConstants.KEY_TRASH_BIN_RETENTION_NUM_OF_FILES,
              TrashBinConfig.RETENTION_NUM_OF_FILES);
      int intervalHours =
          sharedPrefs.getInt(
              PreferencesConstants.KEY_TRASH_BIN_CLEANUP_INTERVAL_HOURS,
              TrashBinConfig.INTERVAL_CLEANUP_HOURS);
      trashBinConfig =
          new TrashBinConfig(
              TRASH_BIN_BASE_PATH, days, bytes, numOfFiles, intervalHours, false, true);
    }
    return trashBinConfig;
  }

  /** Initializes an interactive shell, which will stay throughout the app lifecycle. */
  private void initializeInteractiveShell() {
    if (isRootExplorer()) {
      // Enable mount-master flag when invoking su command, to force su run in the global mount
      // namespace. See https://github.com/topjohnwu/libsu/issues/75
      Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER));
      Shell.getShell();
    }
  }

  /** Closes the interactive shell and threads associated */
  private void closeInteractiveShell() {
    if (isRootExplorer()) {
      // close interactive shell
      try {
        Shell.getShell().close();
      } catch (IOException e) {
        log.error("Error closing Shell", e);
      }
    }
  }
}
