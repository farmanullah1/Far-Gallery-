package com.example.core.database

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.core.common.FarResult
import com.example.core.common.farCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class MetadataRestoreSummary(
    val albums: Int,
    val favorites: Int,
    val tags: Int,
    val folderRules: Int,
    val savedSearches: Int,
)

/**
 * Offline JSON backup/restore of user-authored Room metadata (albums, favorites, tags,
 * folder rules, excluded folders, saved searches, edit recipes).
 * Never exports Vault secrets or private documents.
 */
object MetadataBackup {
    private const val BACKUP_FORMAT_VERSION = 1

    suspend fun exportJson(db: FarDatabase): FarResult<String> = withContext(Dispatchers.IO) {
        farCatching {
            val root = JSONObject().apply {
                put("formatVersion", BACKUP_FORMAT_VERSION)
                put("exportedAt", System.currentTimeMillis())

                put("albums", JSONArray().apply {
                    db.albums().allSnapshot().forEach { a ->
                        put(JSONObject().apply {
                            put("id", a.id)
                            put("name", a.name)
                            put("description", a.description ?: JSONObject.NULL)
                            put("createdAt", a.createdAt)
                            put("pinned", a.pinned)
                            put("sortOrder", a.sortOrder)
                            put("coverMediaId", a.coverMediaId ?: JSONObject.NULL)
                        })
                    }
                })

                put("albumRefs", JSONArray().apply {
                    db.albums().allRefsSnapshot().forEach { r ->
                        put(JSONObject().apply {
                            put("albumId", r.albumId)
                            put("mediaId", r.mediaId)
                            put("position", r.position)
                        })
                    }
                })

                put("favorites", JSONArray().apply {
                    db.favorites().allSnapshot().forEach { f ->
                        put(JSONObject().apply {
                            put("mediaId", f.mediaId)
                            put("addedAt", f.addedAt)
                        })
                    }
                })

                put("tags", JSONArray().apply {
                    db.tags().allTagsSnapshot().forEach { t ->
                        put(JSONObject().apply {
                            put("id", t.id)
                            put("name", t.name)
                            put("color", t.color)
                        })
                    }
                })

                put("tagRefs", JSONArray().apply {
                    db.tags().allRefsSnapshot().forEach { tr ->
                        put(JSONObject().apply {
                            put("mediaId", tr.mediaId)
                            put("tagId", tr.tagId)
                        })
                    }
                })

                put("excludedFolders", JSONArray().apply {
                    db.folders().excludedSnapshot().forEach { put(it.relativePath) }
                })

                put("folderRules", JSONArray().apply {
                    db.folderRules().allSnapshot().forEach { fr ->
                        put(JSONObject().apply {
                            put("relativePath", fr.relativePath)
                            put("hidden", fr.hidden)
                            put("excludedFromTimeline", fr.excludedFromTimeline)
                            put("excludedFromOcr", fr.excludedFromOcr)
                            put("updatedAt", fr.updatedAt)
                        })
                    }
                })

                put("savedSearches", JSONArray().apply {
                    db.savedSearches().allSnapshot().forEach { ss ->
                        put(JSONObject().apply {
                            put("name", ss.name)
                            put("queryJson", ss.queryJson)
                        })
                    }
                })

                put("editRecipes", JSONArray().apply {
                    db.editRecipes().allSnapshot().forEach { er ->
                        put(JSONObject().apply {
                            put("mediaId", er.mediaId)
                            put("sourceSize", er.sourceSize)
                            put("operationsJson", er.operationsJson)
                            put("outputUri", er.outputUri ?: JSONObject.NULL)
                            put("version", er.version)
                            put("updatedAt", er.updatedAt)
                        })
                    }
                })
            }
            root.toString(2)
        }
    }

