package com.dodisrael.clavim

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Entity(
    tableName = "fostering_posts",
    indices = [Index(value = ["postId", "photoUrl"], unique = true)]
)
data class FosteringPostEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val postId: Long,
    val photoUrl: String,
    val caption: String,
    val date: String = ""
)

@Dao
interface FosteringDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(posts: List<FosteringPostEntity>)

    @Query("SELECT photoUrl, caption, date FROM fostering_posts WHERE caption LIKE '%' || :query || '%' ORDER BY postId DESC")
    fun search(query: String): List<FosteringPost>

    @Query("SELECT photoUrl, caption, date FROM fostering_posts ORDER BY postId DESC LIMIT :limit")
    fun getRecentPosts(limit: Int): List<FosteringPost>

    @Query("SELECT MAX(postId) FROM fostering_posts")
    fun getMaxPostId(): Long?

    @Query("SELECT COUNT(DISTINCT postId) FROM fostering_posts")
    fun countPosts(): Int

    @Query("DELETE FROM fostering_posts")
    fun clearAll()
}

@Database(entities = [FosteringPostEntity::class], version = 2, exportSchema = false)
abstract class FosteringDatabase : RoomDatabase() {
    abstract fun dao(): FosteringDao

    companion object {
        @Volatile private var INSTANCE: FosteringDatabase? = null

        fun get(context: Context): FosteringDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                FosteringDatabase::class.java,
                "fostering_db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}

private data class ParsedPost(val id: Long, val photoUrls: List<String>, val caption: String, val date: String = "")

// Sends captions to GPT and returns only posts confirmed to be about pet boarding.
// If apiKey is blank or on any error — returns the original list unchanged.
suspend fun filterFosteringPosts(
    posts: List<FosteringPost>,
    apiKey: String,
    dogName: String = ""
): List<FosteringPost> {
    if (apiKey.isBlank() || posts.isEmpty()) return posts
    return withContext(Dispatchers.IO) {
        try {
            val numbered = posts.mapIndexed { i, p ->
                "${i + 1}: ${p.caption.take(300).ifBlank { "(без текста)" }}"
            }.joinToString("\n")

            val nameCondition = if (dogName.isNotBlank()) {
                val parts = dogName.split(Regex("[+&,]|(?<![а-яёА-ЯЁa-zA-Z])и(?![а-яёА-ЯЁa-zA-Z])"))
                    .map { it.trim() }.filter { it.isNotBlank() }
                if (parts.size > 1) {
                    val named = parts.joinToString(" и ") { "«$it»" }
                    "\n- В посте упоминаются ВСЕ собаки: $named. Если упомянута только одна из них — пост не подходит. Имена могут быть написаны чуть иначе (другая транслитерация е/э, одна/двойная согласная)"
                } else {
                    "\n- В посте упоминается собака «$dogName». " +
                    "Допустимо: другая транслитерация (е/э, одна/двойная согласная), а также сокращённая форма — когда имя в посте является началом клички клиента (например «Джесси» подходит для «Джессика»). " +
                    "Недопустимо: если имя в посте ДЛИННЕЕ клички «$dogName» — это другая собака (например для клички «Бен» имя «Беня» не подходит, так как «Беня» длиннее). " +
                    "Если в посте несколько собак и «$dogName» лишь одна из них — пост не подходит"
                }
            } else ""

            val userPrompt = """Посты из Telegram-канала о собаках. Определи, какие из них ТОЧНО соответствуют ВСЕМ условиям:
- Относятся к передержке или пансиону конкретной собаки (пост о пребывании собаки на передержке/пансионе)$nameCondition

Не включай: просто фото без контекста, объявления о потере, продаже, вязке, дрессировке, посты где имя встречается случайно как часть другого слова.

Посты:
$numbered

Ответь ТОЛЬКО номерами постов через запятую. Если все подходят — напиши: все. Если ни один — напиши: нет."""

            val body = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
                })
                put("max_tokens", 100)
                put("temperature", 0.0)
            }.toString()

            val conn = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode != 200) return@withContext posts

            val answer = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()

            when {
                answer.equals("все", ignoreCase = true) -> posts
                answer.equals("нет", ignoreCase = true) -> emptyList()
                else -> {
                    val keep = answer.split(Regex("[,\\s]+"))
                        .mapNotNull { it.trim().toIntOrNull() }
                        .filter { it in 1..posts.size }
                        .map { it - 1 }
                        .toSet()
                    posts.filterIndexed { i, _ -> i in keep }
                }
            }
        } catch (_: Exception) {
            posts
        }
    }
}

// Returns null on success, error message on failure
suspend fun syncFosteringChannel(
    context: Context,
    incremental: Boolean,
    onProgress: (page: Int) -> Unit
): String? = withContext(Dispatchers.IO) {
    try {
        val dao = FosteringDatabase.get(context).dao()
        if (!incremental) dao.clearAll()
        val maxStoredId: Long? = if (incremental) dao.getMaxPostId() else null

        val photoRegex = Regex(
            """tgme_widget_message_photo(?!_user)[^"]*"[^>]*background-image:url\('([^']+)'\)"""
        )
        val textRegex = Regex("""js-message_text[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
        val htmlTagRegex = Regex("<[^>]+>")
        val idRegex = Regex("""data-post="[^/]*/(\d+)"""")
        val dateRegex = Regex("""datetime="(\d{4}-\d{2}-\d{2})""")
        val displayDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

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
                val isoDate = dateRegex.find(block)?.groupValues?.get(1) ?: ""
                val displayDate = if (isoDate.isNotEmpty()) {
                    try { LocalDate.parse(isoDate).format(displayDateFormatter) } catch (_: Exception) { isoDate }
                } else ""
                ParsedPost(id, photoUrls, text, displayDate)
            }

            if (posts.isEmpty()) break

            val minIdOnPage = posts.minOf { it.id }
            val reachedOverlap = maxStoredId != null && minIdOnPage <= maxStoredId

            val toInsert = if (reachedOverlap) posts.filter { it.id > maxStoredId!! } else posts
            dao.insertAll(toInsert.flatMap { post ->
                post.photoUrls.map { url ->
                    FosteringPostEntity(postId = post.id, photoUrl = url, caption = post.caption, date = post.date)
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
