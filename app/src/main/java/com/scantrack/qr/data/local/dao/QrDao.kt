package com.scantrack.qr.data.local.dao

import androidx.room.*
import com.scantrack.qr.data.local.entity.QrEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QrDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQr(qr: QrEntity): Long

    @Update
    suspend fun updateQr(qr: QrEntity)

    @Delete
    suspend fun deleteQr(qr: QrEntity)

    @Query("SELECT * FROM qr_history WHERE id = :id LIMIT 1")
    suspend fun getQrById(id: Long): QrEntity?

    @Query("SELECT * FROM qr_history WHERE rawValue = :rawValue LIMIT 1")
    suspend fun getQrByRawValue(rawValue: String): QrEntity?

    @Query("SELECT * FROM qr_history WHERE isPinned = 1 ORDER BY lastUsedAt DESC")
    fun getPinnedQrs(): Flow<List<QrEntity>>

    @Query("SELECT * FROM qr_history ORDER BY lastUsedAt DESC")
    fun getRecentQrs(): Flow<List<QrEntity>>

    @Query("SELECT * FROM qr_history WHERE isPinned = 0 ORDER BY usageCount DESC, lastUsedAt DESC")
    fun getQuickAccessQrs(): Flow<List<QrEntity>>

    @Query("SELECT * FROM qr_history ORDER BY createdAt DESC")
    fun getAllQrs(): Flow<List<QrEntity>>

    @Query("DELETE FROM qr_history WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM qr_history")
    suspend fun deleteAll(): Int

    @Query("UPDATE qr_history SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePin(id: Long, isPinned: Boolean): Int

    @Query("UPDATE qr_history SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Long, label: String): Int

    @Query("UPDATE qr_history SET usageCount = usageCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementUsage(id: Long, timestamp: Long): Int

    @Query("UPDATE qr_history SET usageCount = usageCount + 1, lastUsedAt = :timestamp WHERE rawValue = :rawValue")
    suspend fun incrementUsageByRawValue(rawValue: String, timestamp: Long): Int

    @Query("""
        SELECT * FROM qr_history 
        WHERE (label LIKE '%' || :query || '%' OR rawValue LIKE '%' || :query || '%')
        AND (:typeFilter IS NULL OR type = :typeFilter)
        ORDER BY lastUsedAt DESC
    """)
    fun searchAndFilterQrs(query: String, typeFilter: String?): Flow<List<QrEntity>>
}
