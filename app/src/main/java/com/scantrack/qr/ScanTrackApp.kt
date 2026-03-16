package com.scantrack.qr

import android.app.Application
import com.scantrack.qr.data.local.SettingsManager
import com.scantrack.qr.data.local.database.AppDatabase
import com.scantrack.qr.data.repository.QrRepository

class ScanTrackApp : Application() {
    private val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { QrRepository(database.qrDao()) }
    val settingsManager by lazy { SettingsManager(this) }
}
