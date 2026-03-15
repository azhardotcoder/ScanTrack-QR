package com.scantrack.qr.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.scantrack.qr.data.local.dao.QrDao
import com.scantrack.qr.data.local.entity.QrEntity

@Database(entities = [QrEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun qrDao(): QrDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scantrack_qr_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
