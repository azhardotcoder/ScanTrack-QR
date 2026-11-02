package com.scantrack.qr

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.scantrack.qr.data.local.database.AppDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseRoomMigrationTest {

    private val TEST_DB = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesDuplicateMetadataAndValidatesSchema() {
        // Step 1: Create Database at Version 1 using actual 1.json Room schema
        helper.createDatabase(TEST_DB, 1).apply {
            // Insert duplicate records under Schema 1
            execSQL("""
                INSERT INTO qr_history (id, rawValue, type, label, createdAt, lastUsedAt, usageCount, isPinned)
                VALUES (1, 'https://test.com', 'URL', 'Custom Bookmark', 1000, 2000, 3, 0)
            """.trimIndent())
            execSQL("""
                INSERT INTO qr_history (id, rawValue, type, label, createdAt, lastUsedAt, usageCount, isPinned)
                VALUES (2, 'https://test.com', 'URL', NULL, 3000, 5000, 4, 1)
            """.trimIndent())
            close()
        }

        // Step 2: Execute actual MIGRATION_1_2 and validate against actual 2.json Room schema
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        // Step 3: Validate that data was reconciled preserving all metadata
        val cursor = db.query("SELECT * FROM qr_history WHERE rawValue = 'https://test.com'")
        assertTrue("Surviving record must be found", cursor.moveToFirst())
        assertEquals("Surviving ID must be MAX(id) = 2", 2L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
        assertEquals("Custom label from older duplicate must be preserved", "Custom Bookmark", cursor.getString(cursor.getColumnIndexOrThrow("label")))
        assertEquals("Earliest createdAt must be preserved", 1000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
        assertEquals("Latest lastUsedAt must be preserved", 5000L, cursor.getLong(cursor.getColumnIndexOrThrow("lastUsedAt")))
        assertEquals("Usage count must be the sum (3 + 4 = 7)", 7, cursor.getInt(cursor.getColumnIndexOrThrow("usageCount")))
        assertEquals("Pinned status must be preserved", 1, cursor.getInt(cursor.getColumnIndexOrThrow("isPinned")))
        assertFalse("Only 1 deduplicated record must remain for rawValue", cursor.moveToNext())
        cursor.close()
        db.close()
    }
}

