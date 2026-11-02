package com.scantrack.qr

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

class DatabaseMigrationTest {

    private lateinit var connection: Connection

    @Before
    fun setUp() {
        // In-memory SQLite database
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val stmt = connection.createStatement()
        
        // Setup Schema 1
        stmt.execute("""
            CREATE TABLE qr_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                rawValue TEXT NOT NULL,
                type TEXT NOT NULL,
                title TEXT,
                createdAt INTEGER NOT NULL,
                lastUsedAt INTEGER NOT NULL,
                usageCount INTEGER NOT NULL,
                isPinned INTEGER NOT NULL,
                label TEXT
            )
        """.trimIndent())
        stmt.close()
    }

    @After
    fun tearDown() {
        if (!connection.isClosed) {
            connection.close()
        }
    }

    @Test
    fun migration1to2_reconcilesDuplicates_preservesLabelEarliestCreatedLatestUsedAndSumUsage() {
        val stmt = connection.createStatement()

        // Insert duplicate records for the same rawValue
        // Record 1: Older record with custom label, unpinned
        stmt.execute("""
            INSERT INTO qr_history (id, rawValue, type, title, createdAt, lastUsedAt, usageCount, isPinned, label)
            VALUES (1, 'https://example.com/qr', 'URL', 'Example', 1000, 2000, 3, 0, 'My Custom Bookmark')
        """.trimIndent())

        // Record 2: Newer record without label, but pinned, scanned 4 times
        stmt.execute("""
            INSERT INTO qr_history (id, rawValue, type, title, createdAt, lastUsedAt, usageCount, isPinned, label)
            VALUES (2, 'https://example.com/qr', 'URL', 'Example', 3000, 5000, 4, 1, NULL)
        """.trimIndent())

        // Record 3: Independent unique record
        stmt.execute("""
            INSERT INTO qr_history (id, rawValue, type, title, createdAt, lastUsedAt, usageCount, isPinned, label)
            VALUES (3, 'WIFI:S:HomeNet;P:pass12345;;', 'WIFI', 'HomeNet', 4000, 4500, 1, 0, 'Home Wi-Fi')
        """.trimIndent())

        // Execute the exact SQL statements from MIGRATION_1_2
        stmt.execute("""
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

        stmt.execute("""
            DELETE FROM qr_history
            WHERE id NOT IN (
                SELECT MAX(id)
                FROM qr_history
                GROUP BY rawValue
            )
        """.trimIndent())

        // Create the unique index
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_qr_history_rawValue` ON `qr_history` (`rawValue`)")

        // Verification
        val rs = stmt.executeQuery("SELECT * FROM qr_history ORDER BY id ASC")
        
        val rows = mutableListOf<Map<String, Any?>>()
        while (rs.next()) {
            rows.add(mapOf(
                "id" to rs.getLong("id"),
                "rawValue" to rs.getString("rawValue"),
                "createdAt" to rs.getLong("createdAt"),
                "lastUsedAt" to rs.getLong("lastUsedAt"),
                "usageCount" to rs.getInt("usageCount"),
                "isPinned" to rs.getInt("isPinned"),
                "label" to rs.getString("label")
            ))
        }
        rs.close()
        stmt.close()

        assertEquals("Total records after deduplication must be 2", 2, rows.size)

        // Verify surviving record for 'https://example.com/qr'
        val survivingRecord = rows.find { it["rawValue"] == "https://example.com/qr" }
        assertNotNull(survivingRecord)
        assertEquals("Surviving ID must be MAX(id)", 2L, survivingRecord!!["id"])
        assertEquals("Custom label from older duplicate must be preserved", "My Custom Bookmark", survivingRecord["label"])
        assertEquals("Earliest createdAt must be preserved", 1000L, survivingRecord["createdAt"])
        assertEquals("Latest lastUsedAt must be preserved", 5000L, survivingRecord["lastUsedAt"])
        assertEquals("Usage count must be the sum of all scans (3 + 4)", 7, survivingRecord["usageCount"])
        assertEquals("Pinned status must be preserved if true on any duplicate", 1, survivingRecord["isPinned"])

        // Verify independent record was unchanged
        val wifiRecord = rows.find { it["rawValue"] == "WIFI:S:HomeNet;P:pass12345;;" }
        assertNotNull(wifiRecord)
        assertEquals(3L, wifiRecord!!["id"])
        assertEquals("Home Wi-Fi", wifiRecord["label"])
        assertEquals(1, wifiRecord["usageCount"])
    }
}

