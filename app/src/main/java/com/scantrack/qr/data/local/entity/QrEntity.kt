package com.scantrack.qr.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "qr_history",
    indices = [
        Index(value = ["rawValue"], unique = true)
    ]
)
data class QrEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawValue: String,
    val label: String? = null,
    val type: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val usageCount: Int = 1,
    val isPinned: Boolean = false
)
