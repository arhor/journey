package com.github.arhor.journey.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class JourneyDatabaseMigrationTest {

    @Test
    fun `MIGRATION_5_6 should add light column with fully lit default for legacy exploration tiles`() {
        // Given
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        // When
        JourneyDatabase.MIGRATION_5_6.migrate(db)

        // Then
        verify(exactly = 1) {
            db.execSQL(
                """
                ALTER TABLE exploration_tile
                ADD COLUMN light REAL NOT NULL DEFAULT 1.0 CHECK(light >= 0.0 AND light <= 1.0)
                """.trimIndent(),
            )
        }
    }
}
