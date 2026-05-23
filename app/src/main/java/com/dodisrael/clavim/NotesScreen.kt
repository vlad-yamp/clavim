package com.dodisrael.clavim

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

private const val NOTES_CLIENTS_CSV =
    "https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/export?format=csv&gid=1215152509"

private val NOTE_MONTH_NAMES_SHORT = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн",
                                            "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")
private val NOTE_MONTH_NAMES_FULL  = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                                            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")

data class NoteEntry(val key: String, val month: String, val value: String)

/** month stored as "M_YYYY", displayed as full month name. Blank = "Все передержки". */
fun noteMonthDisplay(month: String): String {
    if (month.isBlank()) return "Все передержки"
    val sep = if (month.contains('_')) '_' else '/'
    val parts = month.split(sep)
    val m = parts.getOrNull(0)?.toIntOrNull() ?: return month
    val y = parts.getOrNull(1) ?: return month
    return "${NOTE_MONTH_NAMES_FULL.getOrNull(m - 1) ?: m} $y"
}

fun loadNotes(prefs: android.content.SharedPreferences): List<NoteEntry> {
    val json = prefs.getString("fostering_notes", null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            NoteEntry(obj.optString("key"), obj.optString("month"), obj.optString("value"))
        }
    } catch (_: Exception) { emptyList() }
}

fun saveNotes(prefs: android.content.SharedPreferences, notes: List<NoteEntry>) {
    val arr = JSONArray()
    notes.forEach { n ->
        arr.put(JSONObject().apply { put("key", n.key); put("month", n.month); put("value", n.value) })
    }
    prefs.edit().putString("fostering_notes", arr.toString()).apply()
}

private suspend fun fetchNotesFromServer(url: String): List<NoteEntry> =
    withContext(Dispatchers.IO) {
        val conn = URL("$url?sheet=Заметки").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            if (!json.optBoolean("ok", false)) throw Exception(json.optString("error"))
            val arr = json.getJSONArray("rows")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                NoteEntry(obj.optString("key"), obj.optString("month"), obj.optString("value"))
            }
        } finally {
            conn.disconnect()
        }
    }

suspend fun saveNotesToServer(url: String, notes: List<NoteEntry>): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            notes.forEach { n ->
                arr.put(JSONObject().apply { put("key", n.key); put("month", n.month); put("value", n.value) })
            }
            val body = arr.toString().toByteArray(Charsets.UTF_8)
            val conn = URL("$url?action=replace&sheet=Заметки").openConnection() as HttpURLConnection
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

private suspend fun loadClientKeys(): List<String> = withContext(Dispatchers.IO) {
    try {
        val conn = URL("$NOTES_CLIENTS_CSV&t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = true
        val csv = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val seen = mutableSetOf<String>()
        csv.lines().drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val cols = splitNoteCsvLine(line)
            val owner = cols.getOrNull(0)?.trim().takeIf { !it.isNullOrBlank() } ?: return@mapNotNull null
            val dog   = cols.getOrNull(3)?.trim().takeIf { !it.isNullOrBlank() } ?: return@mapNotNull null
            val key   = "$owner - $dog"
            if (seen.add(key)) key else null
        }.sortedBy { it.lowercase() }
    } catch (_: Exception) { emptyList() }
}

private fun splitNoteCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var inQuotes = false
    val current = StringBuilder()
    for (ch in line) {
        when {
            ch == '"' -> inQuotes = !inQuotes
            ch == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
            else -> current.append(ch)
        }
    }
    result.add(current.toString())
    return result
}

