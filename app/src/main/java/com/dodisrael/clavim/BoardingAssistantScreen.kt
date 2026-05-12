package com.dodisrael.clavim

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView

private const val SHEET_CSV_URL =
    "https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/export?format=csv&gid=0"
private const val SHEET_XLSX_URL =
    "https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/export?format=xlsx&gid=0"

@Composable
fun BoardingAssistantScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("clavim_prefs", Context.MODE_PRIVATE) }

    var apiKey by remember { mutableStateOf(prefs.getString("openai_api_key", "") ?: "") }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    var question by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var isSpeaking by remember { mutableStateOf(false) }
    var answerWebViewHeight by remember { mutableStateOf(100.dp) }

    // TextToSpeech lifecycle
    val ttsHolder = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = ttsHolder.value?.setLanguage(Locale("ru", "RU"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsHolder.value?.setLanguage(Locale.getDefault())
                }
            }
        }
        ttsHolder.value = tts
        onDispose {
            tts.stop()
            tts.shutdown()
            ttsHolder.value = null
        }
    }

    fun speak(text: String) {
        val tts = ttsHolder.value ?: return
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                Handler(Looper.getMainLooper()).post { isSpeaking = true }
            }
            override fun onDone(id: String?) {
                Handler(Looper.getMainLooper()).post { isSpeaking = false }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                Handler(Looper.getMainLooper()).post { isSpeaking = false }
            }
        })
        val plain = android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
        tts.speak(plain, TextToSpeech.QUEUE_FLUSH, null, "boarding_answer")
    }

    fun stopSpeaking() {
        ttsHolder.value?.stop()
        isSpeaking = false
    }

    fun askQuestion() {
        if (apiKey.isBlank()) { showApiKeyDialog = true; return }
        if (question.isBlank()) return
        scope.launch {
            isLoading = true
            errorText = ""
            answer = ""
            try {
                loadingStatus = "Загружаю данные таблицы..."
                val (csv, merges) = coroutineScope {
                    val csvJob    = async { fetchSheetCsv() }
                    val mergesJob = async { fetchSheetMerges() }
                    csvJob.await() to mergesJob.await()
                }
                val year = Calendar.getInstance().get(Calendar.YEAR)
                val csvRows     = csv.lines().filter { it.isNotBlank() }.map { parseCsvLine(it) }
                val allRows     = buildGridWithMerges(csvRows, merges)
                val filteredRows = filterRowsByDateWindow(allRows)
                val sheetText   = filteredRows.joinToString("\n") { row -> row.joinToString(" | ") }

                loadingStatus = "Спрашиваю ChatGPT..."
                answer = askBoardingAssistant(question, sheetText, apiKey, year)
            } catch (e: Exception) {
                errorText = "Ошибка: ${e.message}"
            }
            isLoading = false
            loadingStatus = ""
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                question = spoken
                answer = ""
                errorText = ""
                if (apiKey.isNotBlank()) askQuestion()
            }
        }
    }

    fun launchSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Задайте вопрос про передержку...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { speechLauncher.launch(intent) } catch (_: ActivityNotFoundException) {}
    }

    if (showApiKeyDialog) {
        OpenAiKeyDialog(
            currentKey = apiKey,
            onDismiss = { showApiKeyDialog = false },
            onSave = { key ->
                apiKey = key
                prefs.edit().putString("openai_api_key", key).apply()
                showApiKeyDialog = false
            },
            title = "API ключ OpenAI",
            hint = "Ключ начинается с sk-... Он сохраняется только на вашем устройстве.",
            placeholder = "sk-..."
        )
    }

    val accentColor = Color(0xFF5E35B1)

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Спросить про передержку",
            subtitle = "Голосовой ассистент",
            showBack = true,
            onBack = {
                stopSpeaking()
                onBack()
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // API key warning
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
                        Text(
                            "API ключ OpenAI не задан",
                            fontSize = 14.sp,
                            color = Color(0xFFE65100),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showApiKeyDialog = true }) { Text("Ввести ключ") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Mic button with pulse animation when loading
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isLoading) 1.12f else 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(if (isLoading) accentColor.copy(alpha = 0.4f) else accentColor),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { if (!isLoading) launchSpeech() },
                    modifier = Modifier.size(140.dp),
                    shape = CircleShape,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    elevation = null
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Задать вопрос голосом",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            if (!isLoading && answer.isBlank() && errorText.isBlank()) {
                Text(
                    if (question.isBlank()) "Нажмите и задайте вопрос голосом"
                    else "Нажмите ещё раз для нового вопроса",
                    fontSize = 15.sp,
                    color = Color(0xFF757575),
                    textAlign = TextAlign.Center
                )
            }

            // Recognized question card
            if (question.isNotBlank() && !isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ваш вопрос", fontSize = 12.sp, color = Color(0xFF7E57C2), fontWeight = FontWeight.SemiBold)
                        Text(question, fontSize = 15.sp, color = Color(0xFF1C1B1F))
                        if (answer.isBlank() && errorText.isBlank() && apiKey.isNotBlank()) {
                            Button(
                                onClick = { askQuestion() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Text("Спросить", color = Color.White)
                            }
                        }
                    }
                }
            }

            // Loading indicator
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = accentColor, strokeWidth = 3.dp)
                    Text(loadingStatus, fontSize = 14.sp, color = Color(0xFF757575), textAlign = TextAlign.Center)
                }
            }

            // Error card
            if (errorText.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Не удалось получить ответ", fontSize = 13.sp, color = Color(0xFFB71C1C), fontWeight = FontWeight.SemiBold)
                        Text(errorText, fontSize = 13.sp, color = Color(0xFFC62828))
                    }
                }
            }

            // Answer card
            if (answer.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Ответ ассистента",
                            fontSize = 12.sp,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    setBackgroundColor(AndroidColor.TRANSPARENT)
                                    isScrollContainer = false
                                    overScrollMode = View.OVER_SCROLL_NEVER
                                    settings.javaScriptEnabled = true
                                    settings.defaultFontSize = 15
                                    settings.defaultTextEncodingName = "UTF-8"
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView, url: String) {
                                            view.evaluateJavascript(
                                                "(function(){return document.body.scrollHeight;})()"
                                            ) { result ->
                                                val h = result?.toFloatOrNull() ?: 0f
                                                if (h > 0f) answerWebViewHeight = h.dp
                                            }
                                        }
                                    }
                                }
                            },
                            update = { wv ->
                                wv.loadDataWithBaseURL(null, wrapInHtml(answer), "text/html", "UTF-8", null)
                            },
                            modifier = Modifier.fillMaxWidth().height(answerWebViewHeight)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { speak(answer) },
                                modifier = Modifier.weight(1f),
                                enabled = !isSpeaking
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(if (isSpeaking) "Читает..." else "Прослушать")
                            }
                            Button(
                                onClick = {
                                    stopSpeaking()
                                    question = ""
                                    answer = ""
                                    errorText = ""
                                    launchSpeech()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("Новый вопрос", color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // API key management link
            if (apiKey.isNotBlank()) {
                TextButton(onClick = { showApiKeyDialog = true }) {
                    Text("Изменить API ключ OpenAI", fontSize = 12.sp, color = Color(0xFFBDBDBD))
                }
            }
        }
    }
}

