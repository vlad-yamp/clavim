package com.dodisrael.clavim

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Streetview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.net.HttpURLConnection
import java.net.URL

data class AddressEntry(val name: String, val hebrew: String, val russian: String)

internal fun loadAddresses(prefs: android.content.SharedPreferences): List<AddressEntry> {
    val json = prefs.getString("addresses", null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            AddressEntry(obj.optString("name"), obj.optString("hebrew"), obj.optString("russian"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveAddresses(prefs: android.content.SharedPreferences, addresses: List<AddressEntry>) {
    val arr = JSONArray()
    addresses.forEach { a ->
        arr.put(JSONObject().apply {
            put("name", a.name); put("hebrew", a.hebrew); put("russian", a.russian)
        })
    }
    prefs.edit().putString("addresses", arr.toString()).apply()
}

private suspend fun fetchAddressesFromServer(url: String): List<AddressEntry> =
    withContext(Dispatchers.IO) {
        val conn = URL("$url?sheet=Адреса").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            if (!json.optBoolean("ok", false)) throw Exception(json.optString("error"))
            val arr = json.getJSONArray("rows")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                AddressEntry(obj.optString("name"), obj.optString("hebrew"), obj.optString("russian"))
            }
        } finally {
            conn.disconnect()
        }
    }

private suspend fun saveAddressesToServer(url: String, addresses: List<AddressEntry>): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            addresses.forEach { a ->
                arr.put(JSONObject().apply {
                    put("name", a.name); put("hebrew", a.hebrew); put("russian", a.russian)
                })
            }
            val body = arr.toString().toByteArray(Charsets.UTF_8)
            val conn = URL("$url?action=replace&sheet=Адреса").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.outputStream.use { it.write(body) }
            val json = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            json.optBoolean("ok", false)
        } catch (_: Exception) { false }
    }

private val GROUP_COLOR_PALETTE = listOf(
    Color(0xFFE3F2FD), Color(0xFFE8F5E9), Color(0xFFFFF3E0), Color(0xFFFCE4EC),
    Color(0xFFF3E5F5), Color(0xFFE0F7FA), Color(0xFFF1F8E9), Color(0xFFECEFF1),
    Color(0xFFFFF9C4), Color(0xFFEDE7F6)
)

// name → Pair(colorIndex, groupName)
private fun loadCachedData(
    prefs: android.content.SharedPreferences,
    names: List<String>
): Pair<Map<String, Color>, Map<String, String>> {
    val cacheKey = names.sorted().joinToString("|")
    if (prefs.getString("address_color_cache_key", "") != cacheKey) return Pair(emptyMap(), emptyMap())
    return try {
        val obj = JSONObject(prefs.getString("address_color_cache", "") ?: return Pair(emptyMap(), emptyMap()))
        val colors = buildMap<String, Color> {
            for (k in obj.keys()) put(k, GROUP_COLOR_PALETTE[obj.getJSONObject(k).getInt("i") % GROUP_COLOR_PALETTE.size])
        }
        val groups = buildMap<String, String> {
            for (k in obj.keys()) put(k, obj.getJSONObject(k).getString("g"))
        }
        Pair(colors, groups)
    } catch (_: Exception) { Pair(emptyMap(), emptyMap()) }
}

// name → Pair(colorIndex, groupName)
private suspend fun classifyAddressesWithGpt(
    names: List<String>,
    apiKey: String
): Map<String, Pair<Int, String>> = withContext(Dispatchers.IO) {
    val prompt = "Перед тобой список названий адресов из приложения для выгула и дрессировки собак.\n" +
        "Сгруппируй их по смыслу. Названия групп должны быть на русском языке.\n" +
        "Верни ТОЛЬКО валидный JSON, без пояснений:\n" +
        "{\"groups\":[{\"name\":\"название группы\",\"items\":[\"name1\",\"name2\"]}]}\n\n" +
        "Названия:\n" + names.joinToString("\n") { "- $it" }

    val requestBody = JSONObject().apply {
        put("model", "gpt-4o-mini")
        put("messages", JSONArray().apply {
            put(JSONObject().apply { put("role", "user"); put("content", prompt) })
        })
        put("temperature", 0.2)
    }.toString().toByteArray(Charsets.UTF_8)

    val conn = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
    conn.setRequestProperty("Authorization", "Bearer $apiKey")
    conn.doOutput = true
    conn.connectTimeout = 30_000
    conn.readTimeout = 30_000
    conn.outputStream.use { it.write(requestBody) }

    val content = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
        .getJSONArray("choices").getJSONObject(0)
        .getJSONObject("message").getString("content")
        .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    val groups = JSONObject(content).getJSONArray("groups")
    buildMap {
        for (i in 0 until groups.length()) {
            val group = groups.getJSONObject(i)
            val groupName = group.getString("name")
            val items = group.getJSONArray("items")
            for (j in 0 until items.length()) put(items.getString(j), Pair(i, groupName))
        }
    }
}