@Composable
fun NotesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("clavim_prefs", Context.MODE_PRIVATE) }
    val scriptUrl = remember { prefs.getString("addresses_script_url", "") ?: "" }
    val notesPrefs = remember { context.getSharedPreferences("clavim_notes_prefs", Context.MODE_PRIVATE) }

    var notes by remember { mutableStateOf(loadNotes(notesPrefs)) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var deletingIndex by remember { mutableStateOf<Int?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf("") }

    fun applyAndSync(newList: List<NoteEntry>) {
        notes = newList
        saveNotes(notesPrefs, newList)
        if (scriptUrl.isNotBlank()) {
            scope.launch {
                val ok = saveNotesToServer(scriptUrl, newList)
                if (!ok) Toast.makeText(context, "Ошибка сохранения на сервер", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadFromServer() {
        if (scriptUrl.isBlank()) return
        scope.launch {
            isLoading = true
            syncError = ""
            try {
                val loaded = fetchNotesFromServer(scriptUrl)
                notes = loaded
                saveNotes(notesPrefs, loaded)
            } catch (e: Exception) {
                syncError = "Ошибка загрузки: ${e.message}"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadFromServer() }

    if (editingIndex != null || showAddDialog) {
        val initial = editingIndex?.let { notes[it] }
        NoteEditDialog(
            initial = initial,
            onConfirm = { updated ->
                val newList = if (editingIndex != null)
                    notes.toMutableList().also { it[editingIndex!!] = updated }
                else
                    notes + updated
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
            title = { Text("Удалить заметку?") },
            text = { Text(notes[idx].key, fontWeight = FontWeight.Medium) },
            confirmButton = {
                Button(
                    onClick = {
                        applyAndSync(notes.toMutableList().also { it.removeAt(idx) })
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

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "Заметки", subtitle = "Заметки о клиентах", showBack = true, onBack = onBack)

        if (syncError.isNotBlank()) {
            Text(
                syncError,
                fontSize = 12.sp,
                color = Color(0xFFB00020),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading && notes.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF388E3C)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(notes, key = { _, item -> item.key + item.month }) { idx, note ->
                        NoteCard(
                            note = note,
                            onEdit = { editingIndex = idx },
                            onDelete = { deletingIndex = idx }
                        )
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
                    containerColor = Color(0xFF388E3C)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(note.key, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF388E3C))
                Text(noteMonthDisplay(note.month), fontSize = 12.sp, color = Color(0xFF757575))
                Spacer(Modifier.height(2.dp))
                Text(note.value, fontSize = 14.sp, color = Color(0xFF1C1B1F), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = Color(0xFF388E3C), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color(0xFFB00020), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditDialog(
    initial: NoteEntry?,
    onConfirm: (NoteEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = initial != null
    val today = remember { LocalDate.now() }

    var key by remember { mutableStateOf(initial?.key ?: "") }
    val initParts = initial?.month?.let { m -> if (m.contains('_')) m.split('_') else m.split('/') }
    var selectedMonth by remember { mutableIntStateOf(initParts?.getOrNull(0)?.toIntOrNull() ?: today.monthValue) }
    var selectedYear  by remember { mutableIntStateOf(initParts?.getOrNull(1)?.toIntOrNull() ?: today.year) }
    var value by remember { mutableStateOf(initial?.value ?: "") }
    var forAll by remember { mutableStateOf(initial?.month.isNullOrBlank()) }

    var clientKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var keysLoading by remember { mutableStateOf(!isEditing) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredKeys = remember(clientKeys, searchQuery) {
        if (searchQuery.isBlank()) clientKeys
        else clientKeys.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        if (!isEditing) {
            clientKeys = loadClientKeys()
            keysLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (isEditing) "Редактировать заметку" else "Новая заметка",
                    fontWeight = FontWeight.SemiBold, fontSize = 17.sp
                )

                // Client picker
                if (isEditing) {
                    OutlinedTextField(
                        value = key, onValueChange = {},
                        label = { Text("Клиент") },
                        readOnly = true, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (keysLoading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = Color(0xFF388E3C), strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded && filteredKeys.isNotEmpty(),
                        onExpandedChange = { expanded ->
                            dropdownExpanded = expanded
                            if (expanded && searchQuery.isBlank()) { /* show all on open */ }
                        }
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                key = ""
                                dropdownExpanded = true
                            },
                            label = { Text("Клиент") },
                            placeholder = { Text("Поиск…", color = Color(0xFFBDBDBD)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded && filteredKeys.isNotEmpty()) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded && filteredKeys.isNotEmpty(),
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            filteredKeys.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, fontSize = 14.sp) },
                                    onClick = { key = option; searchQuery = option; dropdownExpanded = false }
                                )
                            }
                        }
                    }
                }

                // "For all boardings" toggle
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

                // Month picker
                if (!forAll) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Месяц передержки", fontSize = 12.sp, color = Color(0xFF757575))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(onClick = { selectedYear-- }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color(0xFF388E3C))
                        }
                        Text(
                            "$selectedYear",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(64.dp)
                        )
                        IconButton(onClick = { selectedYear++ }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF388E3C))
                        }
                    }
                    for (row in 0 until 4) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0 until 3) {
                                val monthNum = row * 3 + col + 1
                                val isSelected = monthNum == selectedMonth
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF388E3C) else Color(0xFFE8F5E9))
                                        .clickable { selectedMonth = monthNum }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        NOTE_MONTH_NAMES_SHORT[monthNum - 1],
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.White else Color(0xFF388E3C),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        if (row < 3) Spacer(Modifier.height(4.dp))
                    }
                }

                OutlinedTextField(
                    value = value, onValueChange = { value = it },
                    label = { Text("Заметка") },
                    minLines = 3, maxLines = 7,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Button(
                        onClick = {
                            val month = if (forAll) "" else "${selectedMonth}_$selectedYear"
                            onConfirm(NoteEntry(key.trim(), month, value.trim()))
                        },
                        enabled = key.isNotBlank() && value.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                    ) { Text("Сохранить", color = Color.White) }
                }
            }
        }
    }
}
