package com.example.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.example.core.common.LocalDiagnostics
import kotlinx.coroutines.flow.Flow
import java.io.File

/** App-owned metadata only. MediaStore stays the source of truth for public media; vault metadata stays isolated from backups and search. */
@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val createdAt: Long,
    val pinned: Boolean = false,
    val sortOrder: Int = 0,
    val coverMediaId: Long? = null,
)

@Entity(tableName = "album_media", primaryKeys = ["albumId", "mediaId"], indices = [Index("mediaId")])
data class AlbumMediaRef(val albumId: Long, val mediaId: Long, val position: Int = 0)

@Entity(tableName = "favorite")
data class FavoriteEntity(@PrimaryKey val mediaId: Long, val addedAt: Long)

@Entity(tableName = "tag")
data class TagEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val color: Int = 0xFF0A84FF.toInt())

@Entity(tableName = "media_tag", primaryKeys = ["mediaId", "tagId"], indices = [Index("tagId")])
data class MediaTagRef(val mediaId: Long, val tagId: Long)

@Entity(tableName = "excluded_folder")
data class ExcludedFolder(@PrimaryKey val relativePath: String)

@Entity(tableName = "recent_view")
data class RecentView(@PrimaryKey val mediaId: Long, val viewedAt: Long)

@Entity(tableName = "search_history")
data class SearchHistory(@PrimaryKey val query: String, val at: Long)

@Entity(tableName = "saved_search")
data class SavedSearch(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val queryJson: String)

@Entity(tableName = "folder_grant")
data class FolderGrant(@PrimaryKey val treeUri: String, val displayName: String, val addedAt: Long, val lastValidated: Long = 0)

@Entity(tableName = "file_favorite")
data class FileFavorite(@PrimaryKey val documentUri: String, val name: String)

@Entity(tableName = "edit_recipe", indices = [Index("mediaId")])
data class EditRecipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val sourceSize: Long,
    val operationsJson: String,
    val outputUri: String?,
    val version: Int = 1,
    val updatedAt: Long,
)

@Entity(tableName = "ocr_index")
data class OcrIndexEntity(
    @PrimaryKey val mediaId: Long,
    val mediaUri: String,
    val extractedText: String,
    val indexedAt: Long,
)

@Fts4(contentEntity = OcrIndexEntity::class)
@Entity(tableName = "ocr_fts")
data class OcrFtsEntity(
    val extractedText: String,
)

/** Canonical asset reconciliation row linking MediaStore / SAF URIs, fingerprints, and Far Camera provenance. */
@Entity(
    tableName = "media_asset",
    indices = [
        Index("contentUri"),
        Index("relativePath"),
        Index("sha256"),
    ],
)
data class MediaAssetEntity(
    @PrimaryKey val mediaStoreId: Long,
    val contentUri: String,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val sha256: String? = null,
    val capturedByFar: Boolean = false,
    val isTrashed: Boolean = false,
    val dateTakenMs: Long = 0L,
    val dateModifiedSec: Long = 0L,
    val lastSeenAt: Long = 0L,
)

/** Per-folder visibility, timeline exclusion, and OCR rules. */
@Entity(tableName = "folder_rule")
data class FolderRuleEntity(
    @PrimaryKey val relativePath: String,
    val hidden: Boolean = false,
    val excludedFromTimeline: Boolean = false,
    val excludedFromOcr: Boolean = false,
    val updatedAt: Long = 0L,
)

/** Incremental MediaStore & OCR cursor checkpoint. */
@Entity(tableName = "scan_checkpoint")
data class ScanCheckpointEntity(
    @PrimaryKey val scope: String,
    val lastSyncedAt: Long,
    val lastGeneration: Long = 0L,
    val lastMediaId: Long = 0L,
    val scannedCount: Int = 0,
)

/** Quarantined unreadable/corrupt media or file references for recovery display. */
@Entity(tableName = "quarantine_item")
data class QuarantineItemEntity(
    @PrimaryKey val sourceUri: String,
    val displayName: String,
    val reason: String,
    val errorCode: String,
    val createdAt: Long,
)

