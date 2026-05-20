package com.dodisrael.clavim

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView

private const val SHEET_CSV_URL =
    "https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/export?format=csv&gid=0"
private const val SHEET_XLSX_URL =
    "https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/export?format=xlsx&gid=0"
private const val CLIENTS_CSV_URL =
    "https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/export?format=csv&gid=1215152509"
private const val TRAINING_CSV_URL =
    "https://docs.google.com/spreadsheets/d/1k7usk6ZFkPL7x6-CFFfAr87kQvRkI9TBCZZqJFej0X4/export?format=csv&gid=0"

private enum class TableType { BOARDING, CLIENTS, TRAINING, PHOTOS, RECORD_BOOKING, NAVIGATION, DATA }

private data class ClassifyResult(
    val tableType: TableType,
    val dogName: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val action: String = "add",
    val clarification: String? = null,
    val newDogPresetName: String? = null,
    val newDogPresetBreed: String? = null,
    val newDogPresetOwner: String? = null
)

private data class PendingBooking(
    val dogName: String,
    val startDate: String,
    val endDate: String,
    val candidates: List<String>,
    val action: String = "add"
)

private data class ExistingDogBookingPending(
    val dogName: String,
    val startDate: String,
    val endDate: String,
    val clarification: String = "",
    val action: String = "add"
)

private data class NewDogBookingPending(
    val startDate: String,
    val endDate: String,
    val presetName: String = "",
    val presetBreed: String = "",
    val presetOwner: String = ""
)