private fun openYandexNavigator(context: Context, destination: String) {
    val trimmed = destination.trim()
    val coordPattern = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*[,; ]\\s*(-?\\d+(?:\\.\\d+)?)\\s*$")
    val match = coordPattern.find(trimmed)
    val url = if (match != null) {
        "yandexnavi://build_route_on_map?lat_to=${match.groupValues[1]}&lon_to=${match.groupValues[2]}"
    } else {
        "yandexnavi://build_route_on_map?text=${Uri.encode(trimmed)}"
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        setPackage("ru.yandex.yandexnavi")
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Яндекс Навигатор не установлен", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddressesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("clavim_prefs", Context.MODE_PRIVATE) }
    val scriptUrl = remember { prefs.getString("addresses_script_url", "") ?: "" }
    val openAiKey = remember { prefs.getString("openai_api_key", "") ?: "" }

    val initialAddresses = remember { loadAddresses(prefs) }
    var addresses by remember { mutableStateOf(initialAddresses) }
    val initialCache = remember { loadCachedData(prefs, initialAddresses.map { it.name }) }
    var addressColors by remember { mutableStateOf(initialCache.first) }
    var addressGroupNames by remember { mutableStateOf(initialCache.second) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var deletingIndex by remember { mutableStateOf<Int?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newList = addresses.toMutableList().apply { add(to.index, removeAt(from.index)) }
        addresses = newList
        saveAddresses(prefs, newList)
        if (scriptUrl.isNotBlank()) {
            scope.launch {
                saveAddressesToServer(scriptUrl, newList)
            }
        }
    }

    fun loadFromServer() {
        if (scriptUrl.isBlank()) return
        scope.launch {
            isLoading = true
            syncError = ""
            try {
                val loaded = fetchAddressesFromServer(scriptUrl)
                addresses = loaded
                saveAddresses(prefs, loaded)
                if (openAiKey.isNotBlank()) {
                    val names = loaded.map { it.name }
                    val cacheKey = "v3|" + names.sorted().joinToString("|")
                    val cached = if (prefs.getString("address_color_cache_key", "") == cacheKey)
                        loadCachedData(prefs, names) else Pair(emptyMap(), emptyMap())
                    if (cached.first.isNotEmpty()) {
                        addressColors = cached.first
                        addressGroupNames = cached.second
                    } else {
                        val grouping = classifyAddressesWithGpt(names, openAiKey)
                        addressColors = grouping.mapValues { (_, p) -> GROUP_COLOR_PALETTE[p.first % GROUP_COLOR_PALETTE.size] }
                        addressGroupNames = grouping.mapValues { (_, p) -> p.second }
                        val cacheJson = JSONObject().apply {
                            grouping.forEach { (k, p) ->
                                put(k, JSONObject().apply { put("i", p.first); put("g", p.second) })
                            }
                        }
                        prefs.edit()
                            .putString("address_color_cache_key", cacheKey)
                            .putString("address_color_cache", cacheJson.toString())
                            .apply()
                    }
                }
            } catch (e: Exception) {
                syncError = "Ошибка загрузки: ${e.message}"
            }
            isLoading = false
        }
    }

    fun applyAndSync(newList: List<AddressEntry>) {
        addresses = newList
        saveAddresses(prefs, newList)
        if (scriptUrl.isNotBlank()) {
            scope.launch {
                val ok = saveAddressesToServer(scriptUrl, newList)
                if (!ok) Toast.makeText(context, "Ошибка сохранения на сервер", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) { loadFromServer() }

    if (editingIndex != null || showAddDialog) {
        val initial = editingIndex?.let { addresses[it] } ?: AddressEntry("", "", "")
        AddressEditDialog(
            initial = initial,
            onConfirm = { updated ->
                val newList = if (editingIndex != null)
                    addresses.toMutableList().also { it[editingIndex!!] = updated }
                else
                    addresses + updated
                applyAndSync(newList)
                editingIndex = null
                showAddDialog = false
            },
            onDismiss = { editingIndex = null; showAddDialog = false }
        )
    }

    deletingIndex?.let { idx ->
        AlertDialog(
            onDismissRequest = { deletingIndex = null },
            title = { Text("Удалить адрес?") },
            text = { Text(addresses[idx].name) },
            confirmButton = {
                Button(
                    onClick = {
                        applyAndSync(addresses.toMutableList().also { it.removeAt(idx) })
                        deletingIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
                ) { Text("Удалить", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { deletingIndex = null }) { Text("Отмена") }
            }
        )
    }

    val filteredAddresses = remember(addresses, searchQuery) {
        if (searchQuery.isBlank()) addresses
        else addresses.filter { a ->
            a.name.contains(searchQuery, ignoreCase = true) ||
            a.hebrew.contains(searchQuery, ignoreCase = true) ||
            a.russian.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Адреса",
            subtitle = if (searchActive) "Поиск по имени и адресу" else "Удерживайте ≡ для перемещения",
            showBack = true,
            onBack = onBack
        )

        if (searchActive) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFAD1457)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить", tint = Color(0xFF757575))
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color(0xFFAD1457),
                    unfocusedIndicatorColor = Color(0xFFE0E0E0)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (syncError.isNotBlank()) {
            Text(
                syncError,
                fontSize = 12.sp,
                color = Color(0xFFB00020),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading && filteredAddresses.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFAD1457)
                )
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(filteredAddresses, key = { _, item -> item.name + item.hebrew }) { idx, addr ->
                        ReorderableItem(reorderState, key = addr.name + addr.hebrew) { isDragging ->
                            AddressCard(
                                entry = addr,
                                cardColor = addressColors[addr.name] ?: Color.White,
                                groupName = addressGroupNames[addr.name] ?: "",
                                isDragging = isDragging,
                                dragHandleModifier = Modifier.longPressDraggableHandle(),
                                onEdit = { editingIndex = idx },
                                onDelete = { deletingIndex = idx },
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val text = listOf(addr.hebrew, addr.russian).filter { it.isNotBlank() }.joinToString("\n")
                                    cm.setPrimaryClip(ClipData.newPlainText("", text))
                                    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                                },
                                onWaze = {
                                    val query = addr.hebrew.ifBlank { addr.russian }
                                    val url = "https://waze.com/ul?q=${Uri.encode(query)}&navigate=yes"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        setPackage("com.waze")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: ActivityNotFoundException) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                },
                                onYandex = {
                                    val query = addr.hebrew.ifBlank { addr.russian }
                                    openYandexNavigator(context, query)
                                },
                                onStreetView = {
                                    val query = addr.hebrew
                                    if (query.isNotBlank()) {
                                        val uri = Uri.parse("https://maps.google.com/maps?q=${Uri.encode(query)}&layer=c")
                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                            setPackage("com.google.android.apps.maps")
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: ActivityNotFoundException) {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                        }
                                    }
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingActionButton(
                    onClick = {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    },
                    containerColor = if (searchActive) Color(0xFFAD1457) else Color(0xFF78909C)
                ) {
                    Icon(
                        if (searchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Поиск",
                        tint = Color.White
                    )
                }
                if (scriptUrl.isNotBlank()) {
                    FloatingActionButton(
                        onClick = { loadFromServer() },
                        containerColor = Color(0xFF757575)
                    ) {
                        if (isLoading)
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        else
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = Color.White)
                    }
                }
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color(0xFF0D47A1)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AddressCard(
    entry: AddressEntry,
    cardColor: Color,
    groupName: String,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onWaze: () -> Unit,
    onYandex: () -> Unit,
    onStreetView: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(if (isDragging) 8.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Переместить",
                    tint = Color(0xFFBDBDBD),
                    modifier = dragHandleModifier
                        .size(32.dp)
                        .padding(end = 4.dp)
                )
                Text(
                    entry.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color(0xFF1C1B1F),
                    modifier = Modifier.weight(1f)
                )
                if (groupName.isNotBlank()) {
                    Text(
                        groupName,
                        fontSize = 11.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.hebrew,
                    fontSize = 14.sp,
                    color = Color(0xFF1C1B1F),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF0D47A1), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFB00020), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.russian,
                    fontSize = 13.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onWaze, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Navigation, contentDescription = "Waze", tint = Color(0xFF33CCFF), modifier = Modifier.size(17.dp))
                }
                if (entry.hebrew.isNotBlank()) {
                    IconButton(onClick = onYandex, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Directions, contentDescription = "Яндекс Навигатор", tint = Color(0xFFFC3F1D), modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onStreetView, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Place, contentDescription = "Street View", tint = Color(0xFFEA4335), modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressEditDialog(
    initial: AddressEntry,
    onConfirm: (AddressEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var hebrew by remember { mutableStateOf(initial.hebrew) }
    var russian by remember { mutableStateOf(initial.russian) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Адрес", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hebrew,
                    onValueChange = { hebrew = it },
                    label = { Text("На иврите") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = russian,
                    onValueChange = { russian = it },
                    label = { Text("На русском") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Button(
                        onClick = { onConfirm(AddressEntry(name.trim(), hebrew.trim(), russian.trim())) },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))
                    ) { Text("Сохранить", color = Color.White) }
                }
            }
        }
    }
}
