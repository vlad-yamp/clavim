package com.dodisrael.clavim

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class SyncState {
    object Checking : SyncState()
    object Empty : SyncState()
    data class Syncing(val page: Int, val incremental: Boolean) : SyncState()
    data class Ready(val count: Int) : SyncState()
    data class SyncError(val message: String) : SyncState()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelegramFosteringScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var syncState by remember { mutableStateOf<SyncState>(SyncState.Checking) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FosteringPost>?>(null) }

    LaunchedEffect(Unit) {
        val count = withContext(Dispatchers.IO) { FosteringDatabase.get(context).dao().countPosts() }
        syncState = if (count == 0) SyncState.Empty else SyncState.Ready(count)
    }

    fun startSync(incremental: Boolean) {
        syncState = SyncState.Syncing(0, incremental)
        searchResults = null
        scope.launch {
            val error = syncFosteringChannel(context, incremental) { page ->
                syncState = SyncState.Syncing(page, incremental)
            }
            val count = withContext(Dispatchers.IO) { FosteringDatabase.get(context).dao().countPosts() }
            syncState = if (error != null) SyncState.SyncError(error) else SyncState.Ready(count)
        }
    }

    fun doSearch() {
        if (query.isBlank()) return
        keyboard?.hide()
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                FosteringDatabase.get(context).dao().search(query.trim())
            }
            searchResults = results
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Фото передержек",
            subtitle = "Из Telegram-канала DogIsrael",
            showBack = true,
            onBack = onBack
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            when (val s = syncState) {
                is SyncState.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF039BE5))
                }

                is SyncState.Empty -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = Color(0xFF039BE5),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Канал не загружен", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Загрузите канал один раз — дальше поиск будет мгновенным",
                        fontSize = 14.sp,
                        color = Color(0xFF757575),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { startSync(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF039BE5))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Загрузить канал")
                    }
                }

                is SyncState.Syncing -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF039BE5))
                        Text(
                            if (s.incremental) "Обновление..." else "Загрузка канала...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF424242)
                        )
                        Text("Страница ${s.page}", color = Color(0xFF757575), fontSize = 13.sp)
                    }
                }

                is SyncState.SyncError -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(s.message, color = Color(0xFFB00020), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { startSync(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF039BE5))
                    ) {
                        Text("Попробовать снова")
                    }
                }

                is SyncState.Ready -> Column(modifier = Modifier.fillMaxSize()) {
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
                                    IconButton(onClick = { query = ""; searchResults = null }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = ::doSearch,
                            enabled = query.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF039BE5))
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Найти", tint = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${s.count} постов в базе",
                            fontSize = 12.sp,
                            color = Color(0xFF9E9E9E),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { startSync(true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF039BE5)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Обновить", fontSize = 12.sp, color = Color(0xFF039BE5))
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    val results = searchResults
                    when {
                        results == null -> Box(
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

                        results.isEmpty() -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Фото с «$query» не найдены",
                                color = Color(0xFF9E9E9E),
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }

                        else -> key(results) {
                            val pagerState = rememberPagerState { results.size }
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${results.size}",
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
                                    val post = results[page]
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
    }
}
