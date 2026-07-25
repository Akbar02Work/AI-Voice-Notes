package com.example.voicenotes.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun deleteDatabaseBeforeTest() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun deleteDatabaseAfterTest() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migration3To4PreservesNotesAndAddsUnpinnedDefault() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(CREATE_VERSION_3_NOTES)
                        db.execSQL(
                            """
                            INSERT INTO notes
                                (id, title, rawText, summary, audioPath, status, timestamp)
                            VALUES
                                (7, 'Shopping', 'Buy milk', 'Groceries', '/audio.m4a', 'SYNCED', 10)
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                }
            )
            .build()

        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DATABASE)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            database.openHelper.writableDatabase.query(
                "SELECT title, rawText, isPinned FROM notes WHERE id = 7"
            ).use { cursor ->
                assertFalse(cursor.isAfterLast)
                cursor.moveToFirst()
                assertEquals("Shopping", cursor.getString(0))
                assertEquals("Buy milk", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "migration-test"
        const val CREATE_VERSION_3_NOTES =
            """
            CREATE TABLE IF NOT EXISTS notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                rawText TEXT NOT NULL,
                summary TEXT NOT NULL,
                audioPath TEXT NOT NULL,
                status TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """
    }
}
