package com.dodisrael.clavim

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Entity(
    tableName = "fostering_posts",
    indices = [Index(value = ["postId", "photoUrl"], unique = true)]
)
data class FosteringPostEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val postId: Long,
    val photoUrl: String,
    val caption: String
)

@Dao
interface FosteringDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(posts: List<FosteringPostEntity>)

    @Query("SELECT photoUrl, caption FROM fostering_posts WHERE caption LIKE '%' || :query || '%' ORDER BY postId DESC")
    fun search(query: String): List<FosteringPost>

    @Query("SELECT MAX(postId) FROM fostering_posts")
    fun getMaxPostId(): Long?

    @Query("SELECT COUNT(DISTINCT postId) FROM fostering_posts")
    fun countPosts(): Int
}

@Database(entities = [FosteringPostEntity::class], version = 1, exportSchema = false)
abstract class FosteringDatabase : RoomDatabase() {
    abstract fun dao(): FosteringDao

    companion object {
        @Volatile private var INSTANCE: FosteringDatabase? = null

        fun get(context: Context): FosteringDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                FosteringDatabase::class.java,
                "fostering_db"
            ).build().also { INSTANCE = it }
        }
    }
}

private data class ParsedPost(val id: Long, val photoUrls: List<String>, val caption: String)

// Returns null on success, error message on failure
suspend fun syncFosteringChannel(
    context: Context,
    incremental: Boolean,
    onProgress: (page: Int) -> Unit
): String? = withContext(Dispatchers.IO) {
    try {
        val dao = FosteringDatabase.get(context).dao()
        val maxStoredId: Long? = if (incremental) dao.getMaxPostId() else null

        val photoRegex = Regex(
            """tgme_widget_message_photo(?!_user)[^"]*"[^>]*background-image:url\('([^']+)'\)"""
        )
        val textRegex = Regex("""js-message_text[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
        val htmlTagRegex = Regex("<[^>]+>")
        val idRegex = Regex("""data-post="[^/]*/(\d+)"""")

        var beforeId: String? = null
        var page = 0
        var fetchedAnyPage = false

        while (true) {
            onProgress(++page)
            val urlStr = "https://t.me/s/DogIsraelTsafon" + (beforeId?.let { "?before=$it" } ?: "")
            val html = fetchTelegramHtml(urlStr)
            if (html == null) {
                if (!fetchedAnyPage) return@withContext "Нет соединения с интернетом"
                break
            }
            fetchedAnyPage = true

            val posts = html.split("js-widget_message_wrap").drop(1).mapNotNull { block ->
                val id = idRegex.find(block)?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
                val photoUrls = photoRegex.findAll(block).map { it.groupValues[1] }.toList()
                if (photoUrls.isEmpty()) return@mapNotNull null
                val rawText = textRegex.find(block)?.groupValues?.get(1) ?: ""
                val text = rawText
                    .replace(htmlTagRegex, " ")
                    .replace("&amp;", "&").replace("&lt;", "<")
                    .replace("&gt;", ">").replace("&nbsp;", " ").replace("&#39;", "'")
                    .replace(Regex("\\s+"), " ").trim()
                ParsedPost(id, photoUrls, text)
            }

            if (posts.isEmpty()) break

            val minIdOnPage = posts.minOf { it.id }
            val reachedOverlap = maxStoredId != null && minIdOnPage <= maxStoredId

            val toInsert = if (reachedOverlap) posts.filter { it.id > maxStoredId!! } else posts
            dao.insertAll(toInsert.flatMap { post ->
                post.photoUrls.map { url ->
                    FosteringPostEntity(postId = post.id, photoUrl = url, caption = post.caption)
                }
            })

            if (reachedOverlap) break

            val newBefore = minIdOnPage.toString()
            if (newBefore == beforeId) break
            beforeId = newBefore
        }

        null
    } catch (e: Exception) {
        e.message ?: "Ошибка загрузки"
    }
}

private fun fetchTelegramHtml(url: String): String? = try {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 8_000
    conn.readTimeout = 8_000
    conn.instanceFollowRedirects = true
    conn.setRequestProperty(
        "User-Agent",
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/121.0.0.0 Mobile Safari/537.36"
    )
    if (conn.responseCode != HttpURLConnection.HTTP_OK) null
    else conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
} catch (_: Exception) { null }
