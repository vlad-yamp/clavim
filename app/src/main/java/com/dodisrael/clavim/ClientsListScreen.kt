package com.dodisrael.clavim

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

internal const val CLIENTS_SHEET_CSV =
    "https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/export?format=csv&gid=1215152509"
private const val SHEET_ID = "1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs"
private const val CLIENTS_SHEET_GID = 1215152509

data class ClientInfo(
    val ownerName: String,
    val phone: String,
    val breed: String,
    val dogName: String,
    val lastBoarding: String = "",
    val lastBoardingMonth: String = "",
    val boardingHistory: String = ""
)

@Composable
fun ClientsListScreen(
    onBack: () -> Unit,
    onRepeatBoarding: (dogName: String, clarification: String, filter: Pair<Int, Int>?) -> Unit,
    onDeleteBoarding: (dogName: String, clarification: String, filter: Pair<Int, Int>?) -> Unit,
    initialMonthFilter: Pair<Int, Int>? = null
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("clavim_prefs", Context.MODE_PRIVATE) }
    val photoPrefs = remember { context.getSharedPreferences("clients_photo_cache", Context.MODE_PRIVATE) }
    var searchQuery by remember { mutableStateOf("") }
    var clients by remember { mutableStateOf<List<ClientInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    // null = still loading, "" = no photo, "url" = has photo
    var photoCache by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var fullScreenPhotoUrl by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var photosToLoad by remember { mutableStateOf(0) }
    var photosLoaded by remember { mutableStateOf(0) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showHistoryFor by remember { mutableStateOf<ClientInfo?>(null) }
    // Pair(month 1-12, year)
    var monthFilter by remember { mutableStateOf(initialMonthFilter) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showBoardingChart by remember { mutableStateOf(false) }
    var galleryClient by remember { mutableStateOf<ClientInfo?>(null) }
    var galleryPhotos by remember { mutableStateOf<List<String>?>(null) }
    var boardingNotes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showNoteText by remember { mutableStateOf<String?>(null) }
    var quickNoteFor by remember { mutableStateOf<Triple<ClientInfo, Int, Int>?>(null) }
    val notesPrefs = remember { context.getSharedPreferences("clavim_notes_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = prefs.getInt("clients_scroll_index", 0),
        initialFirstVisibleItemScrollOffset = prefs.getInt("clients_scroll_offset", 0)
    )
    DisposableEffect(Unit) {
        onDispose {
            prefs.edit()
                .putInt("clients_scroll_index", listState.firstVisibleItemIndex)
                .putInt("clients_scroll_offset", listState.firstVisibleItemScrollOffset)
                .apply()
        }
    }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        errorMessage = ""
        photosToLoad = 0
        photosLoaded = 0
        try {
            val googleApiKey = prefs.getString("google_api_key", "") ?: ""
            val scriptUrl = prefs.getString("addresses_script_url", "") ?: ""
            val (csv, notes, bNotes) = withContext(Dispatchers.IO) {
                val csvJob = async {
                    val conn = URL("$CLIENTS_SHEET_CSV&t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.instanceFollowRedirects = true
                    conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                }
                val notesJob = async { fetchSheetNotes(googleApiKey) }
                val boardingNotesJob = async { fetchBoardingNotes(scriptUrl) }
                Triple(csvJob.await(), notesJob.await(), boardingNotesJob.await())
            }
            boardingNotes = bNotes
            val parsed = parseClientsFromCsv(csv, notes)
            clients = parsed
            isLoading = false

            // Load cached photos — skip and remove stale empty entries
            val editor = photoPrefs.edit()
            val cached = parsed.mapNotNull { client ->
                val key = clientPhotoKey(client)
                val url = photoPrefs.getString(key, null) ?: return@mapNotNull null
                if (url.isBlank()) { editor.remove(key); return@mapNotNull null }
                key to url
            }.toMap()
            editor.apply()
            if (cached.isNotEmpty()) photoCache = cached

            val apiKey = prefs.getString("openai_api_key", "") ?: ""
            val clientsByDogName = parsed.groupBy { it.dogName.lowercase() }
            val semaphore = Semaphore(5)

            // Count uncached and set total for progress bar
            val uncached = parsed.filter { !photoCache.containsKey(clientPhotoKey(it)) }
            photosToLoad = uncached.size

            // Load uncached photos — supervisorScope ties all children to this LaunchedEffect,
            // so they are cancelled when refreshTrigger changes (prevents stale increments of photosLoaded)
            supervisorScope {
                uncached.forEach { client ->
                    val key = clientPhotoKey(client)
                    launch {
                        semaphore.withPermit {
                            val uniqueOwners = clientsByDogName[client.dogName.lowercase()]
                                ?.distinctBy { it.ownerName.lowercase() }?.size ?: 1
                            val url = findClientPhoto(context, client, uniqueOwners, apiKey)
                            if (url != null) {
                                // Persist URL immediately so it survives navigation cancellation
                                photoPrefs.edit().putString(key, url).apply()
                                // Preload image into Coil cache — if cancelled mid-download,
                                // SharedPreferences already has the URL for next session
                                val req = ImageRequest.Builder(context).data(url).build()
                                context.imageLoader.execute(req)
                                photoCache = photoCache + (key to url)
                            } else {
                                photoCache = photoCache + (key to "")
                            }
                            photosLoaded++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errorMessage = "Ошибка загрузки: ${e.message}"
            isLoading = false
        }
    }

    LaunchedEffect(galleryClient) {
        val c = galleryClient ?: return@LaunchedEffect
        galleryPhotos = null
        val apiKey = prefs.getString("openai_api_key", "") ?: ""
        val photos = findAllClientPhotos(context, c, apiKey)
        if (photos.isEmpty()) {
            galleryClient = null
            galleryPhotos = null
        } else {
            galleryPhotos = photos
        }
    }

    val filteredClients = remember(clients, searchQuery, monthFilter) {
        clients
            .let { list ->
                if (searchQuery.isBlank()) list
                else list.filter { c ->
                    c.ownerName.contains(searchQuery, ignoreCase = true) ||
                    c.dogName.contains(searchQuery, ignoreCase = true) ||
                    c.breed.contains(searchQuery, ignoreCase = true) ||
                    c.phone.contains(searchQuery)
                }
            }
            .let { list ->
                val f = monthFilter ?: return@let list
                list.filter { clientHasBoardingInMonth(it, f.first, f.second) }
            }
    }

    val dogDays = remember(filteredClients, monthFilter) {
        val f = monthFilter ?: return@remember 0
        totalDogDaysInMonth(filteredClients, f.first, f.second)
    }

    // При активном фильтре разворачиваем клиентов: одна запись на каждый интервал в месяце,
    // отсортированные по дате начала. Без фильтра — тот же порядок что в filteredClients.
    val displayItems: List<Pair<ClientInfo, String?>> = remember(filteredClients, monthFilter) {
        val f = monthFilter
        if (f == null) {
            filteredClients.map { it to (null as String?) }
        } else {
            val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
            filteredClients
                .flatMap { client ->
                    val intervals = allBoardingIntervalsInMonth(client, f.first, f.second)
                    buildList<Pair<ClientInfo, String?>> {
                        if (intervals.isEmpty()) add(client to null)
                        else intervals.forEach { add(client to it) }
                    }
                }
                .sortedBy { (_, interval) ->
                    interval?.let { s ->
                        datePattern.find(s)?.let { m ->
                            try { LocalDate.parse(m.value, fmt) } catch (_: Exception) { null }
                        }
                    }
                }
        }
    }

    val totalEarnings = remember(displayItems, monthFilter) {
        val f = monthFilter ?: return@remember 0
        displayItems.sumOf { (client, interval) ->
            interval?.let { intervalEarnings(it, f.first, f.second, client.dogName) } ?: 0
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Очистить кэш фото?") },
            text = { Text("Сохранённые фото будут удалены и загружены заново из базы данных.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    photoPrefs.edit().clear().apply()
                    photoCache = emptyMap()
                    refreshTrigger++
                }) { Text("Очистить", color = Color(0xFFD32F2F)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Отмена") }
            }
        )
    }

    showHistoryFor?.let { client ->
        BoardingHistoryDialog(client = client, onDismiss = { showHistoryFor = null })
    }

    showNoteText?.let { text ->
        NoteViewDialog(text = text, onDismiss = { showNoteText = null })
    }

    quickNoteFor?.let { (client, m, y) ->
        QuickNoteDialog(
            ownerName = client.ownerName.trim(),
            dogName = client.dogName.trim(),
            monthDisplay = noteMonthDisplay("${m}_${y}"),
            onDismiss = { quickNoteFor = null },
            onConfirm = { noteText, forAll ->
                coroutineScope.launch {
                    val noteKey = "${client.ownerName.trim()} — ${client.dogName.trim()}"
                    val monthStr = if (forAll) "" else "${m}_${y}"
                    val existing = loadNotes(notesPrefs).toMutableList()
                    existing.removeAll { e -> e.key == noteKey && e.month == monthStr }
                    existing.add(NoteEntry(noteKey, monthStr, noteText))
                    saveNotes(notesPrefs, existing)
                    val scriptUrl = prefs.getString("addresses_script_url", "") ?: ""
                    if (scriptUrl.isNotBlank()) saveNotesToServer(scriptUrl, existing)
                    val ownerNorm = client.ownerName.trim().lowercase()
                    val dogNorm = client.dogName.trim().lowercase()
                    boardingNotes = boardingNotes + mapOf("$ownerNorm $dogNorm|$monthStr" to noteText)
                    quickNoteFor = null
                }
            }
        )
    }

    if (showBoardingChart) {
        monthFilter?.let { (m, y) ->
            BoardingChartDialog(
                clients = filteredClients,
                month = m,
                year = y,
                photoCache = photoCache,
                onDismiss = { showBoardingChart = false }
            )
        }
    }

    if (showMonthPicker) {
        val now = remember { LocalDate.now() }
        MonthPickerDialog(
            initialMonth = monthFilter?.first ?: now.monthValue,
            initialYear = monthFilter?.second ?: now.year,
            onConfirm = { m, y -> monthFilter = m to y; showMonthPicker = false },
            onDismiss = { showMonthPicker = false }
        )
    }

    fullScreenPhotoUrl?.let { url ->
        FullScreenPhotoViewer(photos = listOf(url), onDismiss = { fullScreenPhotoUrl = null })
    }

    if (galleryClient != null) {
        val photos = galleryPhotos
        if (photos == null) {
            AlertDialog(
                onDismissRequest = { galleryClient = null },
                text = {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Загрузка фото ${galleryClient?.dogName}…", fontSize = 14.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { galleryClient = null }) { Text("Отмена") }
                }
            )
        } else if (photos.isNotEmpty()) {
            FullScreenPhotoViewer(
                photos = photos,
                onDismiss = { galleryClient = null; galleryPhotos = null }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "Клиенты", subtitle = "Список клиентов", showBack = true, onBack = onBack, compact = true)

        if (!isLoading) {
            val progress = if (photosToLoad == 0) 1f else photosLoaded.toFloat() / photosToLoad.toFloat()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    color = Color(0xFF7B1FA2),
                    trackColor = Color(0xFFE1BEE7)
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { showClearCacheDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Очистить кэш фото",
                        tint = Color(0xFF7B1FA2),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (monthFilter == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Поиск") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotBlank()) {
                        { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, "Очистить") } }
                    } else null,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = { showMonthPicker = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Transparent)
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = "Фильтр по месяцу",
                        tint = Color(0xFF757575),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (monthFilter != null) {
            val (m, y) = monthFilter!!
            val monthName = listOf(
                "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
            )[m - 1]
            val daysInMonth = LocalDate.of(y, m, 1).lengthOfMonth()
            val avgPerDay = if (daysInMonth > 0) dogDays.toFloat() / daysInMonth else 0f
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2E7D32).copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$monthName $y",
                            fontSize = 13.sp,
                            color = Color(0xFF7B1FA2),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Сбросить фильтр",
                            tint = Color(0xFF7B1FA2),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { monthFilter = null }
                        )
                        if (totalEarnings > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "~${String.format(java.util.Locale.US, "%,d", totalEarnings)} ₪",
                                fontSize = 12.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Text(
                        "${filteredClients.size} собак  ·  $dogDays соб-дн  ·  ср. ${"%.1f".format(avgPerDay)}/дн",
                        fontSize = 11.sp,
                        color = Color(0xFF7B1FA2).copy(alpha = 0.75f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { showMonthPicker = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF7B1FA2).copy(alpha = 0.18f))
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = "Сменить месяц",
                        tint = Color(0xFF7B1FA2),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = { showBoardingChart = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF00897B).copy(alpha = 0.18f))
                ) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = "График",
                        tint = Color(0xFF00897B),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            errorMessage.isNotBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMessage, color = Color(0xFFD32F2F), modifier = Modifier.padding(16.dp))
            }
            displayItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        searchQuery.isNotBlank() || monthFilter != null -> "Ничего не найдено"
                        else -> "Список пуст"
                    },
                    color = Color.Gray, fontSize = 16.sp
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayItems) { (client, interval) ->
                    val photoUrl = photoCache[clientPhotoKey(client)]
                    val earnings = monthFilter?.let { (m, y) ->
                        interval?.let { intervalEarnings(it, m, y, client.dogName) }
                    }
                    val mf = monthFilter
                    val noteText = if (mf != null) {
                        val ownerNorm = client.ownerName.trim().lowercase()
                        val dogNorm   = client.dogName.trim().lowercase()
                        val clientKey = "$ownerNorm $dogNorm"
                        boardingNotes["$clientKey|${mf.first}_${mf.second}"]
                            ?: boardingNotes["$clientKey|"]
                    } else null
                    val showAddNote = mf != null && noteText == null
                    ClientCard(
                        client = client,
                        photoUrl = photoUrl,
                        boardingInterval = interval,
                        earnings = earnings,
                        onCall = { callPhone(context, client.phone) },
                        onWhatsApp = { openWhatsApp(context, client.phone) },
                        onRepeatBoarding = {
                            onRepeatBoarding(client.dogName, clientClarification(client), monthFilter)
                        },
                        onDeleteBoarding = {
                            onDeleteBoarding(client.dogName, clientClarification(client), monthFilter)
                        },
                        onPhotoClick = { url -> fullScreenPhotoUrl = url },
                        onHistoryClick = { showHistoryFor = client },
                        onGalleryClick = { galleryClient = client },
                        noteText = noteText,
                        onNoteClick = { showNoteText = noteText },
                        showAddNote = showAddNote,
                        onAddNoteClick = { if (mf != null) quickNoteFor = Triple(client, mf.first, mf.second) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun ClientCard(
    client: ClientInfo,
    photoUrl: String?,
    boardingInterval: String? = null,
    earnings: Int? = null,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onRepeatBoarding: () -> Unit,
    onDeleteBoarding: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onHistoryClick: () -> Unit,
    onGalleryClick: () -> Unit,
    noteText: String? = null,
    onNoteClick: () -> Unit = {},
    showAddNote: Boolean = false,
    onAddNoteClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier
                .weight(1f)
                .clickable(onClick = onHistoryClick)
            ) {
                Text(
                    text = client.ownerName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1A237E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (client.dogName.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Pets,
                            contentDescription = null,
                            tint = Color(0xFF6D4C41),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = client.dogName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF3E2723)
                        )
                    }
                }

                if (client.breed.isNotBlank()) {
                    Text(
                        text = if (earnings != null)
                            "${client.breed}  (~${String.format(java.util.Locale.US, "%,d", earnings)} ₪)"
                        else client.breed,
                        fontSize = 12.sp,
                        color = Color(0xFF757575),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (boardingInterval != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = boardingInterval,
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (client.phone.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = client.phone,
                        fontSize = 13.sp,
                        color = Color(0xFF1565C0)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ClientActionButton(
                        icon = Icons.Default.Call,
                        description = "Позвонить",
                        color = Color(0xFF2E7D32),
                        onClick = onCall
                    )
                    ClientActionButton(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        description = "WhatsApp",
                        color = Color(0xFF25D366),
                        onClick = onWhatsApp
                    )
                    ClientActionButton(
                        icon = Icons.Default.Refresh,
                        description = "Повторная передержка",
                        color = Color(0xFF1565C0),
                        onClick = onRepeatBoarding
                    )
                    ClientActionButton(
                        icon = Icons.Default.Delete,
                        description = "Удалить из передержки",
                        color = Color(0xFFC62828),
                        onClick = onDeleteBoarding
                    )
                    ClientActionButton(
                        icon = Icons.Default.CameraAlt,
                        description = "Фото из Telegram",
                        color = Color(0xFF0288D1),
                        onClick = onGalleryClick
                    )
                }
            }

            val hasNoteIcon = noteText != null || showAddNote
            Spacer(Modifier.width(if (hasNoteIcon) 4.dp else 10.dp))

            if (hasNoteIcon) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(Modifier.height(6.dp))
                    if (noteText != null) {
                        IconButton(onClick = onNoteClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.PriorityHigh,
                                contentDescription = "Заметка",
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = onAddNoteClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Добавить заметку",
                                tint = Color(0xFFBDBDBD),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
            }

            // Dog photo on the right — fills full card height, fixed width
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE8F5E9))
                    .then(
                        if (photoUrl?.isNotBlank() == true)
                            Modifier.clickable { onPhotoClick(photoUrl) }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    photoUrl == null -> CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF7B1FA2)
                    )
                    photoUrl.isBlank() -> Icon(
                        Icons.Default.Pets,
                        contentDescription = null,
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(40.dp)
                    )
                    else -> AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Фото ${client.dogName}",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientActionButton(
    icon: ImageVector,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.13f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(17.dp))
    }
}

private fun isValidIsraeliPhone(phone: String): Boolean {
    val digits = phone.filter { it.isDigit() }
    return digits.length in 9..10 && digits.startsWith("0")
}

private fun normalizeMonth(raw: String): String {
    if (raw.contains('_')) {
        val p = raw.split('_')
        val m = p.getOrNull(0)?.toIntOrNull(); val y = p.getOrNull(1)?.toIntOrNull()
        if (m != null && y != null) return "${m}_${y}"
    }
    if (raw.contains('/') && raw.length <= 7) {
        val p = raw.split('/')
        val m = p.getOrNull(0)?.toIntOrNull(); val y = p.getOrNull(1)?.toIntOrNull()
        if (m != null && y != null) return "${m}_${y}"
    }
    val monthMap = mapOf("Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4,
        "May" to 5, "Jun" to 6, "Jul" to 7, "Aug" to 8,
        "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12)
    val tokens = raw.split(" ")
    val monthNum = monthMap[tokens.getOrNull(1)]
    val year = tokens.getOrNull(3)?.toIntOrNull()
    if (monthNum != null && year != null) return "${monthNum}_${year}"
    return raw
}

// notes: key = "ownernorm dogname|M_YYYY"
private suspend fun fetchBoardingNotes(scriptUrl: String): Map<String, String> {
    if (scriptUrl.isBlank()) return emptyMap()
    return try {
        withContext(Dispatchers.IO) {
            val conn = URL("$scriptUrl?sheet=Заметки").openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            if (conn.responseCode != 200) return@withContext emptyMap()
            val json = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            if (!json.optBoolean("ok", false)) return@withContext emptyMap()
            val arr = json.getJSONArray("rows")
            buildMap {
                val dashRegex = Regex("\\s*\\p{Pd}\\s*")
                for (i in 0 until arr.length()) {
                    val obj      = arr.getJSONObject(i)
                    val rawKey   = obj.optString("key").trim()
                    val rawMonth = obj.optString("month").trim()
                    val value    = obj.optString("value").trim()
                    if (rawKey.isBlank() || value.isBlank()) continue
                    val parts     = rawKey.split(dashRegex, limit = 2)
                    val ownerNorm = parts.getOrNull(0)?.trim()?.lowercase()
                    val dogNorm   = parts.getOrNull(1)?.trim()?.lowercase()
                    val month     = normalizeMonth(rawMonth)
                    if (!ownerNorm.isNullOrBlank() && !dogNorm.isNullOrBlank()) {
                        put("$ownerNorm $dogNorm|$month", value)
                    }
                }
            }
        }
    } catch (_: Exception) { emptyMap() }
}

@Composable
private fun QuickNoteDialog(
    ownerName: String,
    dogName: String,
    monthDisplay: String,
    onDismiss: () -> Unit,
    onConfirm: (text: String, forAll: Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var forAll by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("$ownerName — $dogName", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        },
        text = {
            Column {
                if (!forAll) {
                    Text(monthDisplay, fontSize = 13.sp, color = Color(0xFF757575))
                    Spacer(Modifier.height(6.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { forAll = !forAll }
                ) {
                    Checkbox(
                        checked = forAll,
                        onCheckedChange = { forAll = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF388E3C))
                    )
                    Text("Для всех передержек", fontSize = 14.sp)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Текст заметки…") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim(), forAll) }, enabled = text.isNotBlank()) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun NoteViewDialog(text: String, onDismiss: () -> Unit) {
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заметка", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(scroll)
            ) {
                Text(text, fontSize = 15.sp, color = Color(0xFF1C1B1F))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

internal fun parseClientsFromCsv(csv: String, notes: Map<String, String> = emptyMap()): List<ClientInfo> {
    val all = csv.lines().drop(1).mapIndexedNotNull { _, line ->
        if (line.isBlank()) return@mapIndexedNotNull null
        val cols = splitCsvLine(line)
        val ownerName = cols.getOrNull(0)?.trim().takeIf { !it.isNullOrBlank() } ?: return@mapIndexedNotNull null
        val dogName = cols.getOrNull(3)?.trim().takeIf { !it.isNullOrBlank() } ?: return@mapIndexedNotNull null
        ClientInfo(
            ownerName = ownerName,
            phone = cols.getOrNull(1)?.trim() ?: "",
            breed = cols.getOrNull(2)?.trim() ?: "",
            dogName = dogName,
            lastBoarding = cols.getOrNull(7)?.trim() ?: "",
            lastBoardingMonth = cols.getOrNull(4)?.trim() ?: "",
            boardingHistory = notes["${ownerName.lowercase()}|${dogName.lowercase()}|${cols.getOrNull(1)?.trim() ?: ""}"]
                ?: notes["${ownerName.lowercase()}|${dogName.lowercase()}"]
                ?: ""
        )
    }
    // Per group (ownerName + dogName + breed): if phones differ and at least one is a valid
    // Israeli number, drop rows with invalid numbers. If all are invalid, keep everyone.
    // Propagate boarding history from any row in the group that has it.
    return all.groupBy { Triple(it.ownerName.lowercase(), it.dogName.lowercase(), it.breed.lowercase()) }
        .flatMap { (_, group) ->
            val history = group.firstOrNull { it.boardingHistory.isNotBlank() }?.boardingHistory ?: ""
            val valid = group.filter { isValidIsraeliPhone(it.phone) }
            val base = if (valid.isNotEmpty()) valid else group
            if (history.isBlank()) base
            else base.map { if (it.boardingHistory.isBlank()) it.copy(boardingHistory = history) else it }
        }
}

// Returns map keyed by "ownerName_lower|dogName_lower" → note text.
// Matching by content (not row index) avoids mismatches caused by hidden/filtered rows.
internal suspend fun fetchSheetNotes(apiKey: String): Map<String, String> = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) return@withContext emptyMap()
    try {
        // Step 1: resolve sheet name from GID (ranges param requires sheet title, not GID)
        val ts = System.currentTimeMillis()
        val metaConn = URL(
            "https://sheets.googleapis.com/v4/spreadsheets/$SHEET_ID?fields=sheets%2Fproperties&key=$apiKey&t=$ts"
        ).openConnection() as HttpURLConnection
        metaConn.connectTimeout = 10_000
        metaConn.readTimeout = 10_000
        val sheetsArr = JSONObject(metaConn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            .getJSONArray("sheets")
        var sheetTitle = ""
        for (i in 0 until sheetsArr.length()) {
            val props = sheetsArr.getJSONObject(i).getJSONObject("properties")
            if (props.getInt("sheetId") == CLIENTS_SHEET_GID) {
                sheetTitle = props.getString("title"); break
            }
        }
        if (sheetTitle.isBlank()) return@withContext emptyMap()

        // Step 2: fetch columns A–H (owner col A, dog col D, note on col H)
        val range = URLEncoder.encode("$sheetTitle!A:H", "UTF-8")
        val dataConn = URL(
            "https://sheets.googleapis.com/v4/spreadsheets/$SHEET_ID" +
            "?includeGridData=true&ranges=$range" +
            "&fields=sheets%2Fdata%2FrowData%2Fvalues(formattedValue,note)&key=$apiKey&t=$ts"
        ).openConnection() as HttpURLConnection
        dataConn.connectTimeout = 15_000
        dataConn.readTimeout = 15_000
        val rowData = JSONObject(dataConn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            .getJSONArray("sheets").getJSONObject(0)
            .getJSONArray("data").getJSONObject(0)
            .optJSONArray("rowData") ?: return@withContext emptyMap()

        // Keys: "owner|dog|phone" (specific) and "owner|dog" (fallback).
        // Phone disambiguates duplicate rows with same owner+dog but different phones.
        buildMap {
            for (i in 1 until rowData.length()) {
                val cells = rowData.getJSONObject(i).optJSONArray("values") ?: continue
                val owner = cells.optJSONObject(0)?.optString("formattedValue", "")?.trim() ?: ""
                val phone = cells.optJSONObject(1)?.optString("formattedValue", "")?.trim() ?: ""
                val dog   = cells.optJSONObject(3)?.optString("formattedValue", "")?.trim() ?: ""
                val note  = cells.optJSONObject(7)?.optString("note", "").orEmpty()
                if (owner.isNotBlank() && dog.isNotBlank() && note.isNotBlank()) {
                    if (phone.isNotBlank())
                        putIfAbsent("${owner.lowercase()}|${dog.lowercase()}|$phone", note)
                    putIfAbsent("${owner.lowercase()}|${dog.lowercase()}", note)
                }
            }
        }
    } catch (_: Exception) { emptyMap() }
}

@Composable
private fun BoardingHistoryDialog(client: ClientInfo, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    val fmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    // Split by any line ending, drop blank lines, classify by first date in each line
    val lines = remember(client.boardingHistory) {
        client.boardingHistory.lines().map { it.trim() }.filter { it.isNotBlank() }
    }
    val past   = remember(lines) { lines.filter { line ->
        val d = try { LocalDate.parse(line.substringBefore(" "), fmt) } catch (_: Exception) { null }
        d != null && d.isBefore(today)
    }}
    val future = remember(lines) { lines.filter { line ->
        val d = try { LocalDate.parse(line.substringBefore(" "), fmt) } catch (_: Exception) { null }
        d == null || !d.isBefore(today)
    }}

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("История — ${client.dogName}") },
        text = {
            if (lines.isEmpty()) {
                Text("История не найдена", color = Color.Gray, fontSize = 14.sp)
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (future.isNotEmpty()) {
                        item {
                            Text(
                                "Предстоящие",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1565C0),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        items(future) { line ->
                            Text(text = line, fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF1A237E))
                        }
                    }
                    if (past.isNotEmpty()) {
                        item {
                            if (future.isNotEmpty()) {
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    color = Color(0xFFE0E0E0)
                                )
                            }
                            Text(
                                "Прошедшие",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF9E9E9E),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        items(past) { line ->
                            Text(text = line, fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFF9E9E9E))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

private fun splitCsvLine(line: String): List<String> {
    val cols = mutableListOf<String>()
    var inQuotes = false
    val sb = StringBuilder()
    for (ch in line) {
        when {
            ch == '"' -> inQuotes = !inQuotes
            ch == ',' && !inQuotes -> { cols.add(sb.toString()); sb.clear() }
            else -> sb.append(ch)
        }
    }
    cols.add(sb.toString())
    return cols
}

internal fun clientPhotoKey(client: ClientInfo) = "${client.dogName}|${client.ownerName}|${client.breed}"

private fun clientClarification(client: ClientInfo): String = when {
    client.breed.isNotBlank() && client.ownerName.isNotBlank() -> "${client.breed}, Хозяин: ${client.ownerName}"
    client.ownerName.isNotBlank() -> "Хозяин: ${client.ownerName}"
    client.breed.isNotBlank() -> client.breed
    else -> ""
}

private suspend fun findClientPhoto(
    context: Context,
    client: ClientInfo,
    uniqueOwnerCount: Int,
    apiKey: String
): String? = withContext(Dispatchers.IO) {
    try {
        val raw = FosteringDatabase.get(context).dao().search(client.dogName)
        if (raw.isEmpty()) return@withContext null
        val posts = filterFosteringPosts(raw, apiKey, client.dogName)
        if (posts.isEmpty()) return@withContext null
        if (uniqueOwnerCount <= 1) return@withContext posts.firstOrNull()?.photoUrl
        if (client.lastBoarding.isBlank()) return@withContext posts.firstOrNull()?.photoUrl
        val range = parsePastBoardingRange(client.lastBoarding, client.lastBoardingMonth)
            ?: return@withContext posts.firstOrNull()?.photoUrl
        val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val startDate = try { LocalDate.parse(range.first, fmt) } catch (_: Exception) {
            return@withContext posts.firstOrNull()?.photoUrl
        }
        val endDate = try { LocalDate.parse(range.second, fmt) } catch (_: Exception) {
            return@withContext posts.firstOrNull()?.photoUrl
        }
        posts.firstOrNull { post ->
            if (post.date.isBlank()) return@firstOrNull false
            val d = try { LocalDate.parse(post.date, fmt) } catch (_: Exception) { return@firstOrNull false }
            !d.isBefore(startDate) && !d.isAfter(endDate)
        }?.photoUrl ?: posts.firstOrNull()?.photoUrl
    } catch (_: Exception) { null }
}

private suspend fun findAllClientPhotos(
    context: Context,
    client: ClientInfo,
    apiKey: String
): List<String> = withContext(Dispatchers.IO) {
    try {
        val raw = FosteringDatabase.get(context).dao().search(client.dogName)
        if (raw.isEmpty()) return@withContext emptyList()
        val posts = filterFosteringPosts(raw, apiKey, client.dogName)
        posts.mapNotNull { it.photoUrl.takeIf { u -> u.isNotBlank() } }
    } catch (_: Exception) { emptyList() }
}

private fun parsePastBoardingRange(s: String, yearHint: String = ""): Pair<String, String>? {
    if (s.isBlank()) return null
    val cleaned = s.substringBefore("(").trim()
    val parts = Regex("[–—\\-]").split(cleaned).map { it.trim() }.filter { it.isNotBlank() }
    if (parts.size < 2) return null
    fun parseMonthDay(part: String): Pair<Int, Int>? {
        val dm = part.split(".").map { it.trim() }
        if (dm.size < 2) return null
        return (dm[0].toIntOrNull() ?: return null) to (dm[1].toIntOrNull() ?: return null)
    }
    val (startDay, startMonth) = parseMonthDay(parts[0]) ?: return null
    val (endDay, endMonth) = parseMonthDay(parts[1]) ?: return null
    val hintParts = yearHint.split(".")
    val hintYear = if (hintParts.size == 2) hintParts[1].toIntOrNull() else null
    val endYear: Int
    val startYear: Int
    if (hintYear != null) {
        endYear = hintYear
        startYear = if (startMonth <= endMonth) endYear else endYear - 1
    } else {
        val cal = Calendar.getInstance()
        val curMonth = cal.get(Calendar.MONTH) + 1
        val curYear = cal.get(Calendar.YEAR)
        startYear = if (startMonth > curMonth) curYear - 1 else curYear
        endYear = if (endMonth < startMonth) startYear + 1 else startYear
    }
    return "%02d.%02d.%04d".format(startDay, startMonth, startYear) to
           "%02d.%02d.%04d".format(endDay, endMonth, endYear)
}

private fun callPhone(context: Context, phone: String) {
    val digits = phone.filter { it.isDigit() }
    if (digits.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
    } catch (_: ActivityNotFoundException) {}
}

@Composable
private fun MonthPickerDialog(
    initialMonth: Int,
    initialYear: Int,
    onConfirm: (month: Int, year: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMonth by remember { mutableStateOf(initialMonth) }
    var selectedYear by remember { mutableStateOf(initialYear) }
    val monthNames = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн",
                            "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фильтр по месяцу") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = { selectedYear-- }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущий год", tint = Color(0xFF7B1FA2))
                    }
                    Text(
                        "$selectedYear",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(72.dp)
                    )
                    IconButton(onClick = { selectedYear++ }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующий год", tint = Color(0xFF7B1FA2))
                    }
                }
                Spacer(Modifier.height(8.dp))
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (col in 0 until 3) {
                            val monthNum = row * 3 + col + 1
                            val isSelected = monthNum == selectedMonth
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) Color(0xFF7B1FA2) else Color(0xFFF3E5F5)
                                    )
                                    .clickable { selectedMonth = monthNum }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    monthNames[monthNum - 1],
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color.White else Color(0xFF7B1FA2),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    if (row < 3) Spacer(Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedMonth, selectedYear) }) { Text("Применить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

private fun dogsPerDay(clients: List<ClientInfo>, month: Int, year: Int): List<Int> {
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val monthStart = LocalDate.of(year, month, 1)
    val daysInMonth = monthStart.lengthOfMonth()
    val monthEnd = monthStart.withDayOfMonth(daysInMonth)
    val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
    val counts = IntArray(daysInMonth)
    for (client in clients) {
        if (client.boardingHistory.isBlank()) continue
        for (line in client.boardingHistory.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            val dates = datePattern.findAll(trimmed).mapNotNull { m ->
                try { LocalDate.parse(m.value, fmt) } catch (_: Exception) { null }
            }.toList()
            when {
                dates.isEmpty() -> Unit
                dates.size == 1 -> {
                    val d = dates[0]
                    if (d.monthValue == month && d.year == year) counts[d.dayOfMonth - 1]++
                }
                else -> {
                    val start = dates.first()
                    val end = dates.last()
                    if (!end.isBefore(monthStart) && !start.isAfter(monthEnd)) {
                        val clippedStart = if (start.isBefore(monthStart)) monthStart else start
                        val clippedEnd = if (end.isAfter(monthEnd)) monthEnd else end
                        var d = clippedStart
                        while (!d.isAfter(clippedEnd)) {
                            counts[d.dayOfMonth - 1]++
                            d = d.plusDays(1)
                        }
                    }
                }
            }
        }
    }
    return counts.toList()
}

private fun dogWord(n: Int): String = when {
    n % 100 in 11..19 -> "собак"
    n % 10 == 1        -> "собака"
    n % 10 in 2..4     -> "собаки"
    else               -> "собак"
}

internal val DOG_COLORS = listOf(
    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFE65100), Color(0xFF6A1B9A),
    Color(0xFF00838F), Color(0xFFC62828), Color(0xFF4527A0), Color(0xFF558B2F)
)

internal data class DogInterval(val client: ClientInfo, val actualStart: LocalDate, val actualEnd: LocalDate) {
    val dogName get() = client.dogName
}

internal fun dogIntervalsInMonth(clients: List<ClientInfo>, month: Int, year: Int): List<DogInterval> {
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val monthStart = LocalDate.of(year, month, 1)
    val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
    val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
    return buildList {
        for (client in clients) {
            if (client.boardingHistory.isBlank()) continue
            for (line in client.boardingHistory.lines()) {
                val trimmed = line.trim()
                if (trimmed.isBlank()) continue
                val dates = datePattern.findAll(trimmed).mapNotNull { m ->
                    try { LocalDate.parse(m.value, fmt) } catch (_: Exception) { null }
                }.toList()
                if (dates.size < 2) continue
                val start = dates.first(); val end = dates.last()
                if (end.isBefore(monthStart) || start.isAfter(monthEnd)) continue
                add(DogInterval(client, start, end))
            }
        }
    }.sortedWith(compareBy({ it.actualStart }, { it.actualEnd }))
}

private fun dogsPerDayRange(clients: List<ClientInfo>, rangeStart: LocalDate, rangeEnd: LocalDate): List<Int> {
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
    val totalDays = (rangeEnd.toEpochDay() - rangeStart.toEpochDay() + 1).toInt()
    val counts = IntArray(totalDays)
    for (client in clients) {
        if (client.boardingHistory.isBlank()) continue
        for (line in client.boardingHistory.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            val dates = datePattern.findAll(trimmed).mapNotNull { m ->
                try { LocalDate.parse(m.value, fmt) } catch (_: Exception) { null }
            }.toList()
            when {
                dates.isEmpty() -> Unit
                dates.size == 1 -> {
                    val d = dates[0]
                    if (!d.isBefore(rangeStart) && !d.isAfter(rangeEnd))
                        counts[(d.toEpochDay() - rangeStart.toEpochDay()).toInt()]++
                }
                else -> {
                    val s = dates.first(); val e = dates.last()
                    if (!e.isBefore(rangeStart) && !s.isAfter(rangeEnd)) {
                        val cs = if (s.isBefore(rangeStart)) rangeStart else s
                        val ce = if (e.isAfter(rangeEnd)) rangeEnd else e
                        var d = cs
                        while (!d.isAfter(ce)) {
                            counts[(d.toEpochDay() - rangeStart.toEpochDay()).toInt()]++
                            d = d.plusDays(1)
                        }
                    }
                }
            }
        }
    }
    return counts.toList()
}

@Composable
internal fun DogFaceLabel(number: Int, color: Color) {
    val density = LocalDensity.current
    Canvas(modifier = Modifier.size(width = 18.dp, height = 22.dp)) {
        val w = size.width
        val h = size.height
        val corner = with(density) { 3.dp.toPx() }

        // Droopy bulldog ears — rounded rectangles hanging from upper sides
        val earW = w * 0.27f
        val earH = h * 0.50f
        val earTop = h * 0.06f
        drawRoundRect(
            color = color.copy(alpha = 0.80f),
            topLeft = Offset(0f, earTop),
            size = Size(earW, earH),
            cornerRadius = CornerRadius(corner)
        )
        drawRoundRect(
            color = color.copy(alpha = 0.80f),
            topLeft = Offset(w - earW, earTop),
            size = Size(earW, earH),
            cornerRadius = CornerRadius(corner)
        )

        // Round head overlapping the ears
        val headCX = w / 2f
        val headCY = h * 0.64f
        val headR  = w * 0.40f
        drawCircle(color = color, radius = headR, center = Offset(headCX, headCY))

        // Number centred in head
        val textSizePx = with(density) { 7.sp.toPx() }
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize    = textSizePx
                textAlign   = android.graphics.Paint.Align.CENTER
                this.color  = Color.White.toArgb()
                typeface    = android.graphics.Typeface.DEFAULT_BOLD
            }
            canvas.nativeCanvas.drawText("$number", headCX, headCY + textSizePx * 0.38f, paint)
        }
    }
}

@Composable
private fun BoardingChartDialog(
    clients: List<ClientInfo>,
    month: Int,
    year: Int,
    photoCache: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit
) {
    val monthStart = remember(month, year) { LocalDate.of(year, month, 1) }
    val monthEnd   = remember(monthStart) { monthStart.withDayOfMonth(monthStart.lengthOfMonth()) }
    val intervals  = remember(clients, month, year) { dogIntervalsInMonth(clients, month, year) }

    val chartStart = remember(intervals, monthStart) {
        intervals.minOfOrNull { it.actualStart }?.takeIf { it.isBefore(monthStart) } ?: monthStart
    }
    val chartEnd = remember(intervals, monthEnd) {
        intervals.maxOfOrNull { it.actualEnd }?.takeIf { it.isAfter(monthEnd) } ?: monthEnd
    }
    val totalDays = remember(chartStart, chartEnd) {
        (chartEnd.toEpochDay() - chartStart.toEpochDay() + 1).toInt()
    }
    val today = remember { LocalDate.now() }
    val dogCountPerDay = remember(intervals, chartStart, totalDays) {
        IntArray(totalDays) { offset ->
            val date = chartStart.plusDays(offset.toLong())
            intervals.count { !date.isBefore(it.actualStart) && !date.isAfter(it.actualEnd) }
        }
    }

    val monthNames = listOf("Январь","Февраль","Март","Апрель","Май","Июнь",
                            "Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь")
    val monthAbbr  = listOf("Янв","Фев","Мар","Апр","Май","Июн","Июл","Авг","Сен","Окт","Ноя","Дек")
    val dowLabels  = listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")
    val dogColors = DOG_COLORS

    val rowHeightDp = 20.dp
    val chartHeight = rowHeightDp * totalDays
    val density     = LocalDensity.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            var popupIdx by remember { mutableStateOf<Int?>(null) }
            val dateFmt = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale("ru")) }

            popupIdx?.let { idx ->
                val iv     = intervals[idx]
                val client = iv.client
                val color  = dogColors[idx % dogColors.size]
                val photoUrl = photoCache[clientPhotoKey(client)]
                Dialog(onDismissRequest = { popupIdx = null }) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(8.dp),
                        border = BorderStroke(1.5.dp, color.copy(alpha = 0.55f)),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(color)
                            )
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .height(IntrinsicSize.Max),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(client.dogName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    if (client.ownerName.isNotBlank())
                                        Text(client.ownerName, fontSize = 13.sp, color = Color(0xFF424242))
                                    if (client.breed.isNotBlank())
                                        Text(client.breed, fontSize = 12.sp, color = Color(0xFF757575))
                                    if (client.phone.isNotBlank())
                                        Text(client.phone, fontSize = 13.sp, color = Color(0xFF1565C0))
                                    Text(
                                        "${iv.actualStart.format(dateFmt)} — ${iv.actualEnd.format(dateFmt)}",
                                        fontSize = 13.sp,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (photoUrl?.isNotBlank() == true) {
                                    Spacer(Modifier.width(10.dp))
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(photoUrl).crossfade(true).build(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .width(107.dp)
                                            .border(1.5.dp, Color(0xFF0D2B5E)),
                                        contentScale = ContentScale.FillWidth
                                    )
                                }
                            }
                        }
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val horizPad      = 24.dp
            val dateColWidth  = 44.dp
            val countColWidth = 22.dp
            val gap           = 4.dp
            val stripAreaWidth = maxWidth - horizPad - dateColWidth - countColWidth - gap
            val stripWidthDp = if (intervals.isEmpty()) 18.dp
                else (stripAreaWidth / intervals.size).coerceAtLeast(14.dp)
            val stripPx = with(density) { stripWidthDp.toPx() }
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {

                // Заголовок по центру
                val n = intervals.size
                Text(
                    text = "${monthNames[month - 1]} $year — $n ${dogWord(n)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
                )

                Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Row(modifier = Modifier.fillMaxWidth().height(chartHeight)) {

                    // Подписи дней — фиксированная ширина, не скроллируется
                    Column(modifier = Modifier.width(44.dp)) {
                        for (offset in 0 until totalDays) {
                            val date      = chartStart.plusDays(offset.toLong())
                            val inMonth   = date.monthValue == month && date.year == year
                            val isWeekend = date.dayOfWeek.value >= 6
                            val isToday   = date == today
                            val label = if (inMonth)
                                "%2d %s".format(date.dayOfMonth, dowLabels[date.dayOfWeek.value - 1])
                            else
                                "%2d %s".format(date.dayOfMonth, monthAbbr[date.monthValue - 1])
                            val overloaded = dogCountPerDay[offset] > 3
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeightDp)
                                    .background(
                                        when {
                                            isToday                -> Color(0xFF4CAF50).copy(alpha = 0.40f)
                                            overloaded && inMonth  -> Color(0xFFD32F2F).copy(alpha = 0.10f)
                                            overloaded && !inMonth -> Color(0xFFD32F2F).copy(alpha = 0.07f)
                                            !inMonth               -> Color(0xFFF5F5F5)
                                            else                   -> Color.Transparent
                                        }
                                    ),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    color = when {
                                        isToday   -> Color(0xFF1B5E20)
                                        !inMonth  -> Color(0xFFBDBDBD)
                                        isWeekend -> Color(0xFFE53935)
                                        else      -> Color(0xFF424242)
                                    },
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.width(countColWidth).fillMaxHeight()) {
                        for (offset in 0 until totalDays) {
                            val date    = chartStart.plusDays(offset.toLong())
                            val inMonth = date.monthValue == month && date.year == year
                            val count   = dogCountPerDay[offset]
                            val countBg = when {
                                !inMonth   -> Color.Transparent
                                count == 0 -> Color(0xFF4CAF50).copy(alpha = 0.25f)
                                count == 1 -> Color(0xFFFFEB3B).copy(alpha = 0.50f)
                                count == 2 -> Color(0xFF29B6F6).copy(alpha = 0.35f)
                                count == 3 -> Color(0xFFF48FB1).copy(alpha = 0.50f)
                                else       -> Color(0xFFEF5350).copy(alpha = 0.40f)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeightDp)
                                    .background(countBg),
                                contentAlignment = Alignment.Center
                            ) {
                                if (inMonth) Text(
                                    text = count.toString(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    if (intervals.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .width(stripWidthDp * intervals.size)
                                    .fillMaxHeight()
                                    .pointerInput(intervals, stripPx) {
                                        detectTapGestures { offset ->
                                            val idx = (offset.x / stripPx).toInt()
                                                .coerceIn(0, intervals.lastIndex)
                                            popupIdx = idx
                                        }
                                    }
                            ) {
                                val rowPx   = with(density) { rowHeightDp.toPx() }
                                val mStartOff = (monthStart.toEpochDay() - chartStart.toEpochDay()).toInt()
                                val mEndOff   = (monthEnd.toEpochDay()   - chartStart.toEpochDay()).toInt()

                                // Сетка: фон строк + горизонтальные линии
                                for (offset in 0 until totalDays) {
                                    val y    = offset * rowPx
                                    val date = chartStart.plusDays(offset.toLong())
                                    val inMonth = date.monthValue == month && date.year == year
                                    if (!inMonth) drawRect(
                                        color = Color(0xFFF5F5F5),
                                        topLeft = Offset(0f, y),
                                        size = Size(size.width, rowPx)
                                    )
                                    if (offset > 0) drawLine(
                                        color = Color(0xFFBDBDBD),
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 0.7f
                                    )
                                }

                                // Перегрузка (> 3 собаки) — красноватый фон
                                for (offset in 0 until totalDays) {
                                    if (dogCountPerDay[offset] > 3) {
                                        val y = offset * rowPx
                                        val date = chartStart.plusDays(offset.toLong())
                                        val inMonth = date.monthValue == month && date.year == year
                                        drawRect(
                                            color = Color(0xFFD32F2F).copy(alpha = if (inMonth) 0.13f else 0.08f),
                                            topLeft = Offset(0f, y),
                                            size = Size(size.width, rowPx)
                                        )
                                    }
                                }

                                // Текущий день — зелёная подсветка
                                val todayOffset = (today.toEpochDay() - chartStart.toEpochDay()).toInt()
                                if (todayOffset in 0 until totalDays) {
                                    drawRect(
                                        color = Color(0xFF4CAF50).copy(alpha = 0.22f),
                                        topLeft = Offset(0f, todayOffset * rowPx),
                                        size = Size(size.width, rowPx)
                                    )
                                }

                                // Граница месяца — более заметная линия
                                if (mStartOff > 0)
                                    drawLine(Color(0xFFBDBDBD), Offset(0f, mStartOff * rowPx), Offset(size.width, mStartOff * rowPx), 1f)
                                if (mEndOff < totalDays - 1)
                                    drawLine(Color(0xFFBDBDBD), Offset(0f, (mEndOff + 1) * rowPx), Offset(size.width, (mEndOff + 1) * rowPx), 1f)

                                intervals.forEachIndexed { idx, interval ->
                                    val color = dogColors[idx % dogColors.size]
                                    val x     = idx * stripPx
                                    val yTop  = (interval.actualStart.toEpochDay() - chartStart.toEpochDay()) * rowPx
                                    val yBot  = (interval.actualEnd.toEpochDay()   - chartStart.toEpochDay() + 1) * rowPx

                                    // Фон всего интервала (включая дни вне месяца) — бледный
                                    drawRoundRect(
                                        color = color.copy(alpha = 0.10f),
                                        topLeft = Offset(x + 1f, yTop),
                                        size = Size(stripPx - 2f, yBot - yTop),
                                        cornerRadius = CornerRadius(with(density) { 4.dp.toPx() })
                                    )
                                    // Часть внутри текущего месяца — ярче
                                    val inTop = maxOf(yTop, mStartOff * rowPx)
                                    val inBot = minOf(yBot, (mEndOff + 1) * rowPx)
                                    if (inBot > inTop) drawRect(
                                        color = color.copy(alpha = 0.18f),
                                        topLeft = Offset(x + 1f, inTop),
                                        size = Size(stripPx - 2f, inBot - inTop)
                                    )
                                    // Цветная рамка по периметру
                                    drawRoundRect(
                                        color = color.copy(alpha = 0.80f),
                                        topLeft = Offset(x + 1f, yTop),
                                        size = Size(stripPx - 2f, yBot - yTop),
                                        cornerRadius = CornerRadius(with(density) { 4.dp.toPx() }),
                                        style = Stroke(width = with(density) { 1.2.dp.toPx() })
                                    )
                                    // Имя собаки вертикально по центру полного интервала
                                    val cx = x + stripPx / 2f
                                    val cy = (yTop + yBot) / 2f
                                    val textSizePx = with(density) { 9.sp.toPx() }
                                    val paint = android.graphics.Paint().apply {
                                        isAntiAlias = true
                                        textSize    = textSizePx
                                        textAlign   = android.graphics.Paint.Align.CENTER
                                        this.color  = color.copy(alpha = 0.9f).toArgb()
                                    }
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.apply {
                                            save()
                                            clipRect(x, yTop, x + stripPx, yBot)
                                            rotate(-90f, cx, cy)
                                            drawText(interval.dogName, cx, cy + textSizePx * 0.35f, paint)
                                            restore()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    } // Row
                } // Box verticalScroll (weight 1f)
            } // Column
            } // BoxWithConstraints
        } // Box fillMaxSize
    } // Dialog
}

@Composable
internal fun BoardingTimeline(
    clients: List<ClientInfo>,
    month: Int,
    year: Int,
    photoCache: Map<String, String> = emptyMap(),
    showFullscreenButton: Boolean = false,
    modifier: Modifier = Modifier
) {
    val monthStart = remember(month, year) { LocalDate.of(year, month, 1) }
    val monthEnd   = remember(monthStart) { monthStart.withDayOfMonth(monthStart.lengthOfMonth()) }
    val intervals  = remember(clients, month, year) { dogIntervalsInMonth(clients, month, year) }
    val chartStart = remember(intervals, monthStart) {
        intervals.minOfOrNull { it.actualStart }?.takeIf { it.isBefore(monthStart) } ?: monthStart
    }
    val chartEnd = remember(intervals, monthEnd) {
        intervals.maxOfOrNull { it.actualEnd }?.takeIf { it.isAfter(monthEnd) } ?: monthEnd
    }
    val totalDays = remember(chartStart, chartEnd) {
        (chartEnd.toEpochDay() - chartStart.toEpochDay() + 1).toInt()
    }
    val today = remember { LocalDate.now() }
    val dogCountPerDay = remember(intervals, chartStart, totalDays) {
        IntArray(totalDays) { offset ->
            val date = chartStart.plusDays(offset.toLong())
            intervals.count { !date.isBefore(it.actualStart) && !date.isAfter(it.actualEnd) }
        }
    }
    val monthNames = listOf("Январь","Февраль","Март","Апрель","Май","Июнь",
                            "Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь")
    val monthAbbr  = listOf("Янв","Фев","Мар","Апр","Май","Июн","Июл","Авг","Сен","Окт","Ноя","Дек")
    val dowLabels  = listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")
    val rowHeightDp = 20.dp
    val chartHeight = rowHeightDp * totalDays
    val density     = LocalDensity.current
    var popupIdx    by remember { mutableStateOf<Int?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    val dateFmt = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale("ru")) }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                BoardingTimeline(
                    clients              = clients,
                    month                = month,
                    year                 = year,
                    photoCache           = photoCache,
                    showFullscreenButton = false,
                    modifier             = Modifier.fillMaxSize()
                )
            }
        }
    }

    popupIdx?.let { idx ->
        if (idx in intervals.indices) {
            val iv       = intervals[idx]
            val client   = iv.client
            val color    = DOG_COLORS[idx % DOG_COLORS.size]
            val photoUrl = photoCache[clientPhotoKey(client)]
            Dialog(onDismissRequest = { popupIdx = null }) {
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    border = BorderStroke(1.5.dp, color.copy(alpha = 0.55f)),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(4.dp).background(color)
                        )
                        Row(
                            modifier = Modifier.padding(12.dp).height(IntrinsicSize.Max),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(client.dogName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (client.ownerName.isNotBlank())
                                    Text(client.ownerName, fontSize = 13.sp, color = Color(0xFF424242))
                                if (client.breed.isNotBlank())
                                    Text(client.breed, fontSize = 12.sp, color = Color(0xFF757575))
                                if (client.phone.isNotBlank())
                                    Text(client.phone, fontSize = 13.sp, color = Color(0xFF1565C0))
                                Text(
                                    "${iv.actualStart.format(dateFmt)} — ${iv.actualEnd.format(dateFmt)}",
                                    fontSize = 13.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium
                                )
                            }
                            if (photoUrl?.isNotBlank() == true) {
                                Spacer(Modifier.width(10.dp))
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(photoUrl).crossfade(true).build(),
                                    contentDescription = null,
                                    modifier = Modifier.width(107.dp).border(1.5.dp, Color(0xFF0D2B5E)),
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        if (intervals.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (clients.isEmpty()) "Загрузка данных…" else "Нет передержек в ${monthNames[month - 1]}",
                    color = Color(0xFF9E9E9E), fontSize = 14.sp,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            val horizPad      = 12.dp
            val dateColWidth  = 44.dp
            val countColWidth = 22.dp
            val gap           = 4.dp
            val stripAreaWidth = maxWidth - horizPad * 2 - dateColWidth - countColWidth - gap
            val stripWidthDp = (stripAreaWidth / intervals.size).coerceAtLeast(14.dp)
            val stripPx = with(density) { stripWidthDp.toPx() }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = horizPad)) {
                val n = intervals.size
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${monthNames[month - 1]} $year — $n ${dogWord(n)}",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (showFullscreenButton) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Развернуть",
                            tint = Color(0xFF757575),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { isFullscreen = true }
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Row(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
                        Column(modifier = Modifier.width(dateColWidth)) {
                            for (offset in 0 until totalDays) {
                                val date      = chartStart.plusDays(offset.toLong())
                                val inMonth   = date.monthValue == month && date.year == year
                                val isWeekend = date.dayOfWeek.value >= 6
                                val isToday   = date == today
                                val label = if (inMonth)
                                    "%2d %s".format(date.dayOfMonth, dowLabels[date.dayOfWeek.value - 1])
                                else
                                    "%2d %s".format(date.dayOfMonth, monthAbbr[date.monthValue - 1])
                                val overloaded = dogCountPerDay[offset] > 3
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(rowHeightDp).background(
                                        when {
                                            isToday                -> Color(0xFF4CAF50).copy(alpha = 0.40f)
                                            overloaded && inMonth  -> Color(0xFFD32F2F).copy(alpha = 0.10f)
                                            overloaded && !inMonth -> Color(0xFFD32F2F).copy(alpha = 0.07f)
                                            !inMonth               -> Color(0xFFF5F5F5)
                                            else                   -> Color.Transparent
                                        }
                                    ),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(label, fontSize = 10.sp,
                                        color = when {
                                            isToday   -> Color(0xFF1B5E20)
                                            !inMonth  -> Color(0xFFBDBDBD)
                                            isWeekend -> Color(0xFFE53935)
                                            else      -> Color(0xFF424242)
                                        },
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.width(countColWidth).fillMaxHeight()) {
                            for (offset in 0 until totalDays) {
                                val date    = chartStart.plusDays(offset.toLong())
                                val inMonth = date.monthValue == month && date.year == year
                                val count   = dogCountPerDay[offset]
                                val countBg = when {
                                    !inMonth   -> Color.Transparent
                                    count == 0 -> Color(0xFF4CAF50).copy(alpha = 0.25f)
                                    count == 1 -> Color(0xFFFFEB3B).copy(alpha = 0.50f)
                                    count == 2 -> Color(0xFF29B6F6).copy(alpha = 0.35f)
                                    count == 3 -> Color(0xFFF48FB1).copy(alpha = 0.50f)
                                    else       -> Color(0xFFEF5350).copy(alpha = 0.40f)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeightDp)
                                        .background(countBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (inMonth) Text(
                                        text = count.toString(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(gap))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Canvas(
                                modifier = Modifier
                                    .width(stripWidthDp * intervals.size)
                                    .fillMaxHeight()
                                    .pointerInput(intervals, stripPx) {
                                        detectTapGestures { offset ->
                                            val idx = (offset.x / stripPx).toInt()
                                                .coerceIn(0, intervals.lastIndex)
                                            popupIdx = idx
                                        }
                                    }
                            ) {
                                val rowPx     = with(density) { rowHeightDp.toPx() }
                                val mStartOff = (monthStart.toEpochDay() - chartStart.toEpochDay()).toInt()
                                val mEndOff   = (monthEnd.toEpochDay()   - chartStart.toEpochDay()).toInt()
                                for (offset in 0 until totalDays) {
                                    val y    = offset * rowPx
                                    val date = chartStart.plusDays(offset.toLong())
                                    val inMonth = date.monthValue == month && date.year == year
                                    if (!inMonth) drawRect(Color(0xFFF5F5F5), Offset(0f, y), Size(size.width, rowPx))
                                    if (offset > 0) drawLine(Color(0xFFBDBDBD), Offset(0f, y), Offset(size.width, y), 0.7f)
                                }
                                for (offset in 0 until totalDays) {
                                    if (dogCountPerDay[offset] > 3) {
                                        val y = offset * rowPx
                                        val date = chartStart.plusDays(offset.toLong())
                                        val inMonth = date.monthValue == month && date.year == year
                                        drawRect(Color(0xFFD32F2F).copy(alpha = if (inMonth) 0.13f else 0.08f),
                                            Offset(0f, y), Size(size.width, rowPx))
                                    }
                                }
                                val todayOffset = (today.toEpochDay() - chartStart.toEpochDay()).toInt()
                                if (todayOffset in 0 until totalDays)
                                    drawRect(Color(0xFF4CAF50).copy(alpha = 0.22f),
                                        Offset(0f, todayOffset * rowPx), Size(size.width, rowPx))
                                if (mStartOff > 0)
                                    drawLine(Color(0xFFBDBDBD), Offset(0f, mStartOff * rowPx), Offset(size.width, mStartOff * rowPx), 1f)
                                if (mEndOff < totalDays - 1)
                                    drawLine(Color(0xFFBDBDBD), Offset(0f, (mEndOff + 1) * rowPx), Offset(size.width, (mEndOff + 1) * rowPx), 1f)
                                intervals.forEachIndexed { idx, interval ->
                                    val color = DOG_COLORS[idx % DOG_COLORS.size]
                                    val x     = idx * stripPx
                                    val yTop  = (interval.actualStart.toEpochDay() - chartStart.toEpochDay()) * rowPx
                                    val yBot  = (interval.actualEnd.toEpochDay()   - chartStart.toEpochDay() + 1) * rowPx
                                    drawRoundRect(color.copy(alpha = 0.10f), Offset(x+1f, yTop), Size(stripPx-2f, yBot-yTop),
                                        CornerRadius(with(density){4.dp.toPx()}))
                                    val inTop = maxOf(yTop, mStartOff * rowPx)
                                    val inBot = minOf(yBot, (mEndOff + 1) * rowPx)
                                    if (inBot > inTop) drawRect(color.copy(alpha = 0.18f), Offset(x+1f, inTop), Size(stripPx-2f, inBot-inTop))
                                    drawRoundRect(color.copy(alpha = 0.80f), Offset(x+1f, yTop), Size(stripPx-2f, yBot-yTop),
                                        CornerRadius(with(density){4.dp.toPx()}),
                                        style = Stroke(width = with(density){1.2.dp.toPx()}))
                                    val cx = x + stripPx / 2f
                                    val cy = (yTop + yBot) / 2f
                                    val textSizePx = with(density) { 9.sp.toPx() }
                                    val paint = android.graphics.Paint().apply {
                                        isAntiAlias = true; textSize = textSizePx
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        this.color = color.copy(alpha = 0.9f).toArgb()
                                    }
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.apply {
                                            save()
                                            clipRect(x, yTop, x + stripPx, yBot)
                                            rotate(-90f, cx, cy)
                                            drawText(interval.dogName, cx, cy + textSizePx * 0.35f, paint)
                                            restore()
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
}

private fun totalDogDaysInMonth(clients: List<ClientInfo>, month: Int, year: Int): Int {
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val monthStart = LocalDate.of(year, month, 1)
    val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
    val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
    return clients.sumOf { client ->
        if (client.boardingHistory.isBlank()) return@sumOf 0
        var days = 0
        for (line in client.boardingHistory.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            val dates = datePattern.findAll(trimmed).mapNotNull { m ->
                try { LocalDate.parse(m.value, fmt) } catch (_: Exception) { null }
            }.toList()
            when {
                dates.isEmpty() -> Unit
                dates.size == 1 -> {
                    val d = dates[0]
                    if (d.monthValue == month && d.year == year) days++
                }
                else -> {
                    val start = dates.first()
                    val end = dates.last()
                    if (!end.isBefore(monthStart) && !start.isAfter(monthEnd)) {
                        val clippedStart = if (start.isBefore(monthStart)) monthStart else start
                        val clippedEnd = if (end.isAfter(monthEnd)) monthEnd else end
                        val d = (clippedEnd.toEpochDay() - clippedStart.toEpochDay() + 1).toInt()
                        if (d > 0) days += d
                    }
                }
            }
        }
        days
    }
}

private fun countDogs(dogName: String): Int {
    if (dogName.isBlank()) return 1
    val parts = dogName.split(Regex("""\s+и\s+|\+|&|,"""))
        .map { it.trim() }.filter { it.isNotBlank() }
    return maxOf(1, parts.size)
}

// Возвращает заработок если дата окончания попадает в указанный месяц, иначе null.
// С 2026: 1 — 120 ₪/день, 2 — 180 ₪/день, 3+ — 340 ₪/день.
// До 2026: 1 — 100 ₪/день, 2 — 150 ₪/день, 3+ — 200 ₪/день.
private fun intervalEarnings(intervalStr: String, month: Int, year: Int, dogName: String = ""): Int? {
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
    val dates = datePattern.findAll(intervalStr).mapNotNull { m ->
        try { LocalDate.parse(m.value, fmt) } catch (_: Exception) { null }
    }.toList()
    if (dates.size < 2) return null
    val end = dates.last()
    if (end.monthValue != month || end.year != year) return null
    val days = Regex("""\((\d+)""").find(intervalStr)?.groupValues?.get(1)?.toIntOrNull()
        ?: (end.toEpochDay() - dates.first().toEpochDay() + 1).toInt()
    val rate = if (year >= 2026) {
        when (countDogs(dogName)) { 1 -> 120; 2 -> 180; else -> 240 }
    } else {
        when (countDogs(dogName)) { 1 -> 100; 2 -> 150; else -> 200 }
    }
    return days * rate
}

private fun allBoardingIntervalsInMonth(client: ClientInfo, month: Int, year: Int): List<String> {
    if (client.boardingHistory.isBlank()) return emptyList()
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val monthStart = LocalDate.of(year, month, 1)
    val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
    val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
    return buildList {
        for (line in client.boardingHistory.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            val dates = datePattern.findAll(trimmed).mapNotNull { m ->
                try { LocalDate.parse(m.value, fmt) } catch (_: Exception) { null }
            }.toList()
            val overlaps = when (dates.size) {
                0 -> false
                1 -> dates[0].let { it.monthValue == month && it.year == year }
                else -> !dates.last().isBefore(monthStart) && !dates.first().isAfter(monthEnd)
            }
            if (overlaps) {
                val dateStrs = datePattern.findAll(trimmed).map { it.value }.toList()
                val parens = Regex("""\(\d+[^)]*\)""").find(trimmed)?.value
                val base = if (dateStrs.size >= 2) "${dateStrs.first()} — ${dateStrs.last()}"
                           else dateStrs.firstOrNull() ?: trimmed
                add(if (parens != null) "$base $parens" else base)
            }
        }
    }
}

private fun findBoardingIntervalForMonth(client: ClientInfo, month: Int, year: Int): String? {
    if (client.boardingHistory.isBlank()) return null
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val monthStart = LocalDate.of(year, month, 1)
    val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
    val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
    val line = client.boardingHistory.lines().firstOrNull { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@firstOrNull false
        val dates = datePattern.findAll(trimmed).mapNotNull { m ->
            try { LocalDate.parse(m.value, fmt) } catch (_: Exception) { null }
        }.toList()
        when (dates.size) {
            0 -> false
            1 -> dates[0].let { it.monthValue == month && it.year == year }
            else -> !dates.last().isBefore(monthStart) && !dates.first().isAfter(monthEnd)
        }
    }?.trim() ?: return null
    val dates = datePattern.findAll(line).map { it.value }.toList()
    val parens = Regex("""\(\d+[^)]*\)""").find(line)?.value
    return if (dates.size >= 2) {
        val base = "${dates.first()} — ${dates.last()}"
        if (parens != null) "$base $parens" else base
    } else dates.firstOrNull() ?: line
}

private fun boardingStartInMonth(client: ClientInfo, month: Int, year: Int): LocalDate? {
    if (client.boardingHistory.isBlank()) return null
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val monthStart = LocalDate.of(year, month, 1)
    val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
    val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
    for (line in client.boardingHistory.lines()) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) continue
        val dates = datePattern.findAll(trimmed).mapNotNull { m ->
            try { LocalDate.parse(m.value, fmt) } catch (_: Exception) { null }
        }.toList()
        val overlaps = when (dates.size) {
            0 -> false
            1 -> dates[0].let { it.monthValue == month && it.year == year }
            else -> !dates.last().isBefore(monthStart) && !dates.first().isAfter(monthEnd)
        }
        if (overlaps) return dates.firstOrNull()
    }
    return null
}

internal fun clientHasBoardingInMonth(client: ClientInfo, month: Int, year: Int): Boolean {
    if (client.boardingHistory.isBlank()) return false
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val monthStart = LocalDate.of(year, month, 1)
    val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
    val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
    return client.boardingHistory.lines().any { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@any false
        val dates = datePattern.findAll(trimmed).mapNotNull { m ->
            try { LocalDate.parse(m.value, fmt) } catch (_: Exception) { null }
        }.toList()
        when (dates.size) {
            0 -> false
            1 -> dates[0].let { it.monthValue == month && it.year == year }
            // Range: overlaps with target month if start <= monthEnd AND end >= monthStart
            else -> !dates.last().isBefore(monthStart) && !dates.first().isAfter(monthEnd)
        }
    }
}

private fun openWhatsApp(context: Context, phone: String) {
    val digits = phone.filter { it.isDigit() }
    if (digits.isBlank()) return
    val wa = when {
        digits.startsWith("972") -> digits
        digits.startsWith("0") && digits.length >= 9 -> "972" + digits.drop(1)
        else -> digits
    }
    val uri = Uri.parse("https://wa.me/$wa")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
    }
}
