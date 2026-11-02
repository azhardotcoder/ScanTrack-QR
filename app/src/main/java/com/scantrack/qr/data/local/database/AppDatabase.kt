package com.scantrack.qr.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scantrack.qr.data.local.dao.QrDao
import com.scantrack.qr.data.local.entity.QrEntity

@Database(entities = [QrEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun qrDao(): QrDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration from Schema 1 to Schema 2:
         * 1. Safely merges existing duplicate records preserving:
         *    - label (retains custom label if set on any duplicate)
         *    - createdAt (retains earliest original timestamp via MIN)
         *    - lastUsedAt (retains newest timestamp via MAX)
         *    - usageCount (aggregates total scans via SUM)
         *    - isPinned (preserves pinned state if true on any duplicate via MAX)
         * 2. Deletes duplicate entries, keeping the survivor (MAX(id)).
         * 3. Creates unique index on `rawValue`.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reconcile duplicates safely before applying unique index
                db.execSQL("""
                    UPDATE qr_history
                    SET usageCount = (
                        SELECT SUM(q2.usageCount)
                        FROM qr_history q2
                        WHERE q2.rawValue = qr_history.rawValue
                    ),
                    isPinned = (
                        SELECT MAX(q2.isPinned)
                        FROM qr_history q2
                        WHERE q2.rawValue = qr_history.rawValue
                    ),
                    createdAt = (
                        SELECT MIN(q2.createdAt)
                        FROM qr_history q2
                        WHERE q2.rawValue = qr_history.rawValue
                    ),
                    lastUsedAt = (
                        SELECT MAX(q2.lastUsedAt)
                        FROM qr_history q2
                        WHERE q2.rawValue = qr_history.rawValue
                    ),
                    label = CASE
                        WHEN label IS NOT NULL AND TRIM(label) != '' THEN label
                        ELSE (
                            SELECT q2.label
                            FROM qr_history q2
                            WHERE q2.rawValue = qr_history.rawValue
                              AND q2.label IS NOT NULL
                              AND TRIM(q2.label) != ''
                            ORDER BY q2.id DESC
                            LIMIT 1
                        )
                    END
                    WHERE id IN (
                        SELECT MAX(id)
                        FROM qr_history
                        GROUP BY rawValue
                    )
                """.trimIndent())

                db.execSQL("""
                    DELETE FROM qr_history
                    WHERE id NOT IN (
                        SELECT MAX(id)
                        FROM qr_history
                        GROUP BY rawValue
                    )
                """.trimIndent())

                // Create the unique index on rawValue
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_qr_history_rawValue` ON `qr_history` (`rawValue`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scantrack_qr_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
