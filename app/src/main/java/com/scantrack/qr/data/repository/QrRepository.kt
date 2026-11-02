package com.scantrack.qr.data.repository

import com.scantrack.qr.data.local.dao.QrDao
import com.scantrack.qr.data.local.entity.QrEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class QrRepository(private val qrDao: QrDao) {

    val pinnedQrs: Flow<List<QrEntity>> = qrDao.getPinnedQrs().flowOn(Dispatchers.IO)
    val quickAccessQrs: Flow<List<QrEntity>> = qrDao.getQuickAccessQrs().flowOn(Dispatchers.IO)
    val recentQrs: Flow<List<QrEntity>> = qrDao.getRecentQrs().flowOn(Dispatchers.IO)
    val allQrs: Flow<List<QrEntity>> = qrDao.getAllQrs().flowOn(Dispatchers.IO)

    fun searchQrs(query: String, typeFilter: String?): Flow<List<QrEntity>> {
        val type = if (typeFilter == "All" || typeFilter == null) null else typeFilter
        return qrDao.searchAndFilterQrs(query, type).flowOn(Dispatchers.IO)
    }

    /**
     * Atomically saves a new scan or increments the usage count of an existing scan.
     * Prevents duplicate history records and race conditions.
     */
    suspend fun saveOrUpdateQr(qr: QrEntity) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updatedRows = qrDao.incrementUsageByRawValue(qr.rawValue, now)
        if (updatedRows == 0) {
            val insertedId = qrDao.insertQr(qr.copy(createdAt = now, lastUsedAt = now, usageCount = 1))
            // If another thread inserted concurrently and IGNORE was applied (insertedId == -1)
            if (insertedId == -1L) {
                qrDao.incrementUsageByRawValue(qr.rawValue, now)
            }
        }
    }

    /**
     * Atomically toggles pinned status directly in SQLite by ID.
     * Guaranteed independent of any stale UI snapshots.
     */
    suspend fun togglePin(id: Long) = withContext(Dispatchers.IO) {
        qrDao.togglePinById(id)
    }

    suspend fun updatePin(id: Long, isPinned: Boolean) = withContext(Dispatchers.IO) {
        qrDao.updatePin(id, isPinned)
    }

    /**
     * Targeted update of label by ID.
     */
    suspend fun renameQr(id: Long, newLabel: String) = withContext(Dispatchers.IO) {
        qrDao.updateLabel(id, newLabel.trim())
    }

    suspend fun deleteQrById(id: Long) = withContext(Dispatchers.IO) {
        qrDao.deleteById(id)
    }

    suspend fun markQrUsed(id: Long) = withContext(Dispatchers.IO) {
        qrDao.incrementUsage(id, System.currentTimeMillis())
    }

    /**
     * Atomically clears all records from the database.
     */
    suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        qrDao.deleteAll()
    }
}
