package com.scantrack.qr.data.repository

import com.scantrack.qr.data.local.dao.QrDao
import com.scantrack.qr.data.local.entity.QrEntity
import kotlinx.coroutines.flow.Flow

class QrRepository(private val qrDao: QrDao) {

    val pinnedQrs: Flow<List<QrEntity>> = qrDao.getPinnedQrs()
    val quickAccessQrs: Flow<List<QrEntity>> = qrDao.getQuickAccessQrs()
    val recentQrs: Flow<List<QrEntity>> = qrDao.getRecentQrs()
    val allQrs: Flow<List<QrEntity>> = qrDao.getAllQrs()

    fun searchQrs(query: String, typeFilter: String?): Flow<List<QrEntity>> {
        val type = if (typeFilter == "All" || typeFilter == null) null else typeFilter
        return qrDao.searchAndFilterQrs(query, type)
    }

    suspend fun saveOrUpdateQr(qr: QrEntity) {
        val existing = qrDao.getQrByRawValue(qr.rawValue)
        if (existing != null) {
            qrDao.updateQr(existing.copy(
                lastUsedAt = System.currentTimeMillis(),
                usageCount = existing.usageCount + 1
            ))
        } else {
            qrDao.insertQr(qr)
        }
    }

    suspend fun pinQr(id: Long, isPinned: Boolean) {
        // In a more complex app, we'd use a dedicated update query
        // For now, let's keep it simple with a Flow-based observational pattern if needed
        // but since we need a suspend update, we'll fetch and update
    }

    suspend fun togglePin(qr: QrEntity) {
        qrDao.updateQr(qr.copy(isPinned = !qr.isPinned))
    }

    suspend fun renameQr(qr: QrEntity, newLabel: String) {
        qrDao.updateQr(qr.copy(label = newLabel))
    }

    suspend fun deleteQr(qr: QrEntity) {
        qrDao.deleteQr(qr)
    }

    suspend fun deleteQrById(id: Long) {
        qrDao.deleteById(id)
    }

    suspend fun markQrUsed(qr: QrEntity) {
        qrDao.updateQr(qr.copy(
            lastUsedAt = System.currentTimeMillis(),
            usageCount = qr.usageCount + 1
        ))
    }
}
