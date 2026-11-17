package com.example.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** App-owned metadata only. MediaStore stays the source of truth for media. Zero vault tables. */
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

@Dao
interface AlbumDao {
    @Query("SELECT * FROM album ORDER BY pinned DESC, sortOrder, name")
    fun albums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM album_media")
    fun allRefs(): Flow<List<AlbumMediaRef>>

    @Query("SELECT mediaId FROM album_media WHERE albumId = :albumId ORDER BY position")
    fun mediaIds(albumId: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM album_media WHERE albumId = :albumId")
    fun count(albumId: Long): Flow<Int>

    @Insert
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(f: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE mediaId = :id")
    suspend fun remove(id: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tag ORDER BY name")
    fun allTags(): Flow<List<TagEntity>>

    @Query("SELECT t.* FROM tag t INNER JOIN media_tag mt ON t.id = mt.tagId WHERE mt.mediaId = :mediaId ORDER BY t.name")
    fun tagsForMedia(mediaId: Long): Flow<List<TagEntity>>

    @Query("SELECT mt.mediaId FROM media_tag mt INNER JOIN tag t ON t.id = mt.tagId WHERE LOWER(t.name) LIKE '%' || LOWER(:query) || '%'")
    suspend fun findMediaIdsByTagQuery(query: String): List<Long>

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

    @Query("DELETE FROM operation WHERE id = :id")
    suspend fun dismiss(id: String)
}

@Dao
interface EditRecipeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: EditRecipe): Long

    @Query("SELECT * FROM edit_recipe WHERE mediaId = :mediaId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestForMedia(mediaId: Long): EditRecipe?
}

@Dao
interface OcrDao {
    @Query("SELECT * FROM ocr_index WHERE mediaId = :mediaId LIMIT 1")
    suspend fun get(mediaId: Long): OcrIndexEntity?

    @Query("SELECT mediaId FROM ocr_index WHERE LOWER(extractedText) LIKE '%' || LOWER(:query) || '%'")
    suspend fun searchIds(query: String): List<Long>

    @Query("SELECT COUNT(*) FROM ocr_index WHERE extractedText != ''")
    fun indexedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OcrIndexEntity)

    @Query("DELETE FROM ocr_index")
    suspend fun clearAll()
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class FarDatabase : RoomDatabase() {
    abstract fun albums(): AlbumDao
    abstract fun favorites(): FavoriteDao
    abstract fun tags(): TagDao
    abstract fun folders(): FolderDao
    abstract fun history(): HistoryDao
    abstract fun operations(): OperationDao
    abstract fun editRecipes(): EditRecipeDao
    abstract fun ocr(): OcrDao

    companion object {
        @Volatile
        private var instance: FarDatabase? = null

        fun get(ctx: Context): FarDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, FarDatabase::class.java, "far.db")
                // Never use fallbackToDestructiveMigration: user albums and tags must survive upgrades.
                .build().also { instance = it }
        }
    }
}
