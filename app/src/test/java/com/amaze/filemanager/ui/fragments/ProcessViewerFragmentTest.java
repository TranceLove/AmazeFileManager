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

package com.amaze.filemanager.ui.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.amaze.filemanager.asynchronous.management.IOOperation;
import com.amaze.filemanager.asynchronous.management.IOOperationQueue;

import android.content.Intent;

public class ProcessViewerFragmentTest {

  @Test
  public void filterQueueEntriesForDisplay_hidesStaleCompletedEntries() {
    long now = 1_000_000L;
    long retentionMs = 30_000L;

    List<IOOperationQueue.QueueEntry> entries = new ArrayList<>();
    entries.add(
        new IOOperationQueue.QueueEntry(
            1L,
            new IOOperation.Copy(new Intent()),
            IOOperationQueue.Status.COMPLETED,
            null,
            now - retentionMs - 1));
    entries.add(
        new IOOperationQueue.QueueEntry(
            2L,
            new IOOperation.Copy(new Intent()),
            IOOperationQueue.Status.COMPLETED,
            null,
            now - retentionMs));
    entries.add(
        new IOOperationQueue.QueueEntry(
            3L,
            new IOOperation.Copy(new Intent()),
            IOOperationQueue.Status.RUNNING,
            null,
            now - retentionMs - 100));

    List<IOOperationQueue.QueueEntry> filtered =
        ProcessViewerFragment.filterQueueEntriesForDisplay(entries, now, 10, retentionMs);

    assertEquals(2, filtered.size());
    assertFalse(filtered.stream().anyMatch(entry -> entry.getId() == 1L));
    assertTrue(filtered.stream().anyMatch(entry -> entry.getId() == 2L));
    assertTrue(filtered.stream().anyMatch(entry -> entry.getId() == 3L));
  }

  @Test
  public void filterQueueEntriesForDisplay_returnsLastNEntries() {
    List<IOOperationQueue.QueueEntry> entries = new ArrayList<>();
    for (int i = 1; i <= 15; i++) {
      entries.add(
          new IOOperationQueue.QueueEntry(
              i, new IOOperation.Copy(new Intent()), IOOperationQueue.Status.RUNNING, null, i));
    }

    List<IOOperationQueue.QueueEntry> filtered =
        ProcessViewerFragment.filterQueueEntriesForDisplay(entries, 100L, 10, 30_000L);

    assertEquals(10, filtered.size());
    assertEquals(6L, filtered.get(0).getId());
    assertEquals(15L, filtered.get(9).getId());
  }
}