@Composable
fun BoardingAssistantScreen(
    onBack: () -> Unit,
    onTelegramFosteringClick: () -> Unit = {},
    initialFormAction: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("clavim_prefs", Context.MODE_PRIVATE) }

    var apiKey by remember { mutableStateOf(prefs.getString("openai_api_key", "") ?: "") }
    val voiceAutoSpeak = remember { prefs.getBoolean("voice_auto_speak", true) }

    var question by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var isSpeaking by remember { mutableStateOf(false) }
    var voiceComment by remember { mutableStateOf("") }
    var answerWebViewHeight by remember { mutableStateOf(100.dp) }
    var fullScreenPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var galleryUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingBooking by remember { mutableStateOf<PendingBooking?>(null) }
    val fromMenu = remember { initialFormAction != null }

    var pendingExistingDogBooking by remember { mutableStateOf<ExistingDogBookingPending?>(
        when (initialFormAction) {
            "add"    -> ExistingDogBookingPending("", "", "")
            "delete" -> ExistingDogBookingPending("", "", "", "", "delete")
            else     -> null
        }
    ) }
    var existingDogDialogState by remember { mutableStateOf<Pair<ExistingDogBookingPending, String?>?>(null) }
    LaunchedEffect(pendingExistingDogBooking) {
        val pending = pendingExistingDogBooking ?: run { existingDogDialogState = null; return@LaunchedEffect }
        existingDogDialogState = null
        var photoUrl: String? = null
        if (pending.dogName.isNotBlank()) {
            try {
                val csv = withContext(Dispatchers.IO) { fetchCsv(CLIENTS_CSV_URL) }
                val dogs = parseClientDogs(csv)
                val entries = dogs.filter { it.dogName.equals(pending.dogName, ignoreCase = true) }
                if (entries.isNotEmpty()) {
                    val matchedEntry = entries.firstOrNull {
                        buildClarification(it.breed, it.ownerName) == pending.clarification
                    } ?: entries.first()
                    photoUrl = findBoardingPhoto(context, pending.dogName, matchedEntry.lastBoarding, entries.distinctBy { it.ownerName }.size, apiKey)
                }
            } catch (_: Exception) {}
        }
        existingDogDialogState = pending to photoUrl
    }
    var pendingNewDogBooking by remember { mutableStateOf<NewDogBookingPending?>(
        if (initialFormAction == "new") NewDogBookingPending("", "") else null
    ) }
    var shouldAutoLaunchMic by remember { mutableStateOf(false) }
    var bookingSuccess by remember { mutableStateOf(false) }
    var lastTableType by remember { mutableStateOf<TableType?>(null) }
    var wazeAddress by remember { mutableStateOf("") }
    val pendingTtsBytes = remember { mutableStateOf<ByteArray?>(null) }

    val mediaPlayerHolder = remember { mutableStateOf<MediaPlayer?>(null) }
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
            mediaPlayerHolder.value?.release()
            mediaPlayerHolder.value = null
            tts.stop()
            tts.shutdown()
            ttsHolder.value = null
        }
    }

    fun speakWithTts(text: String) {
        val tts = ttsHolder.value ?: return
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { Handler(Looper.getMainLooper()).post { isSpeaking = true } }
            override fun onDone(id: String?) { Handler(Looper.getMainLooper()).post { isSpeaking = false } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { Handler(Looper.getMainLooper()).post { isSpeaking = false } }
        })
        val plain = android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
        tts.speak(plain, TextToSpeech.QUEUE_FLUSH, null, "boarding_answer")
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        mediaPlayerHolder.value?.release()
        mediaPlayerHolder.value = null
        ttsHolder.value?.stop()

        if (apiKey.isBlank()) { speakWithTts(text); return }

        val preloaded = pendingTtsBytes.value
        pendingTtsBytes.value = null

        scope.launch {
            isSpeaking = true
            val plain = formatPhoneNumbers(android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim())
            val bytes = preloaded ?: withContext(Dispatchers.IO) { downloadTtsAudio(plain, apiKey) }
            if (bytes != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val file = java.io.File(context.cacheDir, "tts_voice.mp3")
                        file.writeBytes(bytes)
                        val player = MediaPlayer()
                        player.setDataSource(file.absolutePath)
                        player.setOnCompletionListener {
                            Handler(Looper.getMainLooper()).post {
                                isSpeaking = false
                                player.release()
                                if (mediaPlayerHolder.value == player) mediaPlayerHolder.value = null
                            }
                        }
                        player.prepare()
                        mediaPlayerHolder.value = player
                        player.start()
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) { isSpeaking = false }
                        speakWithTts(plain)
                    }
                }
            } else {
                speakWithTts(plain)
            }
        }
    }

    fun stopSpeaking() {
        mediaPlayerHolder.value?.stop()
        mediaPlayerHolder.value?.release()
        mediaPlayerHolder.value = null
        ttsHolder.value?.stop()
        isSpeaking = false
    }

    fun askQuestion() {
        if (apiKey.isBlank()) { errorText = "API ключ не задан. Перейдите в Настройки."; return }
        if (question.isBlank()) return

        // Disambiguation reply for pending booking
        val booking = pendingBooking
        if (booking != null) {
            scope.launch {
                isLoading = true
                loadingStatus = "Уточняю выбор..."
                errorText = ""
                answer = ""
                try {
                    val selected = disambiguateDog(question, booking.candidates, apiKey)
                    if (selected == null) {
                        answer = "<p>Не удалось уточнить. Попробуйте сказать иначе.</p>"
                        shouldAutoLaunchMic = true
                    } else {
                        pendingBooking = null
                        val isDelete = booking.action == "delete"
                        loadingStatus = if (isDelete) "Удаляю из таблицы..." else "Записываю в таблицу..."
                        val webUrl = prefs.getString("apps_script_url", "") ?: ""
                        val success = appendBookingToSheet(booking.startDate, booking.endDate, selected, webUrl, booking.action)
                        val (html, voice) = if (success) {
                            bookingSuccess = true
                            if (isDelete)
                                "<p style='color:#388E3C;font-weight:bold;font-size:16px'>✅ Удалено!</p>" +
                                "<p>$selected</p>" +
                                "<p>📅 с <b>${booking.startDate}</b> по <b>${booking.endDate}</b></p>" to "Удалено!"
                            else
                                "<p style='color:#388E3C;font-weight:bold;font-size:16px'>✅ Записано!</p>" +
                                "<p>$selected</p>" +
                                "<p>📅 с <b>${booking.startDate}</b> по <b>${booking.endDate}</b></p>" to "Записано!"
                        } else {
                            "<p style='color:#D32F2F'>❌ Ошибка. Проверьте URL Apps Script в настройках.</p>" to ""
                        }
                        if (voice.isNotBlank() && apiKey.isNotBlank() && voiceAutoSpeak) {
                            loadingStatus = "Подготовка голоса..."
                            pendingTtsBytes.value = withContext(Dispatchers.IO) { downloadTtsAudio(formatPhoneNumbers(voice.trim()), apiKey) }
                        }
                        answer = html
                        voiceComment = voice
                        if (voice.isNotBlank() && voiceAutoSpeak) speak(voice)
                    }
                } catch (e: Exception) {
                    pendingBooking = null
                    errorText = "Ошибка: ${e.message}"
                }
                isLoading = false
                loadingStatus = ""
            }
            return
        }

        scope.launch {
            isLoading = true
            errorText = ""
            answer = ""
            voiceComment = ""
            galleryUrls = emptyList()
            fullScreenPhotoIndex = null
            bookingSuccess = false
            lastTableType = null
            wazeAddress = ""
            try {
                loadingStatus = "Определяю категорию..."
                val result = classifyQuestion(question, apiKey)
                lastTableType = result.tableType

                val tableLabel = when (result.tableType) {
                    TableType.BOARDING       -> "Передержка"
                    TableType.CLIENTS        -> "Список клиентов"
                    TableType.TRAINING       -> "Занятия с собаками"
                    TableType.PHOTOS         -> "Фото из канала"
                    TableType.RECORD_BOOKING -> "Запись на передержку"
                    TableType.NAVIGATION     -> "Навигация"
                    TableType.DATA           -> "Данные"
                }
                loadingStatus = "Загружаю «$tableLabel»..."

                val (html, voice) = when (result.tableType) {
                    TableType.NAVIGATION -> {
                        val personName = result.dogName ?: question
                        loadingStatus = "Ищу адрес в книге..."
                        val addresses = loadAddresses(prefs)
                        if (addresses.isEmpty()) {
                            "<p>Адресная книга пуста.</p>" to ""
                        } else {
                            loadingStatus = "Определяю адрес..."
                            val hebrewAddr = findAddressForPerson(personName, addresses, apiKey)
                            if (hebrewAddr == null) {
                                "<p>Адрес для <b>«$personName»</b> не найден в адресной книге.</p>" to "Адрес не найден"
                            } else {
                                wazeAddress = hebrewAddr
                                val entry = addresses.firstOrNull { it.hebrew == hebrewAddr }
                                val displayName = entry?.name ?: personName
                                val russianAddr = entry?.russian ?: ""
                                val url = "https://waze.com/ul?q=${Uri.encode(hebrewAddr)}&navigate=yes"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage("com.waze") }
                                try { context.startActivity(intent) }
                                catch (_: ActivityNotFoundException) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                val html = buildString {
                                    append("<p>🗺️ Маршрут к <b>$displayName</b></p>")
                                    append("<p style='direction:rtl;text-align:right;font-size:16px'>$hebrewAddr</p>")
                                    if (russianAddr.isNotBlank()) append("<p style='color:#757575'>$russianAddr</p>")
                                }
                                html to "Открываю маршрут к $displayName"
                            }
                        }
                    }
                    TableType.DATA -> {
                        loadingStatus = "Загружаю данные..."
                        val dataEntries = loadDataEntries(prefs)
                        if (dataEntries.isEmpty()) {
                            "<p>Список данных пуст. Добавьте записи в разделе Информация → Данные.</p>" to ""
                        } else {
                            loadingStatus = "Спрашиваю ChatGPT..."
                            val entriesText = dataEntries.joinToString("\n") { "${it.key}: ${it.value}" }
                            askTableAssistant(question, entriesText, "личные данные и заметки (номера, коды, карты, документы, реквизиты)", apiKey)
                        }
                    }
                    TableType.PHOTOS -> {
                        val name = result.dogName ?: question
                        loadingStatus = "Обновляю канал..."
                        syncFosteringChannel(context, incremental = true, onProgress = {})
                        loadingStatus = "Ищу фото «$name»..."
                        val raw = withContext(Dispatchers.IO) {
                            FosteringDatabase.get(context).dao().search(name)
                        }
                        loadingStatus = "Фильтрую через AI..."
                        val posts = filterFosteringPosts(raw, apiKey, name)
                        if (posts.isEmpty()) {
                            "<p>Фото с именем <b>«$name»</b> не найдены в базе канала.</p>" +
                            "<p>Попробуйте обновить базу в разделе «Фото передержек».</p>" to ""
                        } else {
                            answerWebViewHeight = maxOf(200.dp, (posts.size * 280).dp)
                            galleryUrls = posts.map { it.photoUrl }
                            buildPhotoGalleryHtml(posts, name) to ""
                        }
                    }
                    TableType.RECORD_BOOKING -> {
                        val dogName  = result.dogName
                        val startDate = result.startDate
                        val endDate   = result.endDate
                        if (dogName == null) {
                            "<p>Не удалось распознать кличку. Попробуйте ещё раз.</p>" to ""
                        } else if (dogName.equals("НОВАЯ_СОБАКА", ignoreCase = true)) {
                            pendingNewDogBooking = NewDogBookingPending(
                                startDate   = startDate ?: "",
                                endDate     = endDate ?: "",
                                presetName  = result.newDogPresetName ?: "",
                                presetBreed = result.newDogPresetBreed ?: "",
                                presetOwner = result.newDogPresetOwner ?: ""
                            )
                            "" to ""
                        } else {
                            pendingExistingDogBooking = ExistingDogBookingPending(
                                dogName       = dogName,
                                startDate     = startDate ?: "",
                                endDate       = endDate ?: "",
                                clarification = result.clarification ?: "",
                                action        = result.action
                            )
                            "" to ""
                        }
                    }
                    TableType.BOARDING -> {
                        val (csv, merges) = coroutineScope {
                            val csvJob    = async { fetchCsv(SHEET_CSV_URL) }
                            val mergesJob = async { fetchSheetMerges() }
                            csvJob.await() to mergesJob.await()
                        }
                        val year = Calendar.getInstance().get(Calendar.YEAR)
                        val csvRows     = csv.lines().filter { it.isNotBlank() }.map { parseCsvLine(it) }
                        val filteredRows = filterRowsByDateWindow(buildGridWithMerges(csvRows, merges))
                        val sheetText   = filteredRows.joinToString("\n") { row -> row.joinToString(" | ") }
                        loadingStatus = "Спрашиваю ChatGPT..."
                        askBoardingAssistant(question, sheetText, apiKey, year)
                    }
                    TableType.CLIENTS -> {
                        val csv = fetchCsv(CLIENTS_CSV_URL)
                        loadingStatus = "Спрашиваю ChatGPT..."
                        askTableAssistant(question, csv, "список клиентов и их собак (контакты, телефоны, породы, имена хозяев)", apiKey)
                    }
                    TableType.TRAINING -> {
                        val csv = fetchCsv(TRAINING_CSV_URL)
                        loadingStatus = "Спрашиваю ChatGPT..."
                        askTableAssistant(question, csv, "расписание и журнал занятий по дрессировке собак", apiKey)
                    }
                }

                if (voice.isNotBlank() && apiKey.isNotBlank() && voiceAutoSpeak) {
                    loadingStatus = "Подготовка голоса..."
                    pendingTtsBytes.value = withContext(Dispatchers.IO) { downloadTtsAudio(formatPhoneNumbers(voice.trim()), apiKey) }
                }
                answer = html
                voiceComment = voice
                if (voice.isNotBlank() && voiceAutoSpeak) {
                    speak(voice)
                }
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
                voiceComment = ""
                askQuestion()
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

    LaunchedEffect(shouldAutoLaunchMic) {
        if (shouldAutoLaunchMic) {
            shouldAutoLaunchMic = false
            kotlinx.coroutines.delay(1500)
            launchSpeech()
        }
    }

    fullScreenPhotoIndex?.let { idx ->
        if (galleryUrls.isNotEmpty()) {
            FullScreenPhotoViewer(
                photos = galleryUrls,
                initialIndex = idx,
                onDismiss = { fullScreenPhotoIndex = null }
            )
        }
    }

    existingDogDialogState?.let { (pending, initialPhotoUrl) ->
        ExistingDogFormDialog(
            pending = pending,
            initialPhotoUrl = initialPhotoUrl,
            apiKey = apiKey,
            onConfirm = { dogNameField, clarificationField, startD, endD ->
                pendingExistingDogBooking = null
                existingDogDialogState = null
                val isDelete = pending.action == "delete"
                val webUrl = prefs.getString("apps_script_url", "") ?: ""
                val capturedApiKey = apiKey

                if (fromMenu) {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val csv = fetchCsv(CLIENTS_CSV_URL)
                            val candidates = findDogCandidates(csv, dogNameField)
                            val (success, info) = when {
                                candidates.isEmpty() ->
                                    false to "Собака «$dogNameField» не найдена в базе"
                                candidates.size == 1 ->
                                    appendBookingToSheet(startD, endD, candidates[0], webUrl, pending.action) to candidates[0]
                                else -> {
                                    val selected = if (clarificationField.isNotBlank())
                                        disambiguateDog(clarificationField, candidates, capturedApiKey)
                                    else null
                                    if (selected != null)
                                        appendBookingToSheet(startD, endD, selected, webUrl, pending.action) to selected
                                    else
                                        false to "Несколько собак с кличкой «$dogNameField»: ${candidates.joinToString(", ")}"
                                }
                            }
                            val label = if (isDelete) "Удалён из передержки" else "Записан на передержку"
                            sendTelegramMessage(
                                if (success) "✅ $label!\n$info\n📅 $startD — $endD"
                                else "❌ Ошибка. $info"
                            )
                        } catch (e: Exception) {
                            sendTelegramMessage("❌ Ошибка: ${e.message ?: "неизвестная"}")
                        }
                    }
                    Toast.makeText(context, "Запущено. Придёт сообщение в Telegram.", Toast.LENGTH_LONG).show()
                    onBack()
                } else {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val csv = fetchCsv(CLIENTS_CSV_URL)
                            val candidates = findDogCandidates(csv, dogNameField)
                            val (success, info) = when {
                                candidates.isEmpty() ->
                                    false to "Собака «$dogNameField» не найдена в базе"
                                candidates.size == 1 ->
                                    appendBookingToSheet(startD, endD, candidates[0], webUrl, pending.action) to candidates[0]
                                else -> {
                                    val selected = if (clarificationField.isNotBlank())
                                        disambiguateDog(clarificationField, candidates, capturedApiKey)
                                    else null
                                    if (selected != null)
                                        appendBookingToSheet(startD, endD, selected, webUrl, pending.action) to selected
                                    else
                                        false to "Несколько собак с кличкой «$dogNameField»: ${candidates.joinToString(", ")}"
                                }
                            }
                            val label = if (isDelete) "Удалён из передержки" else "Записан на передержку"
                            sendTelegramMessage(
                                if (success) "✅ $label!\n$info\n📅 $startD — $endD"
                                else "❌ Ошибка. $info"
                            )
                        } catch (e: Exception) {
                            sendTelegramMessage("❌ Ошибка: ${e.message ?: "неизвестная"}")
                        }
                    }
                    Toast.makeText(context, "Запущено. Придёт сообщение в Telegram.", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = {
                pendingExistingDogBooking = null
                existingDogDialogState = null
                if (fromMenu) onBack() else answer = "<p>Запись отменена.</p>"
            }
        )
    }

    pendingNewDogBooking?.let { pending ->
        NewDogFormDialog(
            startDate   = pending.startDate,
            endDate     = pending.endDate,
            presetName  = pending.presetName,
            presetBreed = pending.presetBreed,
            presetOwner = pending.presetOwner,
            onConfirm = { dogNameField, breedField, ownerField, phoneField, startD, endD ->
                pendingNewDogBooking = null
                val parts = listOf(dogNameField, breedField, ownerField, phoneField).filter { it.isNotBlank() }
                val info = if (parts.isEmpty()) "Новая собака" else parts.joinToString(", ")
                val webUrl = prefs.getString("apps_script_url", "") ?: ""
                val capturedApiKey = apiKey

                if (fromMenu) {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val success = appendBookingToSheet(startD, endD, info, webUrl, "add")
                            sendTelegramMessage(
                                if (success) "✅ Записан на передержку!\n$info\n📅 $startD — $endD"
                                else "❌ Ошибка при записи на передержку\n$info"
                            )
                        } catch (e: Exception) {
                            sendTelegramMessage("❌ Ошибка: ${e.message ?: "неизвестная"}")
                        }
                    }
                    Toast.makeText(context, "Запущено. Придёт сообщение в Telegram.", Toast.LENGTH_LONG).show()
                    onBack()
                } else {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val success = appendBookingToSheet(startD, endD, info, webUrl, "add")
                            sendTelegramMessage(
                                if (success) "✅ Записан на передержку!\n$info\n📅 $startD — $endD"
                                else "❌ Ошибка при записи на передержку\n$info"
                            )
                        } catch (e: Exception) {
                            sendTelegramMessage("❌ Ошибка: ${e.message ?: "неизвестная"}")
                        }
                    }
                    Toast.makeText(context, "Запущено. Придёт сообщение в Telegram.", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = {
                pendingNewDogBooking = null
                if (fromMenu) onBack() else answer = "<p>Запись отменена.</p>"
            }
        )
    }

    val accentColor = Color(0xFF5E35B1)

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Голосовой ассистент",
            subtitle = "Передержка, клиенты, занятия, фото",
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
                    Text(
                        "API ключ не задан. Перейдите в Настройки.",
                        fontSize = 14.sp,
                        color = Color(0xFFE65100),
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                }
            }

            if (!fromMenu) Spacer(Modifier.height(8.dp))

            // Mic button with pulse animation when loading
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isLoading) 1.12f else 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "scale"
            )

            if (!fromMenu) {
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
                            Text("Проверьте и исправьте при необходимости", fontSize = 12.sp, color = Color(0xFF7E57C2), fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = question,
                                onValueChange = { question = it },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { askQuestion() })
                            )
                            if (answer.isBlank() && errorText.isBlank() && apiKey.isNotBlank()) {
                                Button(
                                    onClick = { askQuestion() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                ) {
                                    Text("Выполнить", color = Color.White)
                                }
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
                                    settings.domStorageEnabled = true
                                    settings.defaultFontSize = 15
                                    settings.defaultTextEncodingName = "UTF-8"
                                    addJavascriptInterface(object : Any() {
                                        @JavascriptInterface
                                        fun onImageClick(url: String) {
                                            Handler(Looper.getMainLooper()).post {
                                                val idx = galleryUrls.indexOf(url).coerceAtLeast(0)
                                                fullScreenPhotoIndex = idx
                                            }
                                        }
                                    }, "Android")
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
                                wv.loadDataWithBaseURL("https://cdn.jsdelivr.net", wrapInHtml(answer), "text/html", "UTF-8", null)
                            },
                            modifier = Modifier.fillMaxWidth().height(answerWebViewHeight)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isSpeaking) accentColor else accentColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { if (isSpeaking) stopSpeaking() else speak(voiceComment) },
                                    enabled = voiceComment.isNotBlank(),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Прослушать",
                                        tint = if (isSpeaking) Color.White
                                               else if (voiceComment.isNotBlank()) accentColor
                                               else Color(0xFFBDBDBD),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    stopSpeaking()
                                    question = ""
                                    answer = ""
                                    voiceComment = ""
                                    errorText = ""
                                    bookingSuccess = false
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
                        if (bookingSuccess) {
                            Button(
                                onClick = {
                                    context.openInSheets(
                                        "https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/edit?gid=0#gid=0"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C))
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Таблица передержки", color = Color.White)
                            }
                        }
                        if (lastTableType == TableType.BOARDING) {
                            Button(
                                onClick = {
                                    context.openInSheets(
                                        "https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/edit?gid=0#gid=0"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C))
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Таблица передержки", color = Color.White)
                            }
                        }
                        if (lastTableType == TableType.TRAINING) {
                            Button(
                                onClick = {
                                    context.openInSheets(
                                        "https://docs.google.com/spreadsheets/d/1k7usk6ZFkPL7x6-CFFfAr87kQvRkI9TBCZZqJFej0X4/edit?gid=0#gid=0"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF283593))
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Таблица занятий", color = Color.White)
                            }
                        }
                        if (lastTableType == TableType.CLIENTS) {
                            Button(
                                onClick = {
                                    context.openInSheets(
                                        "https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/edit?gid=1215152509#gid=1215152509"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Список клиентов", color = Color.White)
                            }
                        }
                        if (lastTableType == TableType.PHOTOS) {
                            Button(
                                onClick = onTelegramFosteringClick,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF039BE5))
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Фото из Телеграм", color = Color.White)
                            }
                        }
                        if (lastTableType == TableType.NAVIGATION && wazeAddress.isNotBlank()) {
                            Button(
                                onClick = {
                                    val url = "https://waze.com/ul?q=${Uri.encode(wazeAddress)}&navigate=yes"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage("com.waze") }
                                    try { context.startActivity(intent) }
                                    catch (_: ActivityNotFoundException) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33CCFF))
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Открыть Waze", color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))


        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExistingDogFormDialog(
    pending: ExistingDogBookingPending,
    initialPhotoUrl: String? = null,
    apiKey: String,
    onConfirm: (dogName: String, clarification: String, startDate: String, endDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var dogName         by remember { mutableStateOf(pending.dogName) }
    var clarification   by remember { mutableStateOf(pending.clarification) }
    var startDateStr    by remember { mutableStateOf(pending.startDate) }
    var endDateStr      by remember { mutableStateOf(pending.endDate) }
    var showDatePicker  by remember { mutableStateOf(false) }
    var showDogPicker   by remember { mutableStateOf(false) }
    var showOwnerPicker by remember { mutableStateOf(false) }
    var clientDogs      by remember { mutableStateOf<List<ClientDog>>(emptyList()) }
    var isLoadingDogs   by remember { mutableStateOf(true) }
    var boardingPhotoUrl by remember { mutableStateOf(initialPhotoUrl) }

    LaunchedEffect(dogName, clarification, isLoadingDogs) {
        if (dogName.isBlank() || isLoadingDogs) return@LaunchedEffect
        val entries = clientDogs.filter { it.dogName.equals(dogName, ignoreCase = true) }
        if (entries.isEmpty()) { boardingPhotoUrl = null; return@LaunchedEffect }
        val matchedEntry = entries.firstOrNull { buildClarification(it.breed, it.ownerName) == clarification }
            ?: entries.first()
        boardingPhotoUrl = findBoardingPhoto(context, dogName, matchedEntry.lastBoarding, entries.distinctBy { it.ownerName }.size, apiKey)
    }
    val isDelete = pending.action == "delete"

    LaunchedEffect(Unit) {
        try { clientDogs = parseClientDogs(fetchCsv(CLIENTS_CSV_URL)) } catch (_: Exception) {}
        isLoadingDogs = false
        if (dogName.isNotBlank()) {
            val entry = clientDogs.firstOrNull { cd ->
                cd.dogName.equals(dogName, ignoreCase = true) &&
                (clarification.isBlank() || cd.ownerName.equals(clarification, ignoreCase = true))
            } ?: clientDogs.firstOrNull { it.dogName.equals(dogName, ignoreCase = true) }
            if (entry != null) {
                if (clarification.isBlank()) {
                    val cl = buildClarification(entry.breed, entry.ownerName)
                    if (cl.isNotBlank()) clarification = cl
                }
                if (isDelete && startDateStr.isBlank() && endDateStr.isBlank())
                    parseLastBoardingDates(entry.lastBoarding)?.let { (s, e) ->
                        startDateStr = s; endDateStr = e
                    }
            }
        }
    }

    val ownersForDog = remember(dogName, clientDogs) {
        clientDogs.filter { it.dogName.equals(dogName, ignoreCase = true) }
                  .distinctBy { it.ownerName }
    }

    if (showDatePicker) {
        DateRangePickerModal(
            initialStartDate = startDateStr,
            initialEndDate = endDateStr,
            onConfirm = { s, e -> startDateStr = s; endDateStr = e; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showDogPicker) {
        DogSelectionDialog(
            dogs = clientDogs.map { it.dogName }.distinct().sortedBy { it },
            onSelect = { name ->
                dogName = name
                val firstEntry = clientDogs.firstOrNull { it.dogName.equals(name, ignoreCase = true) }
                clarification = buildClarification(firstEntry?.breed ?: "", firstEntry?.ownerName ?: "")
                if (isDelete) {
                    parseLastBoardingDates(firstEntry?.lastBoarding ?: "")?.let { (s, e) ->
                        startDateStr = s; endDateStr = e
                    }
                }
                showDogPicker = false
            },
            onDismiss = { showDogPicker = false }
        )
    }
    if (showOwnerPicker) {
        OwnerSelectionDialog(
            owners = ownersForDog,
            onSelect = { entry ->
                clarification = buildClarification(entry.breed, entry.ownerName)
                if (isDelete) {
                    parseLastBoardingDates(entry.lastBoarding)?.let { (s, e) ->
                        startDateStr = s; endDateStr = e
                    }
                }
                showOwnerPicker = false
            },
            onDismiss = { showOwnerPicker = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isDelete) "Удалить с передержки" else "Записать на передержку",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                boardingPhotoUrl?.let { url ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Фото передержки",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 210.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val label = if (startDateStr.isBlank() && endDateStr.isBlank()) "Выбрать даты"
                                else if (startDateStr.isBlank()) "— $endDateStr"
                                else if (endDateStr.isBlank()) "$startDateStr —"
                                else "$startDateStr  —  $endDateStr"
                    Text(label, fontSize = 14.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = dogName,
                        onValueChange = { dogName = it },
                        label = { Text("Кличка") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(
                        onClick = { if (!isLoadingDogs) showDogPicker = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEDE7F6))
                    ) {
                        if (isLoadingDogs)
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Default.ExpandMore, contentDescription = "Выбрать из списка",
                                 tint = Color(0xFF5E35B1), modifier = Modifier.size(24.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = clarification,
                        onValueChange = { clarification = it },
                        label = { Text("Уточнение") },
                        placeholder = { Text("порода, хозяин...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        minLines = 1
                    )
                    if (ownersForDog.size > 1) {
                        IconButton(
                            onClick = { showOwnerPicker = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEDE7F6))
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Выбрать хозяина",
                                 tint = Color(0xFF5E35B1), modifier = Modifier.size(24.dp))
                        }
                    }
                }

            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(dogName, clarification, startDateStr, endDateStr) },
                colors = if (isDelete) ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                         else ButtonDefaults.buttonColors()
            ) {
                Text(if (isDelete) "Удалить" else "Записать")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewDogFormDialog(
    startDate: String,
    endDate: String,
    presetName: String = "",
    presetBreed: String = "",
    presetOwner: String = "",
    onConfirm: (dogName: String, breed: String, owner: String, phone: String, startDate: String, endDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    var dogName       by remember { mutableStateOf(presetName) }
    var breed         by remember { mutableStateOf(presetBreed) }
    var owner         by remember { mutableStateOf(presetOwner) }
    var phone         by remember { mutableStateOf("") }
    var startDateStr  by remember { mutableStateOf(startDate) }
    var endDateStr    by remember { mutableStateOf(endDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showDatePicker) {
        DateRangePickerModal(
            initialStartDate = startDateStr,
            initialEndDate = endDateStr,
            onConfirm = { s, e -> startDateStr = s; endDateStr = e; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        phone = normalizeIsraeliPhone(cursor.getString(0) ?: "")
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Записать новую собаку", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val label = if (startDateStr.isBlank() && endDateStr.isBlank()) "Выбрать даты"
                                else if (startDateStr.isBlank()) "— $endDateStr"
                                else if (endDateStr.isBlank()) "$startDateStr —"
                                else "$startDateStr  —  $endDateStr"
                    Text(label, fontSize = 14.sp)
                }

                OutlinedTextField(
                    value = dogName,
                    onValueChange = { dogName = it },
                    label = { Text("Кличка") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = breed,
                    onValueChange = { breed = it },
                    label = { Text("Порода") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text("Хозяин") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Телефон") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    IconButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_PICK,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                            )
                            contactPickerLauncher.launch(intent)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEDE7F6))
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Выбрать из контактов",
                            tint = Color(0xFF5E35B1),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(dogName, breed, owner, phone, startDateStr, endDateStr) }) {
                Text("Продолжить")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

private fun normalizeIsraeliPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.startsWith("972") && digits.length > 3 -> "0" + digits.drop(3)
        else -> digits
    }
}

private data class ClientDog(
    val dogName: String,
    val ownerName: String,
    val breed: String = "",
    val lastBoarding: String = ""
)

private fun parseClientDogs(csv: String): List<ClientDog> =
    csv.lines().drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
        val cols = parseCsvLine(line)
        val dogName = cols.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        ClientDog(
            dogName      = dogName,
            ownerName    = cols.getOrNull(0)?.trim() ?: "",
            breed        = cols.getOrNull(2)?.trim() ?: "",
            lastBoarding = cols.getOrNull(7)?.trim() ?: ""
        )
    }

private fun buildClarification(breed: String, ownerName: String): String = when {
    breed.isNotBlank() && ownerName.isNotBlank() -> "$breed, Хозяин: $ownerName"
    ownerName.isNotBlank() -> "Хозяин: $ownerName"
    breed.isNotBlank() -> breed
    else -> ""
}

private fun parseLastBoardingDates(s: String): Pair<String, String>? {
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
    val (endDay, endMonth)     = parseMonthDay(parts[1]) ?: return null

    val cal = Calendar.getInstance()
    val curMonth = cal.get(Calendar.MONTH) + 1
    val curYear  = cal.get(Calendar.YEAR)
    val startYear = if (startMonth < curMonth) curYear + 1 else curYear
    val endYear   = if (endMonth < startMonth) startYear + 1 else startYear

    return "%02d.%02d.%04d".format(startDay, startMonth, startYear) to
           "%02d.%02d.%04d".format(endDay, endMonth, endYear)
}

// Year logic for past dates: month > curMonth → previous year (was last year)
private fun parsePastBoardingDates(s: String): Pair<String, String>? {
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
    val (endDay, endMonth)     = parseMonthDay(parts[1]) ?: return null
    val cal = Calendar.getInstance()
    val curMonth = cal.get(Calendar.MONTH) + 1
    val curYear  = cal.get(Calendar.YEAR)
    val startYear = if (startMonth > curMonth) curYear - 1 else curYear
    val endYear   = if (endMonth < startMonth) startYear + 1 else startYear
    return "%02d.%02d.%04d".format(startDay, startMonth, startYear) to
           "%02d.%02d.%04d".format(endDay, endMonth, endYear)
}

private suspend fun findBoardingPhoto(
    context: Context,
    dogName: String,
    lastBoarding: String,
    uniqueOwnerCount: Int,
    apiKey: String
): String? = withContext(Dispatchers.IO) {
    try {
        val raw = FosteringDatabase.get(context).dao().search(dogName)
        if (raw.isEmpty()) return@withContext null
        val posts = filterFosteringPosts(raw, apiKey, dogName)
        if (posts.isEmpty()) return@withContext null
        if (uniqueOwnerCount <= 1) return@withContext posts.firstOrNull()?.photoUrl
        if (lastBoarding.isBlank()) return@withContext posts.firstOrNull()?.photoUrl
        val (startStr, endStr) = parsePastBoardingDates(lastBoarding)
            ?: return@withContext posts.firstOrNull()?.photoUrl
        val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val startDate = try { LocalDate.parse(startStr, fmt) } catch (_: Exception) { return@withContext posts.firstOrNull()?.photoUrl }
        val endDate   = try { LocalDate.parse(endStr,   fmt) } catch (_: Exception) { return@withContext posts.firstOrNull()?.photoUrl }
        posts.firstOrNull { post ->
            if (post.date.isBlank()) return@firstOrNull false
            val d = try { LocalDate.parse(post.date, fmt) } catch (_: Exception) { return@firstOrNull false }
            !d.isBefore(startDate) && !d.isAfter(endDate)
        }?.photoUrl ?: posts.firstOrNull()?.photoUrl
    } catch (_: Exception) { null }
}

private fun isValidDateFormat(s: String?): Boolean {
    if (s.isNullOrBlank()) return false
    val p = s.split(".")
    return p.size == 3 && p[0].toIntOrNull() != null && p[1].toIntOrNull() != null &&
           p[2].length == 4 && p[2].toIntOrNull() != null
}

@Composable
private fun DogSelectionDialog(
    dogs: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(dogs, search) {
        if (search.isBlank()) dogs else dogs.filter { it.contains(search, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбрать собаку", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Поиск...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    filtered.forEach { dog ->
                        Text(
                            dog,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(dog) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            fontSize = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun OwnerSelectionDialog(
    owners: List<ClientDog>,
    onSelect: (ClientDog) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбрать хозяина", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                owners.forEach { entry ->
                    Text(
                        buildClarification(entry.breed, entry.ownerName),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(entry) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        fontSize = 15.sp
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerModal(
    initialStartDate: String,
    initialEndDate: String,
    onConfirm: (startDate: String, endDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = parseDMYToMillis(initialStartDate),
        initialSelectedEndDateMillis   = parseDMYToMillis(initialEndDate),
        initialDisplayedMonthMillis    = parseDMYToMillis(initialStartDate)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val start = state.selectedStartDateMillis?.let { millisToDMY(it) } ?: initialStartDate
                    val end   = state.selectedEndDateMillis?.let   { millisToDMY(it) } ?: initialEndDate
                    onConfirm(start, end)
                },
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null
            ) { Text("OK") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
    ) {
        DateRangePicker(
            state = state,
            showModeToggle = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
        )
    }
}

private fun parseDMYToMillis(date: String): Long? {
    val p = date.split(".")
    if (p.size != 3) return null
    val day   = p[0].toIntOrNull() ?: return null
    val month = p[1].toIntOrNull() ?: return null
    val year  = p[2].toIntOrNull() ?: return null
    return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        set(year, month - 1, day, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun millisToDMY(millis: Long): String {
    val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    return "%02d.%02d.%04d".format(
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.YEAR)
    )
}

private suspend fun fetchCsv(url: String): String = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 15_000
    try {
        if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
        conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    } finally {
        conn.disconnect()
    }
}

private suspend fun classifyQuestion(question: String, apiKey: String): ClassifyResult =
    withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date())
        val systemPrompt = """Ты определяешь категорию запроса о бизнесе DogIsrael (дрессировка и передержка собак, Израиль).
Сегодня: $today.

Категории и формат ответа (строго):
- BOARDING — вопрос про передержку (кто сейчас, свободные места, даты)
- CLIENTS — вопрос про клиентов (контакты, телефоны, имена хозяев)
- TRAINING — вопрос про занятия/дрессировку (расписание, посещаемость)
- PHOTOS:<кличка> — запрос на показ фото собаки (покажи, хочу посмотреть)
- RECORD_BOOKING:<кличка>:<дата_начала>:<дата_конца>:add:<уточнение> — ЗАПИСЬ собаки на передержку. Использовать для ЛЮБОГО запроса на запись, если в тексте НЕТ слова "новая" или "новый". <уточнение> — порода или имя хозяина если упомянуты, иначе пустая строка.
- RECORD_BOOKING:НОВАЯ_СОБАКА:<дата_начала>:<дата_конца>:add:<кличка>|<порода>|<хозяин> — ТОЛЬКО если в запросе буквально есть слово "новая" или "новый" (например "новую собаку", "нового клиента"). Без этих слов — НИКОГДА не использовать. После последнего ':' три поля через '|' в порядке кличка|порода|хозяин. Если поле не упомянуто — пустая строка.
- RECORD_BOOKING:<кличка>:<дата_начала>:<дата_конца>:delete:<уточнение> — УДАЛЕНИЕ с передержки (ключевые слова: удали, убери, сними, отмени). <уточнение> — порода или хозяин если упомянуты, иначе пустая строка. Если кличка не упомянута — оставить пустой, но action ВСЕГДА "delete" если запрос про удаление.
- NAVIGATION:<имя> — маршрут к человеку из адресной книги (как доехать, как проехать, маршрут к, куда ехать к)
- DATA:<запрос> — поиск в личных данных (номер, код, карта, документ, реквизиты, что не подходит под другие категории)

Для PHOTOS: кличка в именительном падеже.
Для RECORD_BOOKING: кличка в именительном падеже, даты в формате ДД.ММ.ГГГГ.
  Правило года: если месяц >= текущего месяца — текущий год; иначе — следующий год.

Примеры:
"покажи Ричарда" → PHOTOS:Ричард
"запиши новую собаку с 10 по 15 июня" → RECORD_BOOKING:НОВАЯ_СОБАКА:10.06.2026:15.06.2026:add:||
"запиши новую собаку Барон с 10 по 15 июня" → RECORD_BOOKING:НОВАЯ_СОБАКА:10.06.2026:15.06.2026:add:Барон||
"запиши нового клиента Тузик хозяин Иван порода метис с 10 по 20 мая" → RECORD_BOOKING:НОВАЯ_СОБАКА:10.05.2026:20.05.2026:add:Тузик|метис|Иван
"запиши нового клиента Барон, лабрадор, хозяин Петя с 10 по 15 июня" → RECORD_BOOKING:НОВАЯ_СОБАКА:10.06.2026:15.06.2026:add:Барон|Лабрадор|Петя
"запиши Мэри с 10 по 15 июня" → RECORD_BOOKING:Мэри:10.06.2026:15.06.2026:add:
"запиши Джесси на передержку" → RECORD_BOOKING:Джесси:::add:
"запишите Бобика на передержку" → RECORD_BOOKING:Бобик:::add:
"запиши Тома хозяин Елена с 10 по 20 июня" → RECORD_BOOKING:Том:10.06.2026:20.06.2026:add:хозяин Елена
"запиши Тома овчарка с 10 по 20 июня" → RECORD_BOOKING:Том:10.06.2026:20.06.2026:add:Овчарка
"запишите Бобика на передержку с 1 по 5 марта" → RECORD_BOOKING:Бобик:01.03.2027:05.03.2027:add
"удали Мэри с 10 по 15 июня" → RECORD_BOOKING:Мэри:10.06.2026:15.06.2026:delete
"убери Бобика с передержки с 1 по 5 марта" → RECORD_BOOKING:Бобик:01.03.2027:05.03.2027:delete
"удали собаку с передержки" → RECORD_BOOKING::::delete:
"удалить с передержки" → RECORD_BOOKING::::delete:
"кто на передержке сейчас" → BOARDING

Ответь ТОЛЬКО в одном из указанных форматов, без пояснений."""

        val systemMsg = JSONObject().apply { put("role", "system"); put("content", systemPrompt) }
        val userMsg   = JSONObject().apply { put("role", "user");   put("content", question) }
        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().apply { put(systemMsg); put(userMsg) })
            put("max_tokens", 40)
            put("temperature", 0.0)
        }.toString()

        val conn = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code != 200) throw Exception("HTTP $code при определении категории")
        val content = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
            .trim()
        when {
            content.startsWith("RECORD_BOOKING:", ignoreCase = true) -> {
                val parts = content.split(":")
                val dogName = parts.getOrNull(1)?.trim()
                val isNew = dogName.equals("НОВАЯ_СОБАКА", ignoreCase = true)
                val extra = parts.getOrNull(5)?.trim()
                val sub = if (isNew) extra?.split("|") else null
                ClassifyResult(
                    tableType     = TableType.RECORD_BOOKING,
                    dogName       = dogName,
                    startDate     = parts.getOrNull(2)?.trim()?.takeIf { isValidDateFormat(it) },
                    endDate       = parts.getOrNull(3)?.trim()?.takeIf { isValidDateFormat(it) },
                    action        = if (parts.getOrNull(4)?.trim().equals("delete", ignoreCase = true)) "delete" else "add",
                    clarification = if (!isNew) extra?.takeIf { it.isNotBlank() } else null,
                    newDogPresetName  = sub?.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() },
                    newDogPresetBreed = sub?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() },
                    newDogPresetOwner = sub?.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
                )
            }
            content.startsWith("PHOTOS:", ignoreCase = true) ->
                ClassifyResult(TableType.PHOTOS, dogName = content.substringAfter(":").trim())
            content.startsWith("NAVIGATION:", ignoreCase = true) ->
                ClassifyResult(TableType.NAVIGATION, dogName = content.substringAfter(":").trim())
            content.startsWith("DATA:", ignoreCase = true) ->
                ClassifyResult(TableType.DATA, dogName = content.substringAfter(":").trim())
            content.contains("CLIENTS", ignoreCase = true)  -> ClassifyResult(TableType.CLIENTS)
            content.contains("TRAINING", ignoreCase = true) -> ClassifyResult(TableType.TRAINING)
            else                                             -> ClassifyResult(TableType.BOARDING)
        }
    }

private suspend fun askTableAssistant(
    question: String, sheetData: String, tableDescription: String, apiKey: String
): Pair<String, String> = withContext(Dispatchers.IO) {
    val today = SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date())
    val systemPrompt = """Ты помощник службы DogIsrael (дрессировка и передержка собак, Израиль). Тебе предоставлены данные из Google Sheets: $tableDescription.
Сегодня: $today.

Данные таблицы (CSV):
$sheetData

Правила ответа:
- Отвечай подробно и по существу, на русском языке.
- Формат ответа: JSON объект с двумя полями:
- Если пользователь просит диаграмму или график: ты МОЖЕШЬ это сделать! Ответ отображается в WebView с JavaScript. Подключи Chart.js тегом <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>, добавь <canvas id="chart1"></canvas> и скрипт инициализации. Тип: 'bar' — столбиковая, 'pie' — круговая, 'doughnut' — кольцевая. Никогда не отказывай в построении диаграммы.
  "html" — полный детальный ответ в HTML (без тегов html/head/body). Используй <table> для таблиц, <b> для ключевых данных, <ul><li> для списков, <p> для абзацев.
  "voice" — краткий голосовой комментарий 1–2 предложения без HTML-тегов, для озвучивания вслух."""

    val systemMsg = JSONObject().apply { put("role", "system"); put("content", systemPrompt) }
    val userMsg   = JSONObject().apply { put("role", "user");   put("content", question) }
    val body = JSONObject().apply {
        put("model", "gpt-4o")
        put("messages", JSONArray().apply { put(systemMsg); put(userMsg) })
        put("max_tokens", 1200)
        put("temperature", 0.2)
        put("response_format", JSONObject().apply { put("type", "json_object") })
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
    val responseText = if (code == 200) conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    else throw Exception("OpenAI $code: ${conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""}")

    val raw = JSONObject(responseText)
        .getJSONArray("choices").getJSONObject(0)
        .getJSONObject("message").getString("content").trim()
    val parsed = runCatching { JSONObject(raw) }.getOrNull()
    val html  = parsed?.optString("html").takeIf { !it.isNullOrBlank() } ?: raw
    val voice = parsed?.optString("voice") ?: ""
    html to voice
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

private suspend fun askBoardingAssistant(question: String, sheetData: String, apiKey: String, year: Int): Pair<String, String> =
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
- Если пользователь просит диаграмму или график: ты МОЖЕШЬ это сделать! Ответ отображается в WebView с JavaScript. Подключи Chart.js тегом <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>, добавь <canvas id="chart1"></canvas> и скрипт инициализации. Тип: 'bar' — столбиковая, 'pie' — круговая, 'doughnut' — кольцевая. Никогда не отказывай в построении диаграммы.
- Формат ответа: JSON объект с двумя полями:
  "html" — полный детальный ответ в HTML (без тегов html/head/body). Используй <table> для таблиц, <b> для ключевых данных, <ul><li> для списков, <p> для абзацев. Не дублируй данные одновременно в тексте и таблице.
  "voice" — краткий голосовой комментарий 1–2 предложения без HTML-тегов, для озвучивания вслух."""

        val systemMsg = JSONObject().apply { put("role", "system"); put("content", systemPrompt) }
        val userMsg   = JSONObject().apply { put("role", "user");   put("content", question) }
        val messages  = JSONArray().apply { put(systemMsg); put(userMsg) }
        val body = JSONObject().apply {
            put("model", "gpt-4o")
            put("messages", messages)
            put("max_tokens", 1200)
            put("temperature", 0.2)
            put("response_format", JSONObject().apply { put("type", "json_object") })
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

        val raw = JSONObject(responseText)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
        val parsed = runCatching { JSONObject(raw) }.getOrNull()
        val html  = parsed?.optString("html").takeIf { !it.isNullOrBlank() } ?: raw
        val voice = parsed?.optString("voice") ?: ""
        html to voice
    }

private fun buildPhotoGalleryHtml(posts: List<FosteringPost>, dogName: String): String {
    val sb = StringBuilder()
    sb.append("<h3 style='color:#5E35B1;margin:0 0 12px'>")
    sb.append("Фото: $dogName (${posts.size})")
    sb.append("</h3>")
    for (post in posts) {
        sb.append("<div style='margin-bottom:14px;border-radius:12px;overflow:hidden;box-shadow:0 2px 6px rgba(0,0,0,0.12)'>")
        sb.append("<div style='height:250px;background:#f0f0f0;overflow:hidden'>")
        sb.append("<img src='${post.photoUrl}' style='width:100%;height:100%;object-fit:contain;cursor:pointer' loading='lazy' onclick='if(window.Android) Android.onImageClick(this.src)'>")
        sb.append("</div>")
        if (post.caption.isNotBlank() || post.date.isNotBlank()) {
            sb.append("<div style='padding:8px 12px;font-size:13px;color:#424242;background:#fafafa;line-height:1.4'>")
            if (post.caption.isNotBlank()) {
                val caption = if (post.caption.length > 200) post.caption.take(200) + "…" else post.caption
                sb.append(caption)
            }
            if (post.date.isNotBlank()) {
                sb.append("<div style='font-size:11px;color:#9E9E9E;margin-top:4px;text-align:right'>")
                sb.append(post.date)
                sb.append("</div>")
            }
            sb.append("</div>")
        }
        sb.append("</div>")
    }
    return sb.toString()
}

private fun findDogCandidates(csv: String, dogName: String): List<String> =
    csv.lines().drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
        val cols = parseCsvLine(line)
        val name = cols.getOrNull(3)?.trim() ?: return@mapNotNull null
        if (name.equals(dogName, ignoreCase = true))
            cols.getOrNull(8)?.trim()?.takeIf { it.isNotBlank() }
        else null
    }.distinct()

private suspend fun disambiguateDog(
    clarification: String,
    candidates: List<String>,
    apiKey: String
): String? = withContext(Dispatchers.IO) {
    val numbered = candidates.mapIndexed { i, c -> "${i + 1}: $c" }.joinToString("\n")
    val prompt = "Есть несколько собак:\n$numbered\n\nПользователь уточнил: «$clarification»\n\nОтветь ТОЛЬКО цифрой — номером нужной записи. Если неясно — ответь 0."
    val body = JSONObject().apply {
        put("model", "gpt-4o-mini")
        put("messages", JSONArray().apply {
            put(JSONObject().apply { put("role", "user"); put("content", prompt) })
        })
        put("max_tokens", 5)
        put("temperature", 0.0)
    }.toString()
    val conn = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
    conn.setRequestProperty("Authorization", "Bearer $apiKey")
    conn.doOutput = true
    conn.connectTimeout = 15_000
    conn.readTimeout = 15_000
    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    if (conn.responseCode != 200) return@withContext null
    val answer = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
        .getJSONArray("choices").getJSONObject(0)
        .getJSONObject("message").getString("content").trim()
    val idx = answer.toIntOrNull() ?: 0
    if (idx < 1 || idx > candidates.size) null else candidates[idx - 1]
}

private suspend fun appendBookingToSheet(
    startDate: String,
    endDate: String,
    info: String,
    webAppUrl: String,
    action: String = "add"
): Boolean = withContext(Dispatchers.IO) {
    if (webAppUrl.isBlank()) return@withContext false
    try {
        val enc = Charsets.UTF_8.name()
        val url = buildString {
            append(webAppUrl)
            append("?startDate=").append(java.net.URLEncoder.encode(startDate, enc))
            append("&endDate=").append(java.net.URLEncoder.encode(endDate, enc))
            append("&info=").append(java.net.URLEncoder.encode(info, enc))
            append("&action=").append(action)
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.responseCode // wait for response
        true             // any response = script ran = success
    } catch (_: Exception) { false }
}

// Splits digit sequences of 7+ chars with spaces so TTS reads each digit separately
private fun formatPhoneNumbers(text: String): String =
    Regex("""\d{7,}""").replace(text) { it.value.toCharArray().joinToString(" ") }

private suspend fun downloadTtsAudio(text: String, apiKey: String): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val body = JSONObject().apply {
            put("model", "tts-1")
            put("input", text)
            put("voice", "nova")
        }.toString()
        val conn = URL("https://api.openai.com/v1/audio/speech").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        if (conn.responseCode != 200) null else conn.inputStream.readBytes()
    } catch (_: Exception) { null }
}

private suspend fun findAddressForPerson(
    personName: String,
    addresses: List<AddressEntry>,
    apiKey: String
): String? = withContext(Dispatchers.IO) {
    val addressList = addresses.joinToString("\n") { "${it.name}: иврит=${it.hebrew}, русский=${it.russian}" }
    val prompt = """Из списка адресов найди адрес для "$personName".
Список:
$addressList

Ответь ТОЛЬКО ивритским адресом (значение поля "иврит"). Если не найдено — ответь NOT_FOUND."""
    val body = JSONObject().apply {
        put("model", "gpt-4o-mini")
        put("messages", JSONArray().apply {
            put(JSONObject().apply { put("role", "user"); put("content", prompt) })
        })
        put("max_tokens", 80)
        put("temperature", 0.0)
    }.toString()
    try {
        val conn = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        if (conn.responseCode != 200) return@withContext null
        val result = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
        if (result.contains("NOT_FOUND", ignoreCase = true)) null else result
    } catch (_: Exception) { null }
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