private suspend fun fetchSheetCsv(): String = withContext(Dispatchers.IO) {
    val conn = URL(SHEET_CSV_URL).openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 15_000
    try {
        if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
        conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    } finally {
        conn.disconnect()
    }
}

private fun parseCsvLine(line: String): MutableList<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' && !inQuotes -> inQuotes = true
            c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                current.append('"'); i++
            }
            c == '"' && inQuotes -> inQuotes = false
            c == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
            else -> current.append(c)
        }
        i++
    }
    result.add(current.toString())
    return result
}

private val RUSSIAN_MONTHS = mapOf(
    "января" to 1, "февраля" to 2, "марта" to 3, "апреля" to 4,
    "мая" to 5, "июня" to 6, "июля" to 7, "августа" to 8,
    "сентября" to 9, "октября" to 10, "ноября" to 11, "декабря" to 12
)

// Finds today's date scanning from the BOTTOM (most recent year section),
// then returns rows in the window [today-14 .. today+180 days by row count].
// This avoids grabbing previous years' data when dates repeat without a year.
private fun filterRowsByDateWindow(rows: List<List<String>>): List<List<String>> {
    val today      = Calendar.getInstance()
    val todayMonth = today.get(Calendar.MONTH) + 1
    val todayDay   = today.get(Calendar.DAY_OF_MONTH)

    // Scan from bottom: keep the bottom-most row whose (month, day) is closest to today
    var anchorIdx = rows.size - 1
    var minDist   = Int.MAX_VALUE

    for (i in rows.indices.reversed()) {
        val parts = rows[i].getOrElse(0) { "" }.trim().lowercase().split(Regex("\\s+"))
        if (parts.size < 2) continue
        val day   = parts[0].toIntOrNull() ?: continue
        val month = RUSSIAN_MONTHS[parts[1]] ?: continue
        val dist  = kotlin.math.abs((month - todayMonth) * 31 + (day - todayDay))
        if (dist < minDist) { minDist = dist; anchorIdx = i }
        if (dist == 0) break  // exact match — stop, this is the most recent occurrence
    }

    val start = maxOf(0, anchorIdx - 14)
    val end   = minOf(rows.size, anchorIdx + 181)
    return rows.subList(start, end)
}

