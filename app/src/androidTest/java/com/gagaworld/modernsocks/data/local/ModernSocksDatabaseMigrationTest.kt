package com.gagaworld.modernsocks.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModernSocksDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ModernSocksDatabase::class.java,
    )

    @Test
    fun migration1To2AddsSafeAllAppsDefaults() {
        helper.createDatabase(DATABASE_NAME, 1).close()

        val database = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            ModernSocksDatabase.MIGRATION_1_2,
        )
        database.query("PRAGMA table_info(proxy_profiles)").use { cursor ->
            val names = buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
            assertEquals(true, "appRoutingMode" in names)
            assertEquals(true, "appRoutingPackages" in names)
        }
        database.close()
    }

    private companion object {
        const val DATABASE_NAME = "migration-routing-test"
    }
}
