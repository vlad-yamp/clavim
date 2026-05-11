package com.dodisrael.clavim

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppPickupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var endDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    val whatsappOptions = remember {
        buildList {
            if (isAppInstalled(context, "com.whatsapp"))     add("com.whatsapp"     to "WhatsApp (Израиль)")
            if (isAppInstalled(context, "com.whatsapp.w4b")) add("com.whatsapp.w4b" to "WhatsApp Business (Россия)")
        }.ifEmpty { listOf("com.whatsapp" to "WhatsApp (Израиль)") }
    }
    var selectedApp by remember { mutableStateOf(whatsappOptions.first().first) }

    var contacts        by remember { mutableStateOf<List<WhatsAppContact>>(emptyList()) }
    var contactSearch   by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<WhatsAppContact?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { contacts = readContacts(context) }
    }
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            contacts = readContacts(context)
        } else {
            permLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val filteredContacts = remember(contactSearch, contacts) {
        if (contactSearch.isBlank()) emptyList()
        else contacts.filter { it.name.contains(contactSearch.trim(), ignoreCase = true) }.take(15)
    }

    val datePhrase = endDateMillis?.let { pickupDatePhrase(it) } ?: ""
    val greeting = getGreeting()
    val message = if (endDateMillis != null)
        "$greeting, $datePhrase заканчивается передержка, во сколько Вас ждать?"
    else ""

    val canSend = endDateMillis != null && selectedContact != null

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endDateMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } }
        ) {
            DatePicker(
                state = datePickerState,
                modifier = Modifier.heightIn(max = 500.dp)
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Когда заберёте?",
            subtitle = "Напоминание о конце передержки",
            showBack = true,
            onBack = onBack
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Date card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Дата окончания передержки", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF757575))
                    if (endDateMillis != null) {
                        Text(formatDate(endDateMillis!!), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = { showDatePicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F))
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.size(8.dp))
                        Text(if (endDateMillis == null) "Выбрать дату" else "Изменить дату", color = Color.White)
                    }
                }
            }

            // WhatsApp account selector
            if (whatsappOptions.size > 1) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Аккаунт WhatsApp", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF757575))
                        Spacer(Modifier.height(4.dp))
                        whatsappOptions.forEach { (pkg, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedApp = pkg }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedApp == pkg, onClick = { selectedApp = pkg })
                                Text(label, modifier = Modifier.padding(start = 4.dp), fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            // Contact selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Получатель", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF757575))
                    if (selectedContact != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(selectedContact!!.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text(selectedContact!!.phone, fontSize = 13.sp, color = Color(0xFF757575))
                            }
                            IconButton(onClick = { selectedContact = null; contactSearch = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Сбросить")
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = contactSearch,
                            onValueChange = { contactSearch = it },
                            placeholder = { Text("Поиск по имени") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (contactSearch.isNotEmpty()) {
                                    IconButton(onClick = { contactSearch = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        when {
                            contacts.isEmpty() ->
                                Text("Нет доступа к контактам", fontSize = 13.sp, color = Color(0xFF9E9E9E))
                            contactSearch.isNotBlank() && filteredContacts.isEmpty() ->
                                Text("Контакты не найдены", fontSize = 13.sp, color = Color(0xFF9E9E9E))
                            filteredContacts.isNotEmpty() -> Column {
                                filteredContacts.forEach { contact ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedContact = contact; contactSearch = "" }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF00838F)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Column {
                                            Text(contact.name, fontSize = 15.sp)
                                            Text(contact.phone, fontSize = 12.sp, color = Color(0xFF9E9E9E))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Message preview
            if (endDateMillis != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F7FA)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Текст сообщения", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF00695C))
                        Spacer(Modifier.height(8.dp))
                        Text(message, fontSize = 14.sp, color = Color(0xFF1C1B1F))
                    }
                }
            }

            // Send button
            Button(
                onClick = {
                    val contact = selectedContact ?: return@Button
                    val phone = contact.phone.replace("+", "").replace(Regex("\\D"), "")
                    val encoded = Uri.encode(message)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=$encoded"))
                    intent.setPackage(selectedApp)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=$encoded")))
                    }
                },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                Spacer(Modifier.size(8.dp))
                Text("Перейти в WhatsApp", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun pickupDatePhrase(millis: Long): String {
    val dateStr = SimpleDateFormat("d MMMM", Locale("ru")).format(Date(millis))
    val selected = Calendar.getInstance().apply { timeInMillis = millis }
    val today    = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    return when {
        sameDay(selected, today)    -> "сегодня $dateStr"
        sameDay(selected, tomorrow) -> "завтра $dateStr"
        else                        -> dateStr
    }
}
