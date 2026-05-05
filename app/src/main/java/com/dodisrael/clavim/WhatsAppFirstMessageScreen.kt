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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppFirstMessageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    var startMillis by remember { mutableStateOf<Long?>(null) }
    var endMillis by remember { mutableStateOf<Long?>(null) }
    var dogCount by remember { mutableStateOf(1) }

    val whatsappOptions = remember {
        buildList {
            if (isAppInstalled(context, "com.whatsapp"))     add("com.whatsapp"     to "WhatsApp (Израиль)")
            if (isAppInstalled(context, "com.whatsapp.w4b")) add("com.whatsapp.w4b" to "WhatsApp Business (Россия)")
        }.ifEmpty { listOf("com.whatsapp" to "WhatsApp (Израиль)") }
    }
    var selectedApp by remember { mutableStateOf(whatsappOptions.first().first) }

    var contacts by remember { mutableStateOf<List<WhatsAppContact>>(emptyList()) }
    var contactSearch by remember { mutableStateOf("") }
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

    val startStr = startMillis?.let { formatDate(it) } ?: ""
    val endStr   = endMillis?.let   { formatDate(it) } ?: ""

    val days = if (startMillis != null && endMillis != null)
        ((endMillis!! - startMillis!!) / 86_400_000L).toInt() else null

    fun daysWord(n: Int) = when {
        n % 100 in 11..19 -> "дней"
        n % 10 == 1       -> "день"
        n % 10 in 2..4    -> "дня"
        else              -> "дней"
    }

    val daysLabel = days?.let { " ($it ${daysWord(it)})" } ?: ""

    val pricePerDay = when (dogCount) { 2 -> 180; 3 -> 240; else -> 120 }
    val dogsSuffix  = if (dogCount >= 2) " за $dogCount собаки" else ""

    val message = buildString {
        appendLine("${getGreeting()}!")
        appendLine("Это Ольга, по поводу передержки.")
        appendLine()
        appendLine("Вы записаны на передержку с $startStr по $endStr$daysLabel.")
        appendLine()
        appendLine("Стоимость — $pricePerDay шекелей в сутки$dogsSuffix (корм предоставляется вами).")
        appendLine("Прогулки и кормление — по вашему режиму.")
        appendLine("Ежедневно высылаю фотоотчёт.")
        appendLine()
        appendLine("Пожалуйста, убедитесь, что собака привита от бешенства.")
        appendLine("Если есть особенности ухода, аллергии или другие важные моменты — обязательно сообщите заранее в письменном виде.")
        appendLine()
        appendLine("Желательно предварительно встретиться 1–2 раза для знакомства — это поможет снизить стресс у собаки.")
        appendLine()
        appendLine("Адрес:")
        appendLine("Кирьят Хаим, ул. Хома у-Мигдаль 59, кв. 4")
        appendLine("Навигатор Waze:")
        appendLine("https://waze.com/ul/hsvbfwpncv")
        appendLine()
        append("Позвонить: tel:+972506072764")
    }

    val canSend = startMillis != null && endMillis != null && selectedContact != null
    val showPreview = startMillis != null && endMillis != null

    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startMillis = dateRangePickerState.selectedStartDateMillis
                        endMillis   = dateRangePickerState.selectedEndDateMillis
                        showDateRangePicker = false
                    },
                    enabled = dateRangePickerState.selectedStartDateMillis != null &&
                              dateRangePickerState.selectedEndDateMillis   != null
                ) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { showDateRangePicker = false }) { Text("Отмена") } }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.heightIn(max = 500.dp),
                headline = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Дата с", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                dateRangePickerState.selectedStartDateMillis?.let { formatDate(it) } ?: "—",
                                fontSize = 20.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text("–", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Дата по", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                dateRangePickerState.selectedEndDateMillis?.let { formatDate(it) } ?: "—",
                                fontSize = 20.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Первое сообщение",
            subtitle = "Передержка — информация для клиента",
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Период передержки", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF757575))
                    if (startMillis != null && endMillis != null) {
                        Text(
                            "с $startStr по $endStr$daysLabel",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Button(
                        onClick = { showDateRangePicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.size(8.dp))
                        Text(if (startMillis == null) "Выбрать даты" else "Изменить даты", color = Color.White)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Количество собак", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF757575))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { n ->
                            val selected = dogCount == n
                            val price = when (n) { 2 -> 180; 3 -> 240; else -> 120 }
                            Button(
                                onClick = { dogCount = n },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Color(0xFF1565C0) else Color(0xFFF5F5F5),
                                    contentColor   = if (selected) Color.White else Color(0xFF424242)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$n ${if (n == 1) "собака" else "собаки"}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("$price ₪/день", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

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
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1565C0)),
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

            if (showPreview) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Текст сообщения", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF1565C0))
                        Spacer(Modifier.height(8.dp))
                        Text(message, fontSize = 14.sp, color = Color(0xFF1C1B1F))
                    }
                }
            }

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
