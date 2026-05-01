package com.dodisrael.clavim

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val TEAL = Color(0xFF00796B)
private val TEAL_LIGHT = Color(0xFFE0F2F1)
private val TEAL_XLIGHT = Color(0xFFE8F5E9)

@Composable
fun WhatsAppTranslationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("clavim_prefs", Context.MODE_PRIVATE) }

    var apiKey by remember { mutableStateOf(prefs.getString("openai_api_key", "") ?: "") }
    var showApiKeyDialog by remember { mutableStateOf(false) }

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
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
            contacts = readContacts(context)
        else
            permLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    val filteredContacts = remember(contactSearch, contacts) {
        if (contactSearch.isBlank()) emptyList()
        else contacts.filter { it.name.contains(contactSearch.trim(), ignoreCase = true) }.take(15)
    }

    val messages = remember { mutableStateListOf<TranslationMessage>() }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    var selectedTab by remember { mutableStateOf(0) }

    var incomingOriginal   by remember { mutableStateOf("") }
    var incomingTranslated by remember { mutableStateOf("") }
    var isTranslatingIn    by remember { mutableStateOf(false) }

    var outgoingText       by remember { mutableStateOf("") }
    var outgoingTranslated by remember { mutableStateOf("") }
    var isTranslatingOut   by remember { mutableStateOf(false) }

    var translateError by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) outgoingText = spoken
        }
    }

    if (showApiKeyDialog) {
        OpenAiKeyDialog(
            currentKey = apiKey,
            onDismiss = { showApiKeyDialog = false },
            onSave = { key ->
                apiKey = key
                prefs.edit().putString("openai_api_key", key).apply()
                showApiKeyDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Переписка с переводом",
            subtitle = "Иврит ↔ Русский",
            showBack = true,
            onBack = onBack
        )

        if (selectedContact == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (apiKey.isBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("API ключ OpenAI не задан", fontSize = 14.sp, color = Color(0xFFE65100), modifier = Modifier.weight(1f))
                            TextButton(onClick = { showApiKeyDialog = true }) { Text("Ввести ключ") }
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
                                    modifier = Modifier.fillMaxWidth().clickable { selectedApp = pkg }.padding(vertical = 2.dp),
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
                        Text("Выберите собеседника", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF757575))
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
                                            modifier = Modifier.size(36.dp).clip(CircleShape).background(TEAL),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                                color = Color.White, fontWeight = FontWeight.Bold
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Contact info bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TEAL_LIGHT)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedContact!!.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TEAL)
                        Text(selectedContact!!.phone, fontSize = 12.sp, color = Color(0xFF757575))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showApiKeyDialog = true }) {
                            Text(
                                "API ключ",
                                fontSize = 11.sp,
                                color = if (apiKey.isBlank()) Color(0xFFE65100) else Color(0xFF9E9E9E)
                            )
                        }
                        TextButton(onClick = { selectedContact = null }) {
                            Text("Сменить", fontSize = 12.sp, color = TEAL)
                        }
                    }
                }

                // Message history
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "История переписки пуста.\nВставьте входящее сообщение\nили введите исходящее.",
                                    fontSize = 14.sp,
                                    color = Color(0xFF9E9E9E),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    items(messages) { msg -> TranslationBubble(msg) }
                }

                HorizontalDivider(color = Color(0xFFE0E0E0))

                // Input panels
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFAFAFA))
                ) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color(0xFFFAFAFA),
                        contentColor = TEAL
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0; translateError = null },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("Входящее", fontSize = 13.sp)
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1; translateError = null },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("Исходящее", fontSize = 13.sp)
                                }
                            }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedTab == 0) {
                            // Incoming: paste Hebrew → translate to Russian
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val pasted = cb.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                        if (pasted.isBlank()) return@Button
                                        incomingOriginal = pasted
                                        incomingTranslated = ""
                                        translateError = null
                                        if (apiKey.isBlank()) { showApiKeyDialog = true; return@Button }
                                        scope.launch {
                                            isTranslatingIn = true
                                            try {
                                                incomingTranslated = translateText(pasted, "иврита", "русский", apiKey)
                                            } catch (e: Exception) {
                                                translateError = "Ошибка: ${e.message}"
                                            }
                                            isTranslatingIn = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TEAL),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Вставить и перевести", color = Color.White, fontSize = 13.sp)
                                }
                                if (isTranslatingIn) {
                                    CircularProgressIndicator(modifier = Modifier.size(26.dp), color = TEAL, strokeWidth = 2.dp)
                                }
                            }

                            if (incomingOriginal.isNotBlank()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = TEAL_XLIGHT),
                                    elevation = CardDefaults.cardElevation(0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Оригинал (иврит):", fontSize = 11.sp, color = Color(0xFF757575), fontWeight = FontWeight.SemiBold)
                                        Text(incomingOriginal, fontSize = 13.sp, color = Color(0xFF1C1B1F))
                                        if (incomingTranslated.isNotBlank()) {
                                            HorizontalDivider(color = Color(0xFFB2DFDB), modifier = Modifier.padding(vertical = 2.dp))
                                            Text("Перевод (русский):", fontSize = 11.sp, color = Color(0xFF757575), fontWeight = FontWeight.SemiBold)
                                            Text(incomingTranslated, fontSize = 13.sp, color = Color(0xFF1C1B1F))
                                            Button(
                                                onClick = {
                                                    messages.add(TranslationMessage(true, incomingOriginal, incomingTranslated))
                                                    incomingOriginal = ""
                                                    incomingTranslated = ""
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = TEAL)
                                            ) {
                                                Text("Добавить в историю", color = Color.White, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Outgoing: type Russian → translate to Hebrew
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = outgoingText,
                                    onValueChange = { outgoingText = it; outgoingTranslated = "" },
                                    placeholder = { Text("Введите сообщение на русском...", fontSize = 13.sp) },
                                    modifier = Modifier.weight(1f),
                                    minLines = 4,
                                    maxLines = 5,
                                    textStyle = TextStyle(fontSize = 13.sp)
                                )
                                IconButton(
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите...")
                                        }
                                        try { speechLauncher.launch(intent) } catch (_: ActivityNotFoundException) {}
                                    },
                                    modifier = Modifier.size(44.dp).background(TEAL, CircleShape)
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Голосовой ввод", tint = Color.White)
                                }
                            }

                            Button(
                                onClick = {
                                    if (apiKey.isBlank()) { showApiKeyDialog = true; return@Button }
                                    val text = outgoingText.trim()
                                    if (text.isBlank()) return@Button
                                    scope.launch {
                                        isTranslatingOut = true
                                        translateError = null
                                        try {
                                            outgoingTranslated = translateText(text, "русского", "иврит", apiKey)
                                        } catch (e: Exception) {
                                            translateError = "Ошибка: ${e.message}"
                                        }
                                        isTranslatingOut = false
                                    }
                                },
                                enabled = outgoingText.isNotBlank() && !isTranslatingOut,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                            ) {
                                if (isTranslatingOut) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.size(6.dp))
                                }
                                Text("Перевести на иврит", color = Color.White, fontSize = 13.sp)
                            }

                            if (outgoingTranslated.isNotBlank()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                                    elevation = CardDefaults.cardElevation(0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Перевод на иврит:", fontSize = 11.sp, color = Color(0xFF757575), fontWeight = FontWeight.SemiBold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(outgoingTranslated, fontSize = 13.sp, color = Color(0xFF1C1B1F), modifier = Modifier.weight(1f))
                                            IconButton(
                                                onClick = {
                                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cb.setPrimaryClip(ClipData.newPlainText("Hebrew", outgoingTranslated))
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Копировать", tint = Color(0xFF1565C0), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    messages.add(TranslationMessage(false, outgoingText.trim(), outgoingTranslated))
                                                    outgoingText = ""
                                                    outgoingTranslated = ""
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = TEAL)
                                            ) { Text("В историю", color = Color.White, fontSize = 12.sp) }

                                            Button(
                                                onClick = {
                                                    val phone = selectedContact!!.phone.replace("+", "").replace(Regex("\\D"), "")
                                                    val encoded = Uri.encode(outgoingTranslated)
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=$encoded"))
                                                    intent.setPackage(selectedApp)
                                                    try { context.startActivity(intent) }
                                                    catch (_: ActivityNotFoundException) {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=$encoded")))
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.size(4.dp))
                                                Text("WhatsApp", color = Color.White, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (translateError != null) {
                            Text(translateError!!, color = Color(0xFFB00020), fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val phone = selectedContact!!.phone.replace("+", "").replace(Regex("\\D"), "")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone"))
                                intent.setPackage(selectedApp)
                                try { context.startActivity(intent) }
                                catch (_: ActivityNotFoundException) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone")))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Открыть WhatsApp →", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationBubble(msg: TranslationMessage) {
    val bubbleColor = if (msg.isIncoming) TEAL_XLIGHT else Color(0xFFE3F2FD)
    val labelColor  = if (msg.isIncoming) Color(0xFF2E7D32) else Color(0xFF1565C0)
    val alignment   = if (msg.isIncoming) Alignment.Start else Alignment.End

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Card(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(
                topStart    = if (msg.isIncoming) 4.dp else 12.dp,
                topEnd      = if (msg.isIncoming) 12.dp else 4.dp,
                bottomStart = 12.dp,
                bottomEnd   = 12.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (msg.isIncoming) "Входящее (иврит → рус)" else "Исходящее (рус → иврит)",
                    fontSize = 10.sp, color = labelColor, fontWeight = FontWeight.SemiBold
                )
                Text(msg.originalText, fontSize = 13.sp, color = Color(0xFF1C1B1F))
                HorizontalDivider(color = Color(0xFFB0BEC5), modifier = Modifier.padding(vertical = 2.dp))
                Text(msg.translatedText, fontSize = 13.sp, color = Color(0xFF455A64), fontWeight = FontWeight.Medium)
            }
        }
    }
}

private suspend fun translateText(text: String, fromLang: String, toLang: String, apiKey: String): String =
    withContext(Dispatchers.IO) {
        val conn = (URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 20000
            readTimeout    = 20000
        }

        val prompt = "Переведи следующий текст с $fromLang на $toLang. Верни только перевод без пояснений.\n\n$text"
        val escaped = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val body = """{"model":"gpt-4o-mini","messages":[{"role":"user","content":"$escaped"}],"max_tokens":600,"temperature":0.3}"""
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val response = if (code == 200) {
            conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } else {
            val err = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: "HTTP $code"
            throw Exception("OpenAI $code: $err")
        }

        JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }
