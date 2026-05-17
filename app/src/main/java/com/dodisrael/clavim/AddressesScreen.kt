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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.json.JSONArray
import org.json.JSONObject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class AddressEntry(val name: String, val hebrew: String, val russian: String)

private val DEFAULT_ADDRESSES = listOf(
    AddressEntry("Вера", "חיפה, דניה 14", "Хайфа ул. Дения 14"),
    AddressEntry("Вита", "קרית ביאליק, דרך עכו 28", "Кирьят Бялик, дерех Акко 28"),
    AddressEntry("Женя", "נחל פולג, 5 ,דירה 9 , צור יצחק", ""),
    AddressEntry("Кузя", "ראשון לציון הכובש 26", "Ришон ле цион ха ковеш 26"),
    AddressEntry("МВД Акко", "שלום הגליל 1", "Шалом ha Глиль 1"),
    AddressEntry("Кирьят Моцкин\nПочта и Банк Леуми", "קרית מולצקין משה גושן 90", "Моше Гошен 90"),
    AddressEntry("Маккаби, доктор Авербах", "קרית מוצקין קדיש לוז 11", "Кирьят Моцкин Кадиш Люз 11")
)

private fun loadAddresses(prefs: android.content.SharedPreferences): List<AddressEntry> {
    val json = prefs.getString("addresses", null) ?: return DEFAULT_ADDRESSES
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            AddressEntry(obj.optString("name"), obj.optString("hebrew"), obj.optString("russian"))
        }
    } catch (_: Exception) { DEFAULT_ADDRESSES }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddressesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("clavim_prefs", Context.MODE_PRIVATE) }

    var addresses by remember { mutableStateOf(loadAddresses(prefs)) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var deletingIndex by remember { mutableStateOf<Int?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newList = addresses.toMutableList().apply { add(to.index, removeAt(from.index)) }
        addresses = newList
        saveAddresses(prefs, newList)
    }

    if (editingIndex != null || showAddDialog) {
        val initial = editingIndex?.let { addresses[it] } ?: AddressEntry("", "", "")
        AddressEditDialog(
            initial = initial,
            onConfirm = { updated ->
                val newList = if (editingIndex != null)
                    addresses.toMutableList().also { it[editingIndex!!] = updated }
                else
                    addresses + updated
                addresses = newList
                saveAddresses(prefs, newList)
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
                        val newList = addresses.toMutableList().also { it.removeAt(idx) }
                        addresses = newList
                        saveAddresses(prefs, newList)
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
            title = "Адреса",
            subtitle = "Удерживайте ≡ для перемещения",
            showBack = true,
            onBack = onBack
        )
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(addresses, key = { _, item -> item.name + item.hebrew }) { idx, addr ->
                    ReorderableItem(reorderState, key = addr.name + addr.hebrew) { isDragging ->
                        AddressCard(
                            entry = addr,
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
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = Color(0xFF0D47A1)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить", tint = Color.White)
            }
        }
    }
}

@Composable
private fun AddressCard(
    entry: AddressEntry,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onWaze: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(if (isDragging) 8.dp else 2.dp)
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
                IconButton(onClick = onWaze, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Navigation, contentDescription = "Waze", tint = Color(0xFF33CCFF), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF0D47A1), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFB00020), modifier = Modifier.size(18.dp))
                }
            }
            if (entry.hebrew.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.hebrew,
                    fontSize = 14.sp,
                    color = Color(0xFF1C1B1F),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (entry.russian.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.russian,
                    fontSize = 13.sp,
                    color = Color(0xFF757575)
                )
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
