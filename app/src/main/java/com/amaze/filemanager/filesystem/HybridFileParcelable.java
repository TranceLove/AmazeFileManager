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

import com.amaze.filemanager.utils.OpenMode;
import com.amaze.filemanager.utils.Utils;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.documentfile.provider.DocumentFile;

import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.xfer.FilePermission;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/** Created by arpitkh996 on 11-01-2016. */
public class HybridFileParcelable extends HybridFile implements Parcelable {

  private long date, size;
  private boolean isDirectory;
  private String permission;
  private String name;
  private String link = "";

  public HybridFileParcelable(String path) {
    super(OpenMode.FILE, path);
    this.path = path;
  }

  public HybridFileParcelable(
      String path, String permission, long date, long size, boolean isDirectory) {
    super(OpenMode.FILE, path);
    this.date = date;
    this.size = size;
    this.isDirectory = isDirectory;
    this.path = path;
    this.permission = permission;
  }

  public HybridFileParcelable(DocumentFile documentFile) {
    super(OpenMode.DOCUMENT_FILE, documentFile.getUri().toString());
    setName(documentFile.getName());
    setDirectory(documentFile.isDirectory());
    setPermission(RootHelper.parseDocumentFilePermission(documentFile));
    setDate(documentFile.lastModified());
    setSize(documentFile.isDirectory() ? 0 : documentFile.length());
  }

// FIXME: additional constructor for device root as content:// URI + subpath(s)
//  public HybridFileParcelable(String root, DocumentFile documentFile) {
//    super(OpenMode.DOCUMENT_FILE, documentFile.getUri().toString());
//    setName(documentFile.getName());
//    setDirectory(documentFile.isDirectory());
//    setPermission(RootHelper.parseDocumentFilePermission(documentFile));
//    setDate(documentFile.lastModified());
//    setSize(documentFile.isDirectory() ? 0 : documentFile.length());
//  }

  public HybridFileParcelable(SmbFile smbFile) throws SmbException {
    super(OpenMode.SMB, smbFile.getPath());
    setName(smbFile.getName());
    setDirectory(smbFile.isDirectory());
    setDate(smbFile.lastModified());
    setSize(smbFile.isDirectory() ? 0 : smbFile.length());
  }

  public HybridFileParcelable(String path, boolean isDirectory, RemoteResourceInfo sshFile) {
    super(OpenMode.SFTP, String.format("%s/%s", path, sshFile.getName()));
    setName(sshFile.getName());
    setDirectory(isDirectory);
    setDate(sshFile.getAttributes().getMtime() * 1000);
    setSize(isDirectory ? 0 : sshFile.getAttributes().getSize());
    setPermission(
            Integer.toString(
                    FilePermission.toMask(sshFile.getAttributes().getPermissions()), 8));
  }

  @Override
  public String getName() {
    if (!Utils.isNullOrEmpty(name)) return name;
    else return super.getName();
  }

  @Override
  public String getName(Context context) {
    if (!Utils.isNullOrEmpty(name)) return name;
    else return super.getName(context);
  }

  public void setName(String name) {
    this.name = name;
  }

  public OpenMode getMode() {
    return mode;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public long getDate() {
    return date;
  }

  public void setDate(long date) {
    this.date = date;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  public boolean isHidden() {
    return name.startsWith(".");
  }

  public void setDirectory(boolean directory) {
    isDirectory = directory;
  }

  public String getPath() {
    return path;
  }

  public String getPermission() {
    return permission;
  }

  public void setPermission(String permission) {
    this.permission = permission;
  }

  protected HybridFileParcelable(Parcel in) {
    super(OpenMode.getOpenMode(in.readInt()), in.readString());
    permission = in.readString();
    name = in.readString();
    date = in.readLong();
    size = in.readLong();
    isDirectory = in.readByte() != 0;
  }

  public static final Creator<HybridFileParcelable> CREATOR =
      new Creator<HybridFileParcelable>() {
        @Override
        public HybridFileParcelable createFromParcel(Parcel in) {
          return new HybridFileParcelable(in);
        }

        @Override
        public HybridFileParcelable[] newArray(int size) {
          return new HybridFileParcelable[size];
        }
      };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(mode.ordinal());
    dest.writeString(path);
    dest.writeString(permission);
    dest.writeString(name);
    dest.writeLong(date);
    dest.writeLong(size);
    dest.writeByte((byte) (isDirectory ? 1 : 0));
  }

  @Override
  public String toString() {
    return new StringBuilder("HybridFileParcelable, path=[")
        .append(path)
        .append(']')
        .append(", name=[")
        .append(name)
        .append(']')
        .append(", size=[")
        .append(size)
        .append(']')
        .append(", date=[")
        .append(date)
        .append(']')
        .append(", permission=[")
        .append(permission)
        .append(']')
        .toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || (!(obj instanceof HybridFileParcelable))) return false;
    return path.equals(((HybridFileParcelable) obj).path);
  }

  @Override
  public int hashCode() {
    int result = path.hashCode();
    result = 37 * result + name.hashCode();
    result = 37 * result + (isDirectory ? 1 : 0);
    result = 37 * result + (int) (size ^ size >>> 32);
    result = 37 * result + (int) (date ^ date >>> 32);
    return result;
  }
}
