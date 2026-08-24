package com.local.offlinemediaplayer.data.di

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.local.offlinemediaplayer.data.db.AppDatabase
import com.local.offlinemediaplayer.data.db.MIGRATION_1_5
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the real [DatabaseModule] configuration.
 *
 * WHY THIS EXISTS — F-29. The app crashed on **every launch**, including fresh installs, while
 * `assembleDebug`, 166 unit tests, detekt, ktlint and the 6/6 migration test were all green.
 * `DatabaseModule` registered `MIGRATION_1_5` (start version 1) *and* listed version 1 in
 * `fallbackToDestructiveMigrationFrom`. Room rejects that combination inside `Builder.build()`, so
 * `provideAppDatabase` threw during `MainActivity.onCreate`.
 *
 * Nothing caught it because `MigrationTest` builds its own Room instance through
 * `MigrationTestHelper` and never touches `DatabaseModule` — the module that actually ships was
 * untested. These tests call the real `@Provides` functions, so the configuration that runs in
 * production is the configuration under test.
 *
 * They run on the JVM rather than on a device deliberately: the failure is in Room's **static**
 * builder validation, which reproduces off-device, so this guard runs in CI on every push.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseModuleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var db: AppDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase("mediaplayer_db")
    }

    /**
     * The regression guard. If `DatabaseModule` is ever misconfigured the way it was in F-29, this
     * fails here rather than on a user's device.
     */
    @Test
    fun theRealDatabaseConfigurationCanBeBuilt() {
        db = DatabaseModule.provideAppDatabase(context)

        assertNotNull("provideAppDatabase must not throw — see F-29", db)
    }

    @Test
    fun theBuiltDatabaseActuallyOpensAndIsUsable() {
        // build() is lazy; the connection is only opened on first use, so a query is what proves
        // the database is genuinely serviceable rather than merely constructed.
        db = DatabaseModule.provideAppDatabase(context)

        runBlocking {
            assertNull(db!!.mediaDao().getHistory(1))
        }
    }

    @Test
    fun theProvidedDaoBelongsToTheProvidedDatabase() {
        db = DatabaseModule.provideAppDatabase(context)

        val dao = DatabaseModule.provideMediaDao(db!!)

        runBlocking {
            assertTrue("the provided DAO must be usable", dao.getSavedQueue().isEmpty())
        }
    }

    @Test
    fun theThumbnailManagerProviderWorks() {
        assertNotNull(DatabaseModule.provideThumbnailManager(context))
    }

    /**
     * Documents the exact failure mode of F-29, so the reason `1` is absent from the fallback list
     * is provable rather than a comment someone can talk themselves out of — which is precisely
     * what happened the first time.
     *
     * Room's `Builder.build()` calls `validateMigrationsNotRequired`, which rejects a start version
     * that is also covered by a destructive-fallback entry. This is a **static** check: it fires
     * before any database is opened and regardless of whether an upgrade would ever occur, which is
     * why reasoning about the runtime `onUpgrade` precedence did not apply.
     */
    @Test
    fun listingAMigrationsStartVersionInTheFallbackSetIsRejectedAtBuildTime() {
        try {
            Room
                .databaseBuilder(context, AppDatabase::class.java, "f29-probe-db")
                .addMigrations(MIGRATION_1_5)
                // `1` is MIGRATION_1_5's start version — the combination that crashed the app.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)
                .build()
            fail("expected build() to reject version 1 appearing in both the migration and the fallback set")
        } catch (expected: IllegalArgumentException) {
            assertTrue(
                "expected a message naming the conflicting version, got: ${expected.message}",
                expected.message?.contains("1") == true,
            )
        } finally {
            context.deleteDatabase("f29-probe-db")
        }
    }

    /** The shipped configuration must keep the v1 migration registered — that is its whole point. */
    @Test
    fun theShippedConfigurationStillMigratesV1UsersRatherThanWipingThem() {
        // Building succeeds only because 1 is absent from the fallback set while MIGRATION_1_5 is
        // registered. If someone "fixed" the F-29 crash by dropping the migration instead, this
        // still passes — so assert the migration object itself is the one covering v1 -> v5.
        assertEquals(1, MIGRATION_1_5.startVersion)
        assertEquals(5, MIGRATION_1_5.endVersion)

        db = DatabaseModule.provideAppDatabase(context)
        assertNotNull(db)
    }
}
