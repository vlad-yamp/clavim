package com.dodisrael.clavim

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val DEFAULT_MAX_PAGES = 50

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelegramFosteringScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var maxPagesInput by remember { mutableStateOf(DEFAULT_MAX_PAGES.toString()) }
    var state by remember { mutableStateOf<FosteringState>(FosteringState.Idle) }
    var loadingPage by remember { mutableIntStateOf(0) }
    var loadingTotal by remember { mutableIntStateOf(DEFAULT_MAX_PAGES) }
    val scope = rememberCoroutineScope()

    val doSearch: () -> Unit = {
        if (query.isNotBlank()) {
            val maxPages = maxPagesInput.toIntOrNull()?.coerceIn(1, 500) ?: DEFAULT_MAX_PAGES
            loadingTotal = maxPages
            state = FosteringState.Loading
            loadingPage = 0
            scope.launch {
                state = fetchFosteringPhotos(query.trim(), maxPages) { page -> loadingPage = page }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Фото передержек",
            subtitle = "Из Telegram-канала DogIsrael",
            showBack = true,
            onBack = onBack
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Поисковая строка
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Имя собаки") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = doSearch,
                    enabled = query.isNotBlank() && state !is FosteringState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF039BE5))
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Найти", tint = Color.White)
                }
            }

            // Поле для настройки глубины поиска
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Глубина поиска (страниц):",
                    fontSize = 13.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxPagesInput,
                    onValueChange = { v ->
                        if (v.isEmpty() || v.all { it.isDigit() }) maxPagesInput = v
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { doSearch() }),
                    modifier = Modifier.width(80.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            when (val s = state) {
                is FosteringState.Idle -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Введите имя собаки и нажмите 🔍",
                        color = Color(0xFF9E9E9E),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }

                is FosteringState.Loading -> {
                    val progress = if (loadingTotal > 0) loadingPage.toFloat() / loadingTotal else 0f
                    val percent = (progress * 100).toInt()
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Text(
                                text = "Поиск...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF424242)
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF039BE5),
                                trackColor = Color(0xFFBBDEFB)
                            )
                            Text(
                                text = "Страница $loadingPage из $loadingTotal · $percent%",
                                color = Color(0xFF757575),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                is FosteringState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.message,
                        color = Color(0xFFB00020),
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                is FosteringState.Success -> key(s.posts) {
                    val pagerState = rememberPagerState { s.posts.size }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${s.posts.size}",
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 4.dp),
                            fontSize = 13.sp,
                            color = Color(0xFF9E9E9E)
                        )
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val post = s.posts[page]
                            val ctx = LocalContext.current
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx)
                                        .data(post.photoUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Фото передержки",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                if (post.caption.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                    ) {
                                        Text(
                                            text = post.caption,
                                            modifier = Modifier.padding(12.dp),
                                            fontSize = 13.sp,
                                            color = Color(0xFF1C1B1F),
                                            maxLines = 5,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun fetchFosteringPhotos(
    query: String,
    maxPages: Int,
    onProgress: (Int) -> Unit
): FosteringState {
    return withContext(Dispatchers.IO) {
        try {
            val allPosts = mutableListOf<FosteringPost>()
            val seen = mutableSetOf<String>()
            val photoRegex = Regex(
                """tgme_widget_message_photo(?!_user)[^"]*"[^>]*background-image:url\('([^']+)'\)"""
            )
            val textRegex = Regex("""js-message_text[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            val htmlTagRegex = Regex("<[^>]+>")
            val msgIdRegex = Regex("""data-post="[^/]*/(\d+)"""")

            var beforeId: String? = null
            var fetchedAnyPage = false

            for (page in 0 until maxPages) {
                onProgress(page + 1)
                val urlStr = "https://t.me/s/DogIsraelTsafon" +
                    (beforeId?.let { "?before=$it" } ?: "")
                val html = fetchHtml(urlStr) ?: break
                fetchedAnyPage = true

                val newBefore = msgIdRegex.findAll(html)
                    .mapNotNull { it.groupValues[1].toLongOrNull() }
                    .minOrNull()?.toString()

                html.split("js-widget_message_wrap").drop(1).forEach { block ->
                    val photoUrls = photoRegex.findAll(block).map { it.groupValues[1] }.toList()
                    if (photoUrls.isEmpty()) return@forEach

                    val rawText = textRegex.find(block)?.groupValues?.get(1) ?: ""
                    val text = rawText
                        .replace(htmlTagRegex, " ")
                        .replace("&amp;", "&").replace("&lt;", "<")
                        .replace("&gt;", ">").replace("&nbsp;", " ").replace("&#39;", "'")
                        .replace(Regex("\\s+"), " ").trim()

                    if (text.contains(query, ignoreCase = true)) {
                        photoUrls.forEach { url ->
                            if (seen.add(url)) allPosts.add(FosteringPost(url, text))
                        }
                    }
                }

                if (newBefore == null || newBefore == beforeId) break
                beforeId = newBefore
            }

            when {
                !fetchedAnyPage -> FosteringState.Error("Нет соединения с интернетом")
                allPosts.isEmpty() -> FosteringState.Error("Фото с именем «$query» не найдены")
                else -> FosteringState.Success(allPosts)
            }
        } catch (e: Exception) {
            FosteringState.Error("Ошибка: ${e.message ?: "Нет соединения"}")
        }
    }
}

private fun fetchHtml(url: String): String? {
    return try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/121.0.0.0 Mobile Safari/537.36"
        )
        if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
        conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    } catch (_: Exception) { null }
}
