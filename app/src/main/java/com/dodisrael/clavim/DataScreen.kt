package com.dodisrael.clavim

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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

data class DataEntry(val key: String, val value: String)

internal fun loadDataEntries(prefs: android.content.SharedPreferences): List<DataEntry> {
    val json = prefs.getString("data_entries", null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            DataEntry(obj.optString("key"), obj.optString("value"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveDataEntries(prefs: android.content.SharedPreferences, entries: List<DataEntry>) {
    val arr = JSONArray()
    entries.forEach { e ->
        arr.put(JSONObject().apply { put("key", e.key); put("value", e.value) })
    }
    prefs.edit().putString("data_entries", arr.toString()).apply()
}

private suspend fun fetchDataEntriesFromServer(url: String): List<DataEntry> =
    withContext(Dispatchers.IO) {
        val conn = URL("$url?sheet=Данные").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            if (!json.optBoolean("ok", false)) throw Exception(json.optString("error"))
            val arr = json.getJSONArray("rows")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DataEntry(obj.optString("key"), obj.optString("value"))
            }
        } finally {
            conn.disconnect()
        }
    }

private suspend fun saveDataEntriesToServer(url: String, entries: List<DataEntry>): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(JSONObject().apply { put("key", e.key); put("value", e.value) })
            }
            val body = arr.toString().toByteArray(Charsets.UTF_8)
            val conn = URL("$url?action=replace&sheet=Данные").openConnection() as HttpURLConnection
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

@Composable
private fun BiometricLockScreen(onAuthenticated: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var errorText by remember { mutableStateOf("") }
    val onAuthRef = androidx.compose.runtime.rememberUpdatedState(onAuthenticated)

    fun showPrompt() {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            errorText = "Не удалось найти Activity"
            return
        }
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            errorText = when (canAuth) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE    -> "Датчик отпечатка не найден"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED  -> "Отпечаток не добавлен в настройки телефона"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Датчик временно недоступен"
                else -> "Биометрия недоступна (код: $canAuth)"
            }
            return
        }
        errorText = ""
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthRef.value()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        errorText = errString.toString()
                    }
                }
                override fun onAuthenticationFailed() {}
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Наши данные")
            .setSubtitle("Подтвердите личность для входа")
            .setNegativeButtonText("Отмена")
            .build()
        try {
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            errorText = "Ошибка: ${e.message}"
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        showPrompt()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "Наши данные", subtitle = "Защищено", showBack = true, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFF1565C0),
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Требуется аутентификация",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1B1F)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Подтвердите личность для доступа к данным",
                fontSize = 14.sp,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center
            )
            if (errorText.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    errorText,
                    fontSize = 13.sp,
                    color = Color(0xFFB00020),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { showPrompt() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Войти по отпечатку", color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DataEntriesScreen(onBack: () -> Unit) {
    var isAuthenticated by remember { mutableStateOf(false) }

    if (!isAuthenticated) {
        BiometricLockScreen(onAuthenticated = { isAuthenticated = true }, onBack = onBack)
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("clavim_prefs", Context.MODE_PRIVATE) }
    val scriptUrl = remember { prefs.getString("addresses_script_url", "") ?: "" }

    var entries by remember { mutableStateOf(loadDataEntries(prefs)) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var deletingIndex by remember { mutableStateOf<Int?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf("") }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newList = entries.toMutableList().apply { add(to.index, removeAt(from.index)) }
        entries = newList
        saveDataEntries(prefs, newList)
        if (scriptUrl.isNotBlank()) {
            scope.launch { saveDataEntriesToServer(scriptUrl, newList) }
        }
    }

    fun loadFromServer() {
        if (scriptUrl.isBlank()) return
        scope.launch {
            isLoading = true
            syncError = ""
            try {
                val loaded = fetchDataEntriesFromServer(scriptUrl)
                entries = loaded
                saveDataEntries(prefs, loaded)
            } catch (e: Exception) {
                syncError = "Ошибка загрузки: ${e.message}"
            }
            isLoading = false
        }
    }

    fun applyAndSync(newList: List<DataEntry>) {
        entries = newList
        saveDataEntries(prefs, newList)
        if (scriptUrl.isNotBlank()) {
            scope.launch {
                val ok = saveDataEntriesToServer(scriptUrl, newList)
                if (!ok) Toast.makeText(context, "Ошибка сохранения на сервер", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) { loadFromServer() }

    if (editingIndex != null || showAddDialog) {
        val initial = editingIndex?.let { entries[it] } ?: DataEntry("", "")
        DataEntryEditDialog(
            initial = initial,
            onConfirm = { updated ->
                val newList = if (editingIndex != null)
                    entries.toMutableList().also { it[editingIndex!!] = updated }
                else
                    entries + updated
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
            title = { Text("Удалить запись?") },
            text = { Text(entries[idx].key) },
            confirmButton = {
                Button(
                    onClick = {
                        applyAndSync(entries.toMutableList().also { it.removeAt(idx) })
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
        AppHeader(
            title = "Наши данные",
            subtitle = "Удерживайте ≡ для перемещения",
            showBack = true,
            onBack = onBack
        )

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
            if (isLoading && entries.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF1565C0)
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
                    itemsIndexed(entries, key = { idx, item -> "$idx:${item.key}" }) { idx, entry ->
                        ReorderableItem(reorderState, key = "$idx:${entry.key}") { isDragging ->
                            DataEntryCard(
                                entry = entry,
                                isDragging = isDragging,
                                dragHandleModifier = Modifier.longPressDraggableHandle(),
                                onEdit = { editingIndex = idx },
                                onDelete = { deletingIndex = idx },
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("", entry.value))
                                    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                                },
                                onShare = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${entry.key}: ${entry.value}")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Отправить"))
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
                    containerColor = Color(0xFF1565C0)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DataEntryCard(
    entry: DataEntry,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(if (isDragging) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.key,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = Color(0xFF9E9E9E)
                )
                Text(
                    entry.value,
                    fontSize = 16.sp,
                    color = Color(0xFF1C1B1F),
                    fontWeight = FontWeight.Medium
                )
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Копировать", tint = Color(0xFF9E9E9E), modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onShare, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Отправить", tint = Color(0xFF5E35B1), modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = Color(0xFF1565C0), modifier = Modifier.size(17.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color(0xFFB00020), modifier = Modifier.size(17.dp))
            }
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun DataEntryEditDialog(
    initial: DataEntry,
    onConfirm: (DataEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf(initial.key) }
    var value by remember { mutableStateOf(initial.value) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Запись", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Название (что это)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Значение") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Button(
                        onClick = { onConfirm(DataEntry(key.trim(), value.trim())) },
                        enabled = key.isNotBlank() && value.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) { Text("Сохранить", color = Color.White) }
                }
            }
        }
    }
}