    suspend fun importJson(db: FarDatabase, rawJson: String): FarResult<MetadataRestoreSummary> = withContext(Dispatchers.IO) {
        farCatching {
            val root = JSONObject(rawJson)
            val version = root.optInt("formatVersion", -1)
            if (version != BACKUP_FORMAT_VERSION) throw IllegalArgumentException("unsupported backup version $version")

            var albumCount = 0
            var favCount = 0
            var tagCount = 0
            var ruleCount = 0
            var searchCount = 0

            db.withTransaction {
                val albumIdRemap = HashMap<Long, Long>()
                val albumsArr = root.optJSONArray("albums")
                if (albumsArr != null) {
                    val existing = db.albums().allSnapshot().associateBy { it.name.lowercase() }
                    for (i in 0 until albumsArr.length()) {
                        val o = albumsArr.optJSONObject(i) ?: continue
                        val oldId = o.optLong("id", 0L)
                        val name = o.optString("name").trim()
                        if (name.isEmpty()) continue
                        val match = existing[name.lowercase()]
                        val targetId = if (match != null) {
                            match.id
                        } else {
                            db.albums().insert(
                                AlbumEntity(
                                    name = name,
                                    description = if (o.isNull("description")) null else o.optString("description"),
                                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                                    pinned = o.optBoolean("pinned", false),
                                    sortOrder = o.optInt("sortOrder", 0),
                                    coverMediaId = if (o.isNull("coverMediaId")) null else o.optLong("coverMediaId"),
                                )
                            )
                        }
                        if (oldId > 0L) albumIdRemap[oldId] = targetId
                        albumCount++
                    }
                }

                val refsArr = root.optJSONArray("albumRefs")
                if (refsArr != null) {
                    val refs = buildList {
                        for (i in 0 until refsArr.length()) {
                            val o = refsArr.optJSONObject(i) ?: continue
                            val mappedAlbumId = albumIdRemap[o.optLong("albumId", 0L)] ?: continue
                            val mediaId = o.optLong("mediaId", 0L)
                            if (mediaId > 0L) {
                                add(AlbumMediaRef(mappedAlbumId, mediaId, o.optInt("position", 0)))
                            }
                        }
                    }
                    if (refs.isNotEmpty()) db.albums().addMedia(refs)
                }

                val favArr = root.optJSONArray("favorites")
                if (favArr != null) {
                    for (i in 0 until favArr.length()) {
                        val o = favArr.optJSONObject(i) ?: continue
                        val mediaId = o.optLong("mediaId", 0L)
                        if (mediaId > 0L) {
                            db.favorites().add(FavoriteEntity(mediaId, o.optLong("addedAt", System.currentTimeMillis())))
                            favCount++
                        }
                    }
                }

                val tagIdRemap = HashMap<Long, Long>()
                val tagsArr = root.optJSONArray("tags")
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        val o = tagsArr.optJSONObject(i) ?: continue
                        val oldId = o.optLong("id", 0L)
                        val name = o.optString("name").trim()
                        if (name.isEmpty()) continue
                        val existing = db.tags().findByName(name)
                        val targetId = existing?.id ?: run {
                            val inserted = db.tags().insertTag(TagEntity(name = name, color = o.optInt("color", 0xFF0A84FF.toInt())))
                            if (inserted > 0L) inserted else db.tags().findByName(name)?.id ?: 0L
                        }
                        if (oldId > 0L && targetId > 0L) tagIdRemap[oldId] = targetId
                        if (targetId > 0L) tagCount++
                    }
                }

                val tagRefsArr = root.optJSONArray("tagRefs")
                if (tagRefsArr != null) {
                    for (i in 0 until tagRefsArr.length()) {
                        val o = tagRefsArr.optJSONObject(i) ?: continue
                        val mappedTagId = tagIdRemap[o.optLong("tagId", 0L)] ?: continue
                        val mediaId = o.optLong("mediaId", 0L)
                        if (mediaId > 0L) db.tags().attach(MediaTagRef(mediaId, mappedTagId))
                    }
                }

                val exclArr = root.optJSONArray("excludedFolders")
                if (exclArr != null) {
                    for (i in 0 until exclArr.length()) {
                        val p = exclArr.optString(i).trim()
                        if (p.isNotEmpty()) db.folders().exclude(ExcludedFolder(p))
                    }
                }

                val rulesArr = root.optJSONArray("folderRules")
                if (rulesArr != null) {
                    for (i in 0 until rulesArr.length()) {
                        val o = rulesArr.optJSONObject(i) ?: continue
                        val p = o.optString("relativePath").trim()
                        if (p.isNotEmpty()) {
                            db.folderRules().upsert(
                                FolderRuleEntity(
                                    relativePath = p,
                                    hidden = o.optBoolean("hidden", false),
                                    excludedFromTimeline = o.optBoolean("excludedFromTimeline", false),
                                    excludedFromOcr = o.optBoolean("excludedFromOcr", false),
                                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                                )
                            )
                            ruleCount++
                        }
                    }
                }

                val searchesArr = root.optJSONArray("savedSearches")
                if (searchesArr != null) {
                    val existingNames = db.savedSearches().allSnapshot().map { it.name.lowercase() }.toSet()
                    for (i in 0 until searchesArr.length()) {
                        val o = searchesArr.optJSONObject(i) ?: continue
                        val name = o.optString("name").trim()
                        val queryJson = o.optString("queryJson")
                        if (name.isNotEmpty() && name.lowercase() !in existingNames) {
                            db.savedSearches().upsert(SavedSearch(name = name, queryJson = queryJson))
                            searchCount++
                        }
                    }
                }

                val recipesArr = root.optJSONArray("editRecipes")
                if (recipesArr != null) {
                    for (i in 0 until recipesArr.length()) {
                        val o = recipesArr.optJSONObject(i) ?: continue
                        val mediaId = o.optLong("mediaId", 0L)
                        val opsJson = o.optString("operationsJson")
                        if (mediaId > 0L && opsJson.isNotBlank()) {
                            db.editRecipes().upsert(
                                EditRecipe(
                                    mediaId = mediaId,
                                    sourceSize = o.optLong("sourceSize", 0L),
                                    operationsJson = opsJson,
                                    outputUri = if (o.isNull("outputUri")) null else o.optString("outputUri"),
                                    version = o.optInt("version", 1),
                                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                                )
                            )
                        }
                    }
                }
            }
            MetadataRestoreSummary(albumCount, favCount, tagCount, ruleCount, searchCount)
        }
    }

    suspend fun exportToUri(ctx: Context, db: FarDatabase, uri: Uri): FarResult<Unit> = withContext(Dispatchers.IO) {
        when (val jsonRes = exportJson(db)) {
            is FarResult.Err -> jsonRes
            is FarResult.Ok -> farCatching {
                val out = ctx.contentResolver.openOutputStream(uri)
                    ?: throw java.io.IOException("backup output stream unavailable")
                out.use { it.write(jsonRes.value.toByteArray(Charsets.UTF_8)) }
            }
        }
    }

    suspend fun importFromUri(ctx: Context, db: FarDatabase, uri: Uri): FarResult<MetadataRestoreSummary> =
        withContext(Dispatchers.IO) {
            when (val textRes = farCatching {
                val stream = ctx.contentResolver.openInputStream(uri)
                    ?: throw java.io.FileNotFoundException("backup input stream unavailable")
                stream.use { it.readBytes().toString(Charsets.UTF_8) }
            }) {
                is FarResult.Err -> textRes
                is FarResult.Ok -> importJson(db, textRes.value)
            }
        }
}