private data class MergeRange(val startRow: Int, val startCol: Int, val endRow: Int, val endCol: Int)

private suspend fun fetchSheetMerges(): List<MergeRange> = withContext(Dispatchers.IO) {
    val conn = URL(SHEET_XLSX_URL).openConnection() as HttpURLConnection
    conn.connectTimeout = 20_000
    conn.readTimeout    = 30_000
    try {
        if (conn.responseCode != 200) return@withContext emptyList()
        extractMergeCells(conn.inputStream.readBytes())
    } catch (_: Exception) {
        emptyList()
    } finally {
        conn.disconnect()
    }
}

// Unzips XLSX and extracts <mergeCell ref="..."/> entries from the first worksheet
private fun extractMergeCells(xlsxBytes: ByteArray): List<MergeRange> {
    val merges = mutableListOf<MergeRange>()
    val pattern = Regex("""<mergeCell ref="([A-Z]+)(\d+):([A-Z]+)(\d+)"""")
    ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zip ->
        generateSequence { zip.nextEntry }.forEach { entry ->
            if (entry.name == "xl/worksheets/sheet1.xml") {
                val xml = zip.readBytes().toString(Charsets.UTF_8)
                for (m in pattern.findAll(xml)) {
                    merges.add(MergeRange(
                        startRow = m.groupValues[2].toInt() - 1,  // XLSX rows are 1-based
                        startCol = colLetterToIndex(m.groupValues[1]),
                        endRow   = m.groupValues[4].toInt() - 1,
                        endCol   = colLetterToIndex(m.groupValues[3])
                    ))
                }
            }
        }
    }
    return merges
}

// "A"->0, "B"->1, "AA"->26, etc.
private fun colLetterToIndex(letters: String): Int {
    var index = 0
    for (c in letters) index = index * 26 + (c - 'A' + 1)
    return index - 1
}