/** Operation journal: written BEFORE long work starts so process death can be detected and recovered. */
@Entity(tableName = "operation")
data class OperationRecord(
    @PrimaryKey val id: String,
    val type: String,
    val status: String, // Queued|Running|Paused|Completed|Cancelled|Failed|Interrupted
    val done: Int,
    val total: Int,
    val errorCode: String?,
    val updatedAt: Long,
)

/** Per-item operation journal for crash-safe pause/resume across SAF copy/move/export jobs. */
@Entity(
    tableName = "operation_item",
    indices = [Index("operationId")],
)
data class OperationItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationId: String,
    val sourceUri: String,
    val destUri: String? = null,
    val status: String, // Pending|Done|Failed|Skipped
    val errorCode: String? = null,
    val updatedAt: Long,
)

/** Private Vault encrypted media item pointing to an AES-256-GCM blob inside internal app storage. */
@Entity(
    tableName = "vault_item",
    indices = [Index("createdAt")],
)
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val encryptedFileName: String,
    val ivHex: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long,
    val originalUri: String? = null,
)

/** Hardware-encrypted personal Identity Document record (Passport, National ID, Driver's License, Insurance, Custom). */
@Entity(
    tableName = "identity_doc",
    indices = [Index("expiryDateMs")],
)
data class IdentityDocEntity(
    @PrimaryKey val id: String,
    val docType: String,
    val title: String,
    val holderName: String,
    val docNumberCipherB64: String,
    val docNumberIvHex: String,
    val issuer: String,
    val issuedDateMs: Long? = null,
    val expiryDateMs: Long? = null,
    val notesCipherB64: String? = null,
    val notesIvHex: String? = null,
    val frontFileName: String? = null,
    val frontIvHex: String? = null,
    val backFileName: String? = null,
    val backIvHex: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface AlbumDao {
    @Query("SELECT * FROM album ORDER BY pinned DESC, sortOrder, name")
    fun albums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM album")
    suspend fun allSnapshot(): List<AlbumEntity>

    @Query("SELECT * FROM album_media")
    fun allRefs(): Flow<List<AlbumMediaRef>>

    @Query("SELECT * FROM album_media")
    suspend fun allRefsSnapshot(): List<AlbumMediaRef>

    @Query("SELECT mediaId FROM album_media WHERE albumId = :albumId ORDER BY position")
    fun mediaIds(albumId: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM album_media WHERE albumId = :albumId")
    fun count(albumId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(a: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMedia(refs: List<AlbumMediaRef>)

    @Query("DELETE FROM album_media WHERE albumId = :albumId AND mediaId IN (:ids)")
    suspend fun removeMedia(albumId: Long, ids: List<Long>)

    @Query("DELETE FROM album WHERE id = :id")
    suspend fun deleteAlbum(id: Long)

    @Query("DELETE FROM album_media WHERE albumId = :id")
    suspend fun clearAlbum(id: Long)

    @Query("UPDATE album SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE album SET pinned = :pinned WHERE id = :id")
    suspend fun pin(id: Long, pinned: Boolean)

    @Query("UPDATE album SET coverMediaId = :mediaId WHERE id = :id")
    suspend fun setCover(id: Long, mediaId: Long?)
}

@Dao
interface FavoriteDao {
    @Query("SELECT mediaId FROM favorite")
    fun ids(): Flow<List<Long>>

    @Query("SELECT * FROM favorite")
    suspend fun allSnapshot(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(f: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE mediaId = :id")
    suspend fun remove(id: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tag ORDER BY name")
    fun allTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag")
    suspend fun allTagsSnapshot(): List<TagEntity>

    @Query("SELECT * FROM media_tag")
    suspend fun allRefsSnapshot(): List<MediaTagRef>

    @Query("SELECT t.* FROM tag t INNER JOIN media_tag mt ON t.id = mt.tagId WHERE mt.mediaId = :mediaId ORDER BY t.name")
    fun tagsForMedia(mediaId: Long): Flow<List<TagEntity>>

    @Query("SELECT mt.mediaId FROM media_tag mt INNER JOIN tag t ON t.id = mt.tagId WHERE LOWER(t.name) LIKE '%' || LOWER(:query) || '%'")
    suspend fun findMediaIdsByTagQuery(query: String): List<Long>

    @Query("SELECT mt.mediaId FROM media_tag mt INNER JOIN tag t ON t.id = mt.tagId WHERE LOWER(t.name) = LOWER(:name)")
    suspend fun findMediaIdsByExactTag(name: String): List<Long>

    @Query("SELECT * FROM tag WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attach(ref: MediaTagRef)

    @Query("DELETE FROM media_tag WHERE mediaId = :mediaId AND tagId = :tagId")
    suspend fun detach(mediaId: Long, tagId: Long)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folder_grant ORDER BY addedAt")
    fun grants(): Flow<List<FolderGrant>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(g: FolderGrant)

    @Query("DELETE FROM folder_grant WHERE treeUri = :uri")
    suspend fun remove(uri: String)

    @Query("SELECT * FROM excluded_folder ORDER BY relativePath")
    fun excluded(): Flow<List<ExcludedFolder>>

    @Query("SELECT * FROM excluded_folder")
    suspend fun excludedSnapshot(): List<ExcludedFolder>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun exclude(f: ExcludedFolder)

    @Query("DELETE FROM excluded_folder WHERE relativePath = :p")
    suspend fun include(p: String)

    @Query("SELECT * FROM file_favorite ORDER BY name")
    fun favoriteFolders(): Flow<List<FileFavorite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavoriteFile(f: FileFavorite)

    @Query("DELETE FROM file_favorite WHERE documentUri = :uri")
    suspend fun removeFavoriteFile(uri: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM search_history ORDER BY at DESC LIMIT 20")
    fun recent(): Flow<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(h: SearchHistory)

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun remove(query: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun view(r: RecentView)

    @Query("SELECT mediaId FROM recent_view ORDER BY viewedAt DESC LIMIT 200")
    fun recentViews(): Flow<List<Long>>
}

@Dao
interface OperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(o: OperationRecord)

    @Query("UPDATE operation SET status = 'Interrupted', updatedAt = :now WHERE status IN ('Queued','Running')")
    suspend fun markInterrupted(now: Long)

    @Query("SELECT * FROM operation WHERE status = 'Interrupted'")
    fun interrupted(): Flow<List<OperationRecord>>

    @Query("SELECT * FROM operation ORDER BY updatedAt DESC LIMIT 50")
    fun recent(): Flow<List<OperationRecord>>

    @Query("DELETE FROM operation WHERE id = :id")
    suspend fun dismiss(id: String)

    @Query("DELETE FROM operation")
    suspend fun clearAll()
}

@Dao
interface OperationItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<OperationItemEntity>)

    @Query("SELECT * FROM operation_item WHERE operationId = :opId ORDER BY id")
    suspend fun forOperation(opId: String): List<OperationItemEntity>

    @Query("UPDATE operation_item SET status = :status, destUri = :destUri, errorCode = :errorCode, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, destUri: String?, errorCode: String?, now: Long)

    @Query("DELETE FROM operation_item WHERE operationId = :opId")
    suspend fun clearForOperation(opId: String)
}

@Dao
interface EditRecipeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: EditRecipe): Long

    @Query("SELECT * FROM edit_recipe WHERE mediaId = :mediaId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestForMedia(mediaId: Long): EditRecipe?

    @Query("SELECT * FROM edit_recipe")
    suspend fun allSnapshot(): List<EditRecipe>
}

@Dao
interface OcrDao {
    @Query("SELECT * FROM ocr_index WHERE mediaId = :mediaId LIMIT 1")
    suspend fun get(mediaId: Long): OcrIndexEntity?

    @Query("SELECT mediaId FROM ocr_index WHERE LOWER(extractedText) LIKE '%' || LOWER(:query) || '%'")
    suspend fun searchIdsLike(query: String): List<Long>

    @Query("SELECT ocr_index.mediaId FROM ocr_index INNER JOIN ocr_fts ON ocr_index.mediaId = ocr_fts.rowid WHERE ocr_fts MATCH :ftsQuery")
    suspend fun searchIdsFts(ftsQuery: String): List<Long>

    suspend fun searchIds(query: String): List<Long> {
        val tokens = Regex("[\\p{L}\\p{N}_]+").findAll(query).map { "${it.value}*" }.toList()
        if (tokens.isEmpty()) return emptyList()
        return runCatching { searchIdsFts(tokens.joinToString(" ")) }
            .getOrElse { searchIdsLike(query) }
    }

    @Query("SELECT COUNT(*) FROM ocr_index WHERE extractedText != ''")
    fun indexedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OcrIndexEntity)

    @Query("DELETE FROM ocr_index")
    suspend fun clearAll()
}

@Dao
interface SavedSearchDao {
    @Query("SELECT * FROM saved_search ORDER BY id DESC")
    fun all(): Flow<List<SavedSearch>>

    @Query("SELECT * FROM saved_search")
    suspend fun allSnapshot(): List<SavedSearch>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(search: SavedSearch): Long

    @Query("DELETE FROM saved_search WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MediaAssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assets: List<MediaAssetEntity>)

    @Query("SELECT * FROM media_asset WHERE mediaStoreId = :id LIMIT 1")
    suspend fun get(id: Long): MediaAssetEntity?

    @Query("SELECT * FROM media_asset WHERE capturedByFar = 1 ORDER BY dateTakenMs DESC")
    fun capturedByFar(): Flow<List<MediaAssetEntity>>

    @Query("UPDATE media_asset SET capturedByFar = 1 WHERE mediaStoreId = :id")
    suspend fun markCapturedByFar(id: Long)

    @Query("UPDATE media_asset SET sha256 = :sha256 WHERE mediaStoreId = :id")
    suspend fun updateSha256(id: Long, sha256: String)

    @Query("SELECT * FROM media_asset WHERE sha256 IS NOT NULL")
    suspend fun withSha256(): List<MediaAssetEntity>

    @Query("SELECT mediaStoreId FROM media_asset WHERE capturedByFar = 1")
    suspend fun allIdsWithCapturedByFar(): List<Long>

    @Query("DELETE FROM media_asset WHERE lastSeenAt < :cutoffMs")
    suspend fun deleteNotSeenSince(cutoffMs: Long): Int

    @Query("SELECT COUNT(*) FROM media_asset")
    suspend fun count(): Int

    @Query("DELETE FROM media_asset WHERE mediaStoreId NOT IN (:activeIds)")
    suspend fun deleteMissing(activeIds: List<Long>)
}

@Dao
interface FolderRuleDao {
    @Query("SELECT * FROM folder_rule ORDER BY relativePath")
    fun all(): Flow<List<FolderRuleEntity>>

    @Query("SELECT * FROM folder_rule")
    suspend fun allSnapshot(): List<FolderRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: FolderRuleEntity)

    @Query("DELETE FROM folder_rule WHERE relativePath = :path")
    suspend fun delete(path: String)
}

@Dao
interface ScanCheckpointDao {
    @Query("SELECT * FROM scan_checkpoint WHERE scope = :scope LIMIT 1")
    suspend fun get(scope: String): ScanCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cp: ScanCheckpointEntity)
}

@Dao
interface QuarantineDao {
    @Query("SELECT * FROM quarantine_item ORDER BY createdAt DESC")
    fun all(): Flow<List<QuarantineItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: QuarantineItemEntity)

    @Query("DELETE FROM quarantine_item WHERE sourceUri = :uri")
    suspend fun remove(uri: String)

    @Query("DELETE FROM quarantine_item")
    suspend fun clearAll()
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_item ORDER BY createdAt DESC")
    fun all(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_item ORDER BY createdAt DESC")
    suspend fun allSnapshot(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_item WHERE id = :id LIMIT 1")
    suspend fun get(id: String): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: VaultItemEntity)

    @Query("DELETE FROM vault_item WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface IdentityDocDao {
    @Query("SELECT * FROM identity_doc ORDER BY updatedAt DESC")
    fun all(): Flow<List<IdentityDocEntity>>

    @Query("SELECT * FROM identity_doc WHERE id = :id LIMIT 1")
    suspend fun get(id: String): IdentityDocEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: IdentityDocEntity)

    @Query("DELETE FROM identity_doc WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [
        AlbumEntity::class,
        AlbumMediaRef::class,
        FavoriteEntity::class,
        TagEntity::class,
        MediaTagRef::class,
        ExcludedFolder::class,
        RecentView::class,
        SearchHistory::class,
        SavedSearch::class,
        FolderGrant::class,
        FileFavorite::class,
        EditRecipe::class,
        OperationRecord::class,
        OcrIndexEntity::class,
        OcrFtsEntity::class,
        MediaAssetEntity::class,
        FolderRuleEntity::class,
        ScanCheckpointEntity::class,
        QuarantineItemEntity::class,
        OperationItemEntity::class,
        VaultItemEntity::class,
        IdentityDocEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class FarDatabase : RoomDatabase() {
    abstract fun albums(): AlbumDao
    abstract fun favorites(): FavoriteDao
    abstract fun tags(): TagDao
    abstract fun folders(): FolderDao
    abstract fun history(): HistoryDao
    abstract fun operations(): OperationDao
    abstract fun operationItems(): OperationItemDao
    abstract fun editRecipes(): EditRecipeDao
    abstract fun ocr(): OcrDao
    abstract fun savedSearches(): SavedSearchDao
    abstract fun mediaAssets(): MediaAssetDao
    abstract fun folderRules(): FolderRuleDao
    abstract fun scanCheckpoints(): ScanCheckpointDao
    abstract fun quarantine(): QuarantineDao
    abstract fun vaultItems(): VaultDao
    abstract fun identityDocs(): IdentityDocDao

    companion object {
        @Volatile
        private var instance: FarDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_asset` (
                        `mediaStoreId` INTEGER NOT NULL,
                        `contentUri` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `sha256` TEXT,
                        `capturedByFar` INTEGER NOT NULL,
                        `isTrashed` INTEGER NOT NULL,
                        `dateTakenMs` INTEGER NOT NULL,
                        `dateModifiedSec` INTEGER NOT NULL,
                        `lastSeenAt` INTEGER NOT NULL,
                        PRIMARY KEY(`mediaStoreId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_asset_contentUri` ON `media_asset` (`contentUri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_asset_relativePath` ON `media_asset` (`relativePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_asset_sha256` ON `media_asset` (`sha256`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `folder_rule` (
                        `relativePath` TEXT NOT NULL,
                        `hidden` INTEGER NOT NULL,
                        `excludedFromTimeline` INTEGER NOT NULL,
                        `excludedFromOcr` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`relativePath`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scan_checkpoint` (
                        `scope` TEXT NOT NULL,
                        `lastSyncedAt` INTEGER NOT NULL,
                        `lastGeneration` INTEGER NOT NULL,
                        `lastMediaId` INTEGER NOT NULL,
                        `scannedCount` INTEGER NOT NULL,
                        PRIMARY KEY(`scope`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `quarantine_item` (
                        `sourceUri` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `errorCode` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceUri`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `operation_item` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `operationId` TEXT NOT NULL,
                        `sourceUri` TEXT NOT NULL,
                        `destUri` TEXT,
                        `status` TEXT NOT NULL,
                        `errorCode` TEXT,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_operation_item_operationId` ON `operation_item` (`operationId`)")

                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `ocr_fts` USING FTS4(`extractedText` TEXT NOT NULL, content=`ocr_index`)")
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ocr_fts_BEFORE_UPDATE BEFORE UPDATE ON `ocr_index` BEGIN DELETE FROM `ocr_fts` WHERE `docid`=OLD.`rowid`; END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ocr_fts_BEFORE_DELETE BEFORE DELETE ON `ocr_index` BEGIN DELETE FROM `ocr_fts` WHERE `docid`=OLD.`rowid`; END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ocr_fts_AFTER_UPDATE AFTER UPDATE ON `ocr_index` BEGIN INSERT INTO `ocr_fts`(`docid`, `extractedText`) VALUES (NEW.`rowid`, NEW.`extractedText`); END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ocr_fts_AFTER_INSERT AFTER INSERT ON `ocr_index` BEGIN INSERT INTO `ocr_fts`(`docid`, `extractedText`) VALUES (NEW.`rowid`, NEW.`extractedText`); END
                    """.trimIndent()
                )
                db.execSQL("INSERT INTO `ocr_fts`(`ocr_fts`) VALUES('rebuild')")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vault_item` (
                        `id` TEXT NOT NULL,
                        `encryptedFileName` TEXT NOT NULL,
                        `ivHex` TEXT NOT NULL,
                        `originalName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `isVideo` INTEGER NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `originalUri` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_item_createdAt` ON `vault_item` (`createdAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `identity_doc` (
                        `id` TEXT NOT NULL,
                        `docType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `holderName` TEXT NOT NULL,
                        `docNumberCipherB64` TEXT NOT NULL,
                        `docNumberIvHex` TEXT NOT NULL,
                        `issuer` TEXT NOT NULL,
                        `issuedDateMs` INTEGER,
                        `expiryDateMs` INTEGER,
                        `notesCipherB64` TEXT,
                        `notesIvHex` TEXT,
                        `frontFileName` TEXT,
                        `frontIvHex` TEXT,
                        `backFileName` TEXT,
                        `backIvHex` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_identity_doc_expiryDateMs` ON `identity_doc` (`expiryDateMs`)")
            }
        }

        fun get(ctx: Context): FarDatabase = instance ?: synchronized(this) {
            val appCtx = ctx.applicationContext
            instance ?: Room.databaseBuilder(appCtx, FarDatabase::class.java, "far.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .openHelperFactory(RecoveringOpenHelperFactory(appCtx, FrameworkSQLiteOpenHelperFactory()))
                .build().also { instance = it }
        }

        @androidx.annotation.VisibleForTesting
        internal fun resetForTests() = synchronized(this) {
            runCatching { instance?.close() }
            instance = null
        }
    }
}

private class RecoveringOpenHelperFactory(
    private val appContext: Context,
    private val delegate: SupportSQLiteOpenHelper.Factory,
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val initial = delegate.create(configuration)
        return RecoveringOpenHelper(appContext, configuration.name, initial) {
            delegate.create(configuration)
        }
    }
}

private class RecoveringOpenHelper(
    private val appContext: Context,
    private val dbName: String?,
    initial: SupportSQLiteOpenHelper,
    private val recreate: () -> SupportSQLiteOpenHelper,
) : SupportSQLiteOpenHelper {
    @Volatile
    private var current: SupportSQLiteOpenHelper = initial

    override val databaseName: String?
        get() = current.databaseName

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        current.setWriteAheadLoggingEnabled(enabled)
    }

    override val writableDatabase: SupportSQLiteDatabase
        get() = openSafely { it.writableDatabase }

    override val readableDatabase: SupportSQLiteDatabase
        get() = openSafely { it.readableDatabase }

    private fun openSafely(op: (SupportSQLiteOpenHelper) -> SupportSQLiteDatabase): SupportSQLiteDatabase {
        return try {
            op(current)
        } catch (e: IllegalStateException) {
            recover(e, op)
        } catch (e: SQLiteException) {
            recover(e, op)
        }
    }

    /**
     * Non-destructive database salvage:
     * 1. Checkpoint WAL if readable, then archive existing DB files to `filesDir/db_corrupt/` (max 3 snapshots).
     * 2. Salvage surviving user metadata rows (albums, favorites, tags, rules, recipes, saved searches) read-only.
     * 3. Recreate clean v2 schema and restore salvaged rows atomically.
     */
    @Synchronized
    private fun recover(
        cause: Throwable,
        op: (SupportSQLiteOpenHelper) -> SupportSQLiteDatabase,
    ): SupportSQLiteDatabase {
        return try {
            op(current)
        } catch (_: Throwable) {
            LocalDiagnostics.recordEvent(appContext, "FG-DB-REC", "room_open", cause.javaClass.simpleName)
            runCatching { current.close() }
            val salvaged = backupAndSalvage(cause)
            current = recreate()
            val freshDb = op(current)
            restoreSalvaged(freshDb, salvaged)
            freshDb
        }
    }

    private data class SalvagedMetadata(
        val tableRows: Map<String, List<ContentValues>>,
    )

    private fun backupAndSalvage(cause: Throwable): SalvagedMetadata {
        if (dbName.isNullOrBlank()) return SalvagedMetadata(emptyMap())
        val dbFile = appContext.getDatabasePath(dbName)
        if (!dbFile.exists()) return SalvagedMetadata(emptyMap())

        // 1. Best-effort WAL checkpoint on existing DB before backing up
        runCatching {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
                raw.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)?.use { it.moveToFirst() }
            }
        }

        // 2. Archive corrupt/incompatible DB file instead of blind deletion
        val backupDir = File(appContext.filesDir, "db_corrupt").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val backupDb = File(backupDir, "${stamp}_$dbName")
        runCatching {
            dbFile.copyTo(backupDb, overwrite = true)
            File("${dbFile.absolutePath}-wal").takeIf { it.exists() }?.copyTo(File("${backupDb.absolutePath}-wal"), overwrite = true)
            File("${dbFile.absolutePath}-shm").takeIf { it.exists() }?.copyTo(File("${backupDb.absolutePath}-shm"), overwrite = true)
            backupDir.listFiles { f -> f.name.endsWith("_$dbName") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(3)
                ?.forEach { old ->
                    old.delete()
                    File("${old.absolutePath}-wal").delete()
                    File("${old.absolutePath}-shm").delete()
                }
        }

        // 3. Salvage user metadata read-only before clearing primary DB path for fresh rebuild
        val salvageTables = listOf(
            "album",
            "album_media",
            "favorite",
            "tag",
            "media_tag",
            "excluded_folder",
            "saved_search",
            "folder_grant",
            "file_favorite",
            "edit_recipe",
            "folder_rule",
            "vault_item",
            "identity_doc",
        )
        val salvaged = LinkedHashMap<String, List<ContentValues>>()
        var totalRows = 0
        runCatching {
            SQLiteDatabase.openDatabase(backupDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { ro ->
                val existingTables = HashSet<String>()
                ro.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)?.use { c ->
                    while (c.moveToNext()) existingTables += c.getString(0)
                }
                for (table in salvageTables) {
                    if (table !in existingTables) continue
                    val list = ArrayList<ContentValues>()
                    runCatching {
                        ro.rawQuery("SELECT * FROM `$table`", null)?.use { c ->
                            val cols = c.columnNames
                            while (c.moveToNext()) {
                                val cv = ContentValues()
                                for (i in cols.indices) {
                                    when (c.getType(i)) {
                                        android.database.Cursor.FIELD_TYPE_NULL -> cv.putNull(cols[i])
                                        android.database.Cursor.FIELD_TYPE_INTEGER -> cv.put(cols[i], c.getLong(i))
                                        android.database.Cursor.FIELD_TYPE_FLOAT -> cv.put(cols[i], c.getDouble(i))
                                        android.database.Cursor.FIELD_TYPE_STRING -> cv.put(cols[i], c.getString(i))
                                        android.database.Cursor.FIELD_TYPE_BLOB -> cv.put(cols[i], c.getBlob(i))
                                    }
                                }
                                list += cv
                            }
                        }
                    }
                    if (list.isNotEmpty()) {
                        salvaged[table] = list
                        totalRows += list.size
                    }
                }
            }
        }

        // Remove primary DB path so SQLiteOpenHelper cleanly builds the current schema, keeping the backup snapshot intact
        runCatching {
            dbFile.delete()
            File("${dbFile.absolutePath}-wal").delete()
            File("${dbFile.absolutePath}-shm").delete()
            File("${dbFile.absolutePath}-journal").delete()
        }

        runCatching {
            File(appContext.filesDir, "last_db_recovery.txt").writeText(
                "$stamp|reason=${cause.javaClass.simpleName}|salvagedRows=$totalRows|backup=${backupDb.name}"
            )
        }
        return SalvagedMetadata(salvaged)
    }

    private fun restoreSalvaged(target: SupportSQLiteDatabase, salvaged: SalvagedMetadata) {
        if (salvaged.tableRows.isEmpty()) return
        target.beginTransaction()
        try {
            for ((table, rows) in salvaged.tableRows) {
                for (cv in rows) {
                    runCatching { target.insert(table, SQLiteDatabase.CONFLICT_IGNORE, cv) }
                }
            }
            target.setTransactionSuccessful()
        } finally {
            target.endTransaction()
        }
    }

    override fun close() {
        runCatching { current.close() }
    }
}
