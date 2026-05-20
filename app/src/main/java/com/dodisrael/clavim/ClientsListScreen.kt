package com.dodisrael.clavim

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

private const val CLIENTS_SHEET_CSV =
    "https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/export?format=csv&gid=1215152509"

private data class ClientInfo(
    val ownerName: String,
    val phone: String,
    val breed: String,
    val dogName: String,
    val lastBoarding: String = "",
    val lastBoardingMonth: String = ""
)

@Composable
fun ClientsListScreen(
    onBack: () -> Unit,
    onRepeatBoarding: (dogName: String, clarification: String) -> Unit,
    onDeleteBoarding: (dogName: String, clarification: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        errorMessage = ""
        photosToLoad = 0
        photosLoaded = 0
        try {
            val csv = withContext(Dispatchers.IO) {
                val conn = URL(CLIENTS_SHEET_CSV).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.instanceFollowRedirects = true
                conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            }
            val parsed = parseClientsFromCsv(csv)
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

            val clientsByDogName = parsed.groupBy { it.dogName.lowercase() }
            val semaphore = Semaphore(5)

            // Count uncached and set total for progress bar
            val uncached = parsed.filter { !photoCache.containsKey(clientPhotoKey(it)) }
            photosToLoad = uncached.size

            // Load uncached photos in background, max 5 concurrent
            uncached.forEach { client ->
                val key = clientPhotoKey(client)
                scope.launch {
                    semaphore.withPermit {
                        val uniqueOwners = clientsByDogName[client.dogName.lowercase()]
                            ?.distinctBy { it.ownerName.lowercase() }?.size ?: 1
                        val url = findClientPhoto(context, client, uniqueOwners)
                        if (url != null) {
                            // Cache only successful results — never cache "not found"
                            photoPrefs.edit().putString(key, url).apply()
                            photoCache = photoCache + (key to url)
                        } else {
                            // Mark as tried this session (show placeholder), but don't persist
                            photoCache = photoCache + (key to "")
                        }
                        photosLoaded++
                    }
                }
            }
        } catch (e: Exception) {
            errorMessage = "Ошибка загрузки: ${e.message}"
            isLoading = false
        }
    }

    val filteredClients = remember(clients, searchQuery) {
        if (searchQuery.isBlank()) clients
        else clients.filter { c ->
            c.ownerName.contains(searchQuery, ignoreCase = true) ||
            c.dogName.contains(searchQuery, ignoreCase = true) ||
            c.breed.contains(searchQuery, ignoreCase = true) ||
            c.phone.contains(searchQuery)
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

    fullScreenPhotoUrl?.let { url ->
        FullScreenPhotoViewer(photos = listOf(url), onDismiss = { fullScreenPhotoUrl = null })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "Клиенты", subtitle = "Список клиентов", showBack = true, onBack = onBack)

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

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Поиск") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotBlank()) {
                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, "Очистить") } }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            errorMessage.isNotBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMessage, color = Color(0xFFD32F2F), modifier = Modifier.padding(16.dp))
            }
            filteredClients.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isBlank()) "Список пуст" else "Ничего не найдено",
                    color = Color.Gray, fontSize = 16.sp
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredClients) { client ->
                    val photoUrl = photoCache[clientPhotoKey(client)]
                    ClientCard(
                        client = client,
                        photoUrl = photoUrl,
                        onCall = { callPhone(context, client.phone) },
                        onWhatsApp = { openWhatsApp(context, client.phone) },
                        onRepeatBoarding = {
                            onRepeatBoarding(client.dogName, clientClarification(client))
                        },
                        onDeleteBoarding = {
                            onDeleteBoarding(client.dogName, clientClarification(client))
                        },
                        onPhotoClick = { url -> fullScreenPhotoUrl = url }
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
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onRepeatBoarding: () -> Unit,
    onDeleteBoarding: () -> Unit,
    onPhotoClick: (String) -> Unit
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
            Column(modifier = Modifier.weight(1f)) {
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
                        text = client.breed,
                        fontSize = 12.sp,
                        color = Color(0xFF757575),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (client.phone.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = client.phone,
                        fontSize = 13.sp,
                        color = Color(0xFF1565C0)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                }
            }

            Spacer(Modifier.width(10.dp))

            // Dog photo on the right — fills full card height, fixed width
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF3E5F5))
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
                        tint = Color(0xFFCE93D8),
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
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.13f))
    ) {
        Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(20.dp))
    }
}

private fun parseClientsFromCsv(csv: String): List<ClientInfo> =
    csv.lines().drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
        val cols = splitCsvLine(line)
        val ownerName = cols.getOrNull(0)?.trim().takeIf { !it.isNullOrBlank() } ?: return@mapNotNull null
        val dogName = cols.getOrNull(3)?.trim().takeIf { !it.isNullOrBlank() } ?: return@mapNotNull null
        ClientInfo(
            ownerName = ownerName,
            phone = cols.getOrNull(1)?.trim() ?: "",
            breed = cols.getOrNull(2)?.trim() ?: "",
            dogName = dogName,
            lastBoarding = cols.getOrNull(7)?.trim() ?: "",
            lastBoardingMonth = cols.getOrNull(4)?.trim() ?: ""
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

private fun clientPhotoKey(client: ClientInfo) = "${client.dogName}|${client.ownerName}|${client.breed}"

private fun clientClarification(client: ClientInfo): String = when {
    client.breed.isNotBlank() && client.ownerName.isNotBlank() -> "${client.breed}, Хозяин: ${client.ownerName}"
    client.ownerName.isNotBlank() -> "Хозяин: ${client.ownerName}"
    client.breed.isNotBlank() -> client.breed
    else -> ""
}

private suspend fun findClientPhoto(
    context: Context,
    client: ClientInfo,
    uniqueOwnerCount: Int
): String? = withContext(Dispatchers.IO) {
    try {
        val posts = FosteringDatabase.get(context).dao().search(client.dogName)
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