// Applies exact fill-down using XLSX merge boundaries instead of heuristics
private fun buildGridWithMerges(csvRows: List<List<String>>, merges: List<MergeRange>): List<List<String>> {
    if (csvRows.isEmpty()) return emptyList()
    val maxCol = csvRows.maxOf { it.size }
    val grid = csvRows.map { row ->
        val r = row.toMutableList()
        repeat(maxCol - r.size) { r.add("") }
        r
    }.toMutableList()

    for (merge in merges) {
        val startRow = merge.startRow
        val endRow   = minOf(merge.endRow, grid.size - 1)
        if (startRow < 0 || startRow >= grid.size) continue
        val value = grid[startRow].getOrElse(merge.startCol) { "" }
        if (value.isBlank()) continue
        // Fill from the second row of the merge to the last
        for (rowIdx in (startRow + 1)..endRow) {
            val row = grid[rowIdx]
            while (row.size <= merge.endCol) row.add("")
            for (col in merge.startCol..merge.endCol) row[col] = value
        }
    }
    return grid
}

private suspend fun askBoardingAssistant(question: String, sheetData: String, apiKey: String, year: Int): String =
    withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date())

        val systemPrompt = """Ты помощник службы передержки собак "DogIsrael" в Израиле. Тебе предоставлены данные из таблицы Google Sheets с записями на передержку.

Структура таблицы (CSV, разделитель " | "):
- Столбец A: Дата (день и месяц). Год — всегда $year.
- Столбец B: День недели.
- Столбец C: Количество занятых мест (сколько собак сейчас на передержке).
- Столбец D: Количество свободных мест (сколько ещё можно принять). Это уже готовое число, ничего вычислять не нужно.
- Столбцы E, F, …: Каждый столбец — отдельная передержка. Значение ячейки: "Кличка собаки, порода, хозяин, телефон". Значение повторяется на всех строках периода передержки. Пустая ячейка — в этот день слот свободен. При выводе информации о собаках телефон НЕ показывать.

Сегодня: $today.

Данные таблицы:
$sheetData

Правила ответа:
- Отвечай подробно и по существу, на русском языке.
- Если спрашивают о свободных местах: свободные места — это значение столбца D напрямую. Выведи таблицу с колонками: Дата | День | Занято (C) | Свободно (D). Вывод делай на основе колонки D.
- Если нет свободных мест, скажи об этом прямо и укажи, с какого дня освобождается место.
- Если данных по запрошенному периоду нет в таблице, сообщи об этом.
- Формат ответа: ТОЛЬКО HTML без тегов <html>/<head>/<body>. Используй <table><tr><th>/<td> для таблиц, <b> для ключевых данных (имена, числа, даты), <ul><li> для списков, <p> для абзацев. Не дублируй одно и то же в тексте и таблице одновременно."""

        val systemMsg = JSONObject().apply { put("role", "system"); put("content", systemPrompt) }
        val userMsg   = JSONObject().apply { put("role", "user");   put("content", question) }
        val messages  = JSONArray().apply { put(systemMsg); put(userMsg) }
        val body = JSONObject().apply {
            put("model", "gpt-4o")
            put("messages", messages)
            put("max_tokens", 1200)
            put("temperature", 0.2)
        }.toString()

        val conn = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 30_000
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val responseText = if (code == 200) {
            conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } else {
            val err = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: "HTTP $code"
            throw Exception("OpenAI $code: $err")
        }

        JSONObject(responseText)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }

private fun wrapInHtml(content: String): String = """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{font-family:sans-serif;font-size:15px;color:#1C1B1F;margin:0;padding:0;background:transparent;line-height:1.5}
table{border-collapse:collapse;width:100%;margin:8px 0;font-size:14px}
th,td{border:1px solid #C5CAE9;padding:6px 8px;text-align:left;vertical-align:top}
th{background-color:#5E35B1;color:white;font-weight:bold}
tr:nth-child(even) td{background-color:#EDE7F6}
h1,h2,h3{color:#5E35B1;margin:10px 0 4px;font-size:16px}
ul,ol{padding-left:20px;margin:4px 0}
b,strong{color:#311B92}
p{margin:6px 0}
</style>
</head>
<body>$content</body>
</html>"""
