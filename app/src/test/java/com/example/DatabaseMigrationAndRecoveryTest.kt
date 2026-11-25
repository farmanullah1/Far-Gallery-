package com.example

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.FarResult
import com.example.core.database.AlbumEntity
import com.example.core.database.FarDatabase
import com.example.core.database.FavoriteEntity
import com.example.core.database.FolderRuleEntity
import com.example.core.database.MetadataBackup
import com.example.core.database.OcrIndexEntity
import com.example.core.database.TagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationAndRecoveryTest {

    @Test
    fun migrate1To2_preservesExistingUserDataAndRebuildsOcrFts() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val schema1File = listOf(
            File("schemas/com.example.core.database.FarDatabase/1.json"),
            File("app/schemas/com.example.core.database.FarDatabase/1.json"),
        ).firstOrNull { it.exists() } ?: error("1.json schema not found")

        val schemaJson = JSONObject(schema1File.readText()).getJSONObject("database")
        val dbFile = app.getDatabasePath("migration-test-1-2.db").apply {
            parentFile?.mkdirs()
            delete()
        }
        File("${dbFile.absolutePath}-wal").delete()
        File("${dbFile.absolutePath}-shm").delete()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { raw ->
            val entities = schemaJson.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", table)
                raw.execSQL(createSql)
                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val idxSql = indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
                        raw.execSQL(idxSql)
                    }
                }
            }
            val setupStmts = schemaJson.optJSONArray("setupQueries")
            if (setupStmts != null) {
                for (i in 0 until setupStmts.length()) {
                    raw.execSQL(setupStmts.getString(i))
                }
            }
            raw.execSQL("INSERT INTO album (id, name, createdAt, pinned, sortOrder) VALUES (1, 'Trips', 1000, 1, 0)")
            raw.execSQL("INSERT INTO album_media (albumId, mediaId, position) VALUES (1, 101, 0)")
            raw.execSQL("INSERT INTO favorite (mediaId, addedAt) VALUES (101, 1000)")
            raw.execSQL("INSERT INTO tag (id, name, color) VALUES (1, 'passport', -16744193)")
            raw.execSQL("INSERT INTO media_tag (mediaId, tagId) VALUES (101, 1)")
            raw.execSQL("INSERT INTO ocr_index (mediaId, mediaUri, extractedText, indexedAt) VALUES (101, 'content://media/101', 'Boarding Pass Tokyo', 1000)")
            raw.version = 1
        }

        // Room runs MIGRATION_1_2 -> MIGRATION_2_3 AND validates every column/index/FTS4 table against latest schema
        val migratedDb = Room.databaseBuilder(app, FarDatabase::class.java, dbFile.name)
            .addMigrations(FarDatabase.MIGRATION_1_2, FarDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val albums = migratedDb.albums().albums().first()
            assertEquals(1, albums.size)
            assertEquals("Trips", albums.first().name)
            assertTrue(101L in migratedDb.favorites().ids().first())
            assertEquals(listOf(101L), migratedDb.ocr().searchIds("Tokyo"))
        } finally {
            migratedDb.close()
        }
    }

    @Test
    fun salvageRecovery_backsUpIncompatibleDbAndPreservesSurvivingRows() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        FarDatabase.resetForTests()
        val dbName = "far.db"
        val dbFile = app.getDatabasePath(dbName).apply {
            parentFile?.mkdirs()
            delete()
        }
        File("${dbFile.absolutePath}-wal").delete()
        File("${dbFile.absolutePath}-shm").delete()
        File(app.filesDir, "db_corrupt").deleteRecursively()

        // Create an unreadable-to-Room v99 SQLite file that holds surviving user tables
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { raw ->
            raw.execSQL("CREATE TABLE album (id INTEGER PRIMARY KEY, name TEXT NOT NULL, description TEXT, createdAt INTEGER NOT NULL, pinned INTEGER NOT NULL, sortOrder INTEGER NOT NULL, coverMediaId INTEGER)")
            raw.execSQL("CREATE TABLE favorite (mediaId INTEGER PRIMARY KEY, addedAt INTEGER NOT NULL)")
            raw.execSQL("INSERT INTO album (id, name, description, createdAt, pinned, sortOrder, coverMediaId) VALUES (7, 'Salvaged', NULL, 1234, 1, 0, NULL)")
            raw.execSQL("INSERT INTO favorite (mediaId, addedAt) VALUES (777, 1234)")
            raw.version = 99
        }

        val recovered = FarDatabase.get(app)
        val albums = recovered.albums().albums().first()
        val favs = recovered.favorites().ids().first()
        assertTrue(albums.any { it.name == "Salvaged" })
        assertTrue(777L in favs)

        val backups = File(app.filesDir, "db_corrupt").listFiles().orEmpty()
        assertTrue("Expected corrupt backup file in db_corrupt/", backups.any { it.name.endsWith("_far.db") })
        assertTrue(File(app.filesDir, "last_db_recovery.txt").exists())
    }

    @Test
    fun metadataBackup_roundTripsAllCollectionsAndFolderRules() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val sourceDb = Room.inMemoryDatabaseBuilder(app, FarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val targetDb = Room.inMemoryDatabaseBuilder(app, FarDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        sourceDb.albums().insert(AlbumEntity(id = 1, name = "Family", createdAt = 100L, pinned = true))
        sourceDb.favorites().add(FavoriteEntity(mediaId = 55L, addedAt = 100L))
        sourceDb.tags().insertTag(TagEntity(id = 1, name = "receipts"))
        sourceDb.folderRules().upsert(FolderRuleEntity(relativePath = "Pictures/Work/", hidden = true, excludedFromTimeline = true))
        sourceDb.ocr().upsert(OcrIndexEntity(55L, "content://55", "Invoice 2026", 100L))

        val exported = MetadataBackup.exportJson(sourceDb)
        assertTrue(exported is FarResult.Ok)
        val json = (exported as FarResult.Ok).value

        val imported = MetadataBackup.importJson(targetDb, json)
        assertTrue(imported is FarResult.Ok)
        val summary = (imported as FarResult.Ok).value
        assertEquals(1, summary.albums)
        assertEquals(1, summary.favorites)
        assertEquals(1, summary.tags)
        assertEquals(1, summary.folderRules)

        assertTrue(targetDb.albums().albums().first().any { it.name == "Family" })
        assertTrue(55L in targetDb.favorites().ids().first())
        assertTrue(targetDb.folderRules().all().first().any { it.relativePath == "Pictures/Work/" && it.hidden })
        assertEquals(listOf(55L), sourceDb.ocr().searchIds("invoice"))

        sourceDb.close()
        targetDb.close()
    }
}
