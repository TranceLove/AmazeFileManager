/*
 * Copyright (C) 2014-2023 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
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

package com.amaze.filemanager.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Initialize the database. Optionally, may provide a custom way to create the database
     * with supplied [Context].
     */
    @Singleton
    @Provides
    fun provideUtilitiesDatabase(@ApplicationContext context: Context): UtilitiesDatabase {
        val builder: RoomDatabase.Builder<UtilitiesDatabase> =
            UtilitiesDatabase.overrideDatabaseBuilder?.invoke(context) ?: Room.databaseBuilder(
                context,
                UtilitiesDatabase::class.java,
                UtilitiesDatabase.DATABASE_NAME,
            )
        return builder
            .allowMainThreadQueries()
            .addMigrations(
                UtilitiesDatabase.MIGRATION_1_2,
                UtilitiesDatabase.MIGRATION_2_3,
                UtilitiesDatabase.MIGRATION_3_4,
                UtilitiesDatabase.MIGRATION_4_5,
                UtilitiesDatabase.MIGRATION_5_6,
            )
            .build()
    }

    /**
     * Initialize the database. Optionally, may provide a custom way to create the database
     * with supplied [Context].
     */
    @Singleton
    @Provides
    fun provideExplorerDatabase(@ApplicationContext context: Context): ExplorerDatabase {
        val builder =
            ExplorerDatabase.overrideDatabaseBuilder?.invoke(context) ?: Room.databaseBuilder(
                context,
                ExplorerDatabase::class.java,
                ExplorerDatabase.DATABASE_NAME,
            )
        return builder
            .addMigrations(ExplorerDatabase.MIGRATION_1_2)
            .addMigrations(ExplorerDatabase.MIGRATION_2_3)
            .addMigrations(ExplorerDatabase.MIGRATION_3_4)
            .addMigrations(ExplorerDatabase.MIGRATION_4_5)
            .addMigrations(ExplorerDatabase.MIGRATION_5_6)
            .addMigrations(ExplorerDatabase.MIGRATION_6_7)
            .addMigrations(ExplorerDatabase.MIGRATION_7_8)
            .addMigrations(ExplorerDatabase.MIGRATION_8_9)
            .addMigrations(ExplorerDatabase.MIGRATION_9_10)
            .addMigrations(ExplorerDatabase.MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    fun provideUtilsHandler(
        @ApplicationContext context: Context,
        utilitiesDatabase: UtilitiesDatabase,
    ) = UtilsHandler(context, utilitiesDatabase)
}
