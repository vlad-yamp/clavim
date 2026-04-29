package com.dodisrael.clavim

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dodisrael.clavim.ui.theme.ClavimTheme
import com.dodisrael.clavim.ui.theme.HeaderEnd
import com.dodisrael.clavim.ui.theme.HeaderStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Screen { MAIN, SHEETS, INFO, EXCHANGE_RATES, TELEGRAM_FOSTERING, ADVERTISING, WHATSAPP, WHATSAPP_REMINDER }

data class MenuItem(
    val title: String,
    val icon: ImageVector,
    val iconBgColor: Color,
    val onClick: (Context) -> Unit
)

data class RateHistory(
    val labels: List<String>,
    val usdRub: List<Double>,
    val usdIls: List<Double>,
    val ilsRub: List<Double>
)

data class FosteringPost(val photoUrl: String, val caption: String)
data class WhatsAppContact(val name: String, val phone: String)

sealed class FosteringState {
    object Idle : FosteringState()
    object Loading : FosteringState()
    data class Success(val posts: List<FosteringPost>) : FosteringState()
    data class Error(val message: String) : FosteringState()
}

sealed class RatesState {
    object Loading : RatesState()
    data class Success(
        val usdRub: Double,
        val usdIls: Double,
        val ilsRub: Double,
        val history: RateHistory
    ) : RatesState()
    data class Error(val message: String) : RatesState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClavimTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    var screen by remember { mutableStateOf(Screen.MAIN) }
    var reminderType by remember { mutableStateOf(1) }

    BackHandler(enabled = screen != Screen.MAIN) {
        screen = when (screen) {
            Screen.EXCHANGE_RATES, Screen.TELEGRAM_FOSTERING -> Screen.INFO
            Screen.WHATSAPP_REMINDER -> Screen.WHATSAPP
            else -> Screen.MAIN
        }
    }

    when (screen) {
        Screen.SHEETS              -> SheetsMenuScreen(onBack = { screen = Screen.MAIN })
        Screen.INFO                -> InfoMenuScreen(
            onBack = { screen = Screen.MAIN },
            onExchangeRatesClick = { screen = Screen.EXCHANGE_RATES },
            onTelegramFosteringClick = { screen = Screen.TELEGRAM_FOSTERING }
        )
        Screen.EXCHANGE_RATES      -> ExchangeRatesScreen(onBack = { screen = Screen.INFO })
        Screen.TELEGRAM_FOSTERING  -> TelegramFosteringScreen(onBack = { screen = Screen.INFO })
        Screen.ADVERTISING         -> AdvertisingMenuScreen(onBack = { screen = Screen.MAIN })
        Screen.WHATSAPP            -> WhatsAppMenuScreen(
            onBack = { screen = Screen.MAIN },
            onReminderClick = { type -> reminderType = type; screen = Screen.WHATSAPP_REMINDER }
        )
        Screen.WHATSAPP_REMINDER   -> WhatsAppReminderScreen(
            reminderType = reminderType,
            onBack = { screen = Screen.WHATSAPP }
        )
        Screen.MAIN                -> MainMenuScreen(
            onSheetsClick      = { screen = Screen.SHEETS },
            onInfoClick        = { screen = Screen.INFO },
            onAdvertisingClick = { screen = Screen.ADVERTISING },
            onWhatsAppClick    = { screen = Screen.WHATSAPP }
        )
    }
}

@Composable
fun MainMenuScreen(
    onSheetsClick: () -> Unit,
    onInfoClick: () -> Unit,
    onAdvertisingClick: () -> Unit,
    onWhatsAppClick: () -> Unit
) {
    val context = LocalContext.current
    val items = remember { buildMainMenuItems(onSheetsClick, onInfoClick, onAdvertisingClick, onWhatsAppClick) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Clavim",
            subtitle = "Дрессировка и передержка собак · Израиль",
            showBack = false
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item -> MenuCard(item = item, onClick = { item.onClick(context) }) }
        }
    }
}

@Composable
fun SheetsMenuScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val items = remember { buildSheetsMenuItems() }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Таблицы Google",
            subtitle = "Данные по дрессировке и финансам",
            showBack = true,
            onBack = onBack
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item -> MenuCard(item = item, onClick = { item.onClick(context) }) }
        }
    }
}

@Composable
fun InfoMenuScreen(onBack: () -> Unit, onExchangeRatesClick: () -> Unit, onTelegramFosteringClick: () -> Unit) {
    val context = LocalContext.current
    val items = remember { buildInfoMenuItems(onExchangeRatesClick, onTelegramFosteringClick) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Информация",
            subtitle = "Услуги, контакты и фото",
            showBack = true,
            onBack = onBack
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item -> MenuCard(item = item, onClick = { item.onClick(context) }) }
        }
    }
}

@Composable
fun ExchangeRatesScreen(onBack: () -> Unit) {
    val ratesState by produceState<RatesState>(initialValue = RatesState.Loading) {
        value = fetchExchangeRates()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Курсы валют",
            subtitle = "Актуальные курсы и график за 30 дней",
            showBack = true,
            onBack = onBack
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentAlignment = Alignment.Center
        ) {
            when (val state = ratesState) {
                is RatesState.Loading -> CircularProgressIndicator()
                is RatesState.Error   -> Text(
                    text = "Ошибка: ${state.message}",
                    color = Color(0xFFB00020),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(24.dp)
                )
                is RatesState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        RateCard(
                            label = "Доллар к рублю",
                            value = "1 USD = ${"%.2f".format(state.usdRub)} ₽",
                            current = state.usdRub,
                            history = state.history.usdRub,
                            labels = state.history.labels,
                            chartColor = Color(0xFFF57C00)
                        )
                        RateCard(
                            label = "Доллар к шекелю",
                            value = "1 USD = ${"%.2f".format(state.usdIls)} ₪",
                            current = state.usdIls,
                            history = state.history.usdIls,
                            labels = state.history.labels,
                            chartColor = Color(0xFF1565C0)
                        )
                        RateCard(
                            label = "Шекель к рублю",
                            value = "1 ₪ = ${"%.2f".format(state.ilsRub)} ₽",
                            current = state.ilsRub,
                            history = state.history.ilsRub,
                            labels = state.history.labels,
                            chartColor = Color(0xFF00695C)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RateCard(
    label: String,
    value: String,
    current: Double,
    history: List<Double>,
    labels: List<String>,
    chartColor: Color
) {
    val firstVal = history.firstOrNull()
    val changeText = if (firstVal != null && firstVal != 0.0) {
        val pct = (current - firstVal) / firstVal * 100.0
        "${if (pct >= 0) "+" else ""}${"%.1f".format(pct)}% за 30 дн."
    } else ""
    val isPositive = firstVal == null || firstVal == 0.0 ||
            (current - firstVal) / firstVal >= 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = Color(0xFF757575),
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                )
                if (changeText.isNotEmpty()) {
                    Text(
                        text = changeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Sparkline(
                values = history,
                lineColor = chartColor,
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )
            if (labels.size >= 2) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(labels.first(), fontSize = 11.sp, color = Color(0xFFAAAAAA))
                    Text(labels.last(), fontSize = 11.sp, color = Color(0xFFAAAAAA))
                }
            }
        }
    }
}

@Composable
fun Sparkline(values: List<Double>, lineColor: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    Canvas(modifier = modifier) {
        val minVal = values.min()
        val maxVal = values.max()
        val range = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val padY = size.height * 0.1f

        val pts = values.mapIndexed { i, v ->
            Offset(
                x = i / (values.size - 1f) * size.width,
                y = (padY + ((maxVal - v) / range * (size.height - 2 * padY))).toFloat()
            )
        }

        // Gradient fill
        val fillPath = Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.30f), Color.Transparent),
                startY = 0f,
                endY = size.height
            )
        )

        // Line
        val linePath = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(linePath, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // Last point dot
        drawCircle(lineColor, radius = 4.dp.toPx(), center = pts.last())
        drawCircle(Color.White, radius = 2.dp.toPx(), center = pts.last())
    }
}

private suspend fun fetchExchangeRates(): RatesState {
    return withContext(Dispatchers.IO) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val labelSdf = SimpleDateFormat("dd.MM", Locale.US)

            // 10 data points: today + every 3 days back for 30 days
            val dateEntries = (0..27 step 3).map { daysAgo ->
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
                Pair(sdf.format(cal.time), labelSdf.format(cal.time))
            }.reversed()

            val fetched = coroutineScope {
                dateEntries.map { (apiDate, label) ->
                    async { Triple(apiDate, label, fetchSingleDate(apiDate)) }
                }.awaitAll()
            }

            val valid = fetched.filter { it.third != null }
            if (valid.isEmpty()) return@withContext RatesState.Error("Нет данных")

            val (rub, ils) = valid.last().third!!

            RatesState.Success(
                usdRub = rub,
                usdIls = ils,
                ilsRub = rub / ils,
                history = RateHistory(
                    labels = valid.map { it.second },
                    usdRub = valid.map { it.third!!.first },
                    usdIls = valid.map { it.third!!.second },
                    ilsRub = valid.map { it.third!!.first / it.third!!.second }
                )
            )
        } catch (e: Exception) {
            RatesState.Error("Нет соединения с интернетом")
        }
    }
}

private fun fetchSingleDate(date: String): Pair<Double, Double>? {
    return try {
        val url = URL("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$date/v1/currencies/usd.json")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        val json = conn.inputStream.bufferedReader().readText()
        val rub = Regex("\"rub\"\\s*:\\s*([\\d.]+)").find(json)?.groupValues?.get(1)?.toDoubleOrNull()
        val ils = Regex("\"ils\"\\s*:\\s*([\\d.]+)").find(json)?.groupValues?.get(1)?.toDoubleOrNull()
        if (rub != null && ils != null) Pair(rub, ils) else null
    } catch (e: Exception) { null }
}

@Composable
fun AppHeader(
    title: String,
    subtitle: String,
    showBack: Boolean,
    onBack: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(colors = listOf(HeaderStart, HeaderEnd)),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = 24.dp)
    ) {
        if (showBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (showBack) 0.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!showBack) {
                CuteDog(modifier = Modifier.size(110.dp))
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun MenuCard(item: MenuItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(item.iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1B1F),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

private fun buildMainMenuItems(
    onSheetsClick: () -> Unit,
    onInfoClick: () -> Unit,
    onAdvertisingClick: () -> Unit,
    onWhatsAppClick: () -> Unit
): List<MenuItem> = listOf(
    MenuItem("Наш сайт", Icons.Default.Language, Color(0xFF1565C0)) { ctx ->
        ctx.openUrl("https://dogisrael.com")
    },
    MenuItem("Поиск в интернете", Icons.Default.Search, Color(0xFF0F9D58)) { ctx ->
        val query = Uri.encode("Дрессировка собак Хайфа")
        ctx.openUrl("https://www.google.com/search?q=$query")
    },
    MenuItem("Реклама", Icons.Default.Campaign, Color(0xFF4285F4)) { _ -> onAdvertisingClick() },
    MenuItem("WhatsApp", Icons.Default.Chat, Color(0xFF25D366)) { _ -> onWhatsAppClick() },
    MenuItem("Тильда", Icons.Default.BarChart, Color(0xFF7B1FA2)) { ctx ->
        ctx.openUrl("https://stats.tilda.cc/projects/statistics/?projectid=7284816&from=sitesettings")
    },
    MenuItem("Телеграм-канал", Icons.AutoMirrored.Filled.Send, Color(0xFF039BE5)) { ctx ->
        val url = "https://t.me/DogIsraelTsafon"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage("org.telegram.messenger") }
        try { ctx.startActivity(intent) } catch (_: ActivityNotFoundException) { ctx.openUrl(url) }
    },
    MenuItem("Таблицы\nGoogle", Icons.Default.TableChart, Color(0xFF1A8754)) { _ -> onSheetsClick() },
    MenuItem("Информация", Icons.Default.Info, Color(0xFF0277BD)) { _ -> onInfoClick() }
)

private fun buildSheetsMenuItems(): List<MenuItem> = listOf(
    MenuItem("Дрессировка", Icons.Default.Pets, Color(0xFFE65100)) { ctx ->
        ctx.openInSheets("https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/edit?gid=1942354392#gid=1942354392")
    },
    MenuItem("Наличие мест", Icons.Default.EventAvailable, Color(0xFF00796B)) { ctx ->
        ctx.openInSheets("https://docs.google.com/spreadsheets/d/1A1XfeZiXSWQghndLuCrW6bOgenVGEfzuEzvICak1j-E/edit?gid=0#gid=0")
    },
    MenuItem("Доходы–Расходы", Icons.Default.AccountBalanceWallet, Color(0xFF2E7D32)) { ctx ->
        ctx.openInSheets("https://docs.google.com/spreadsheets/d/1dCrUUYrWhvaAyUO4nk3yhSgFGklSd4ZUF9f36PbsaLw/edit?gid=1971716229#gid=1971716229")
    },
    MenuItem("Занятия\nс собаками", Icons.Default.Schedule, Color(0xFF283593)) { ctx ->
        ctx.openInSheets("https://docs.google.com/spreadsheets/d/1k7usk6ZFkPL7x6-CFFfAr87kQvRkI9TBCZZqJFej0X4/edit?gid=1720494258#gid=1720494258")
    },
    MenuItem("Баланс", Icons.Default.AccountBalance, Color(0xFF6A1B9A)) { ctx ->
        ctx.openInSheets("https://docs.google.com/spreadsheets/d/1bF33tKU_BC7a-lSjT2FtCrgWqBFXzQcnDSI4Hts_Pds/edit?gid=1078406436#gid=1078406436")
    }
)

private fun buildInfoMenuItems(onExchangeRatesClick: () -> Unit, onTelegramFosteringClick: () -> Unit): List<MenuItem> = listOf(
    MenuItem("Услуги\nи цены", Icons.Default.Assignment, Color(0xFF6A1B9A)) { ctx ->
        ctx.openUrl("https://docs.google.com/spreadsheets/d/e/2PACX-1vQ9o9cy7RF1_AAEQRLuFWbp_5nLXl_q5WXYansWUuO7G-kGidbcb8_flK3-kRCQQvqiJCEnrJWHLr1h/pubhtml?widget=true&headers=false#gid=0?&single=true")
    },
    MenuItem("Как с нами\nсвязаться", Icons.Default.Phone, Color(0xFF00838F)) { ctx ->
        ctx.openUrl("https://docs.google.com/document/d/e/2PACX-1vTNnFYZ-YtSW-pv9-rolhastMhLNjWSnqgkOyaNBVflot5PtHbQBS7br3PY4nXXP5_iFx2aRHIr9v7x/pub")
    },
    MenuItem("Как к нам\nдобраться", Icons.Default.Navigation, Color(0xFFD84315)) { ctx ->
        ctx.openUrl("https://docs.google.com/document/d/e/2PACX-1vTNnFYZ-YtSW-pv9-rolhastMhLNjWSnqgkOyaNBVflot5PtHbQBS7br3PY4nXXP5_iFx2aRHIr9v7x/pub")
    },
    MenuItem("Фото\nпередержки", Icons.Default.PhotoLibrary, Color(0xFF558B2F)) { ctx ->
        ctx.openUrl("https://docs.google.com/document/d/e/2PACX-1vSugkyexuBk6HAV1pyH2SiOpkdiIH9M7y3e1e75zFiNR3MIB1V9adrwgAyNgmLGHjf-SrJIyR8Ac7vk/pub?widget=true&headers=false#gid=0?&single=true")
    },
    MenuItem("Места\nна передержке", Icons.Default.Bed, Color(0xFF37474F)) { ctx ->
        ctx.openUrl("https://docs.google.com/spreadsheets/d/e/2PACX-1vQid7xTSYIm4dvkLqAP9ewvl7Pq41CFAXDDGldLdVCSQEtpK8KSXTa6f-_MHP4Zt2ezLwVV-Se5BJoG/pubhtml?authuser=0&widget=true&headers=false#gid=0?&single=true")
    },
    MenuItem("Отзывы\nклиентов", Icons.Default.RateReview, Color(0xFFF57F17)) { ctx ->
        ctx.openUrl("https://www.google.com/search?newwindow=1&authuser=1&sxsrf=AHTn8zrbLwwF9wGEA5poSjJh8dL3L95WuQ:1744786758568&si=APYL9bs7Hg2KMLB-4tSoTdxuOx8BdRvHbByC_AuVpNyh0x2KzYKIj8l0ArlFedGpiIPiM2wpnktdq0_hR9JLB-xTz_vgQx7xk0mKoPgtE06bMRpG7JD0lxcEoXkR3fen8REzMIFCmpEzDu54OtHTV8GPWqqaQHMkFU9d06MYqRw3YY4eaCF3KxPYVzEvPNfnq0w1VvKbXb_1u4Bk0XhB8vBd-Va6Bt1KeJHAQTSeyDJipBeX-_DFsngm5CyL3Mfe2HROqSZCYOoZqoYOkDjqPYn37_9-yaswD5fRjNQyV-Q2S-q4xEG-U-htIFfSBPrbZAw5kUt7AqHk&q=DogIsrael+-+%D0%B4%D1%80%D0%B5%D1%81%D1%81%D0%B8%D1%80%D0%BE%D0%B2%D0%BA%D0%B0+%D0%B8+%D0%BF%D0%B5%D1%80%D0%B5%D0%B4%D0%B5%D1%80%D0%B6%D0%BA%D0%B0+%28%D0%B4%D0%BE%D0%BC%D0%B0%D1%88%D0%BD%D0%B8%D0%B9+%D0%BF%D0%B0%D0%BD%D1%81%D0%B8%D0%BE%D0%BD%29+%D0%B4%D0%BB%D1%8F+%D1%81%D0%BE%D0%B1%D0%B0%D0%BA.+%D0%A5%D0%B0%D0%B9%D1%84%D0%B0,+%D0%9A%D1%80%D0%B0%D0%B9%D0%BE%D1%82,+%D0%A1%D0%B5%D0%B2%D0%B5%D1%80+Reviews")
    },
    MenuItem("Фото из\nTelegram", Icons.Default.Slideshow, Color(0xFF039BE5)) { _ ->
        onTelegramFosteringClick()
    },
    MenuItem("Курсы\nвалют", Icons.Default.CurrencyExchange, Color(0xFF00695C)) { _ ->
        onExchangeRatesClick()
    }
)

private fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun Context.tryLaunchApp(packageName: String): Boolean {
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
    startActivity(intent)
    return true
}

@Composable
fun CuteDog(modifier: Modifier = Modifier) {
    val fur       = Color(0xFFD4943E)
    val ear       = Color(0xFF9A5C10)
    val earInner  = Color(0xFFE8A8A8)
    val muzzle    = Color(0xFFF5DFB0)
    val eyePupil  = Color(0xFF1A0800)
    val noseColor = Color(0xFF200800)
    val tongue    = Color(0xFFFF7096)
    val cheek     = Color(0xFFFF8080)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = minOf(cx, cy) * 0.82f

        fun flopEar(flip: Float) {
            val path = Path().apply {
                moveTo(cx + flip * r * 0.45f, cy - r * 0.65f)
                cubicTo(cx + flip * r * 1.10f, cy - r * 0.95f, cx + flip * r * 1.25f, cy + r * 0.25f, cx + flip * r * 0.70f, cy + r * 0.38f)
                cubicTo(cx + flip * r * 0.40f, cy + r * 0.45f, cx + flip * r * 0.20f, cy - r * 0.10f, cx + flip * r * 0.45f, cy - r * 0.65f)
                close()
            }
            drawPath(path, ear)
            val inner = Path().apply {
                moveTo(cx + flip * r * 0.48f, cy - r * 0.50f)
                cubicTo(cx + flip * r * 0.88f, cy - r * 0.68f, cx + flip * r * 0.95f, cy + r * 0.12f, cx + flip * r * 0.68f, cy + r * 0.22f)
                cubicTo(cx + flip * r * 0.52f, cy + r * 0.28f, cx + flip * r * 0.38f, cy - r * 0.05f, cx + flip * r * 0.48f, cy - r * 0.50f)
                close()
            }
            drawPath(inner, earInner.copy(alpha = 0.45f))
        }
        flopEar(-1f); flopEar(+1f)

        drawCircle(fur, r, Offset(cx, cy))
        drawCircle(cheek.copy(alpha = 0.30f), r * 0.20f, Offset(cx - r * 0.65f, cy + r * 0.08f))
        drawCircle(cheek.copy(alpha = 0.30f), r * 0.20f, Offset(cx + r * 0.65f, cy + r * 0.08f))
        drawOval(muzzle, topLeft = Offset(cx - r * 0.40f, cy + r * 0.14f), size = Size(r * 0.80f, r * 0.52f))

        fun eyebrow(ex: Float) {
            drawArc(color = ear, startAngle = 195f, sweepAngle = 150f, useCenter = false,
                topLeft = Offset(ex - r * 0.17f, cy - r * 0.44f), size = Size(r * 0.34f, r * 0.20f),
                style = Stroke(r * 0.055f, cap = StrokeCap.Round))
        }
        eyebrow(cx - r * 0.30f); eyebrow(cx + r * 0.30f)

        fun eye(ex: Float) {
            drawCircle(Color.White, r * 0.165f, Offset(ex, cy - r * 0.10f))
            drawCircle(eyePupil,    r * 0.105f, Offset(ex + r * 0.02f, cy - r * 0.08f))
            drawCircle(Color.White, r * 0.040f, Offset(ex + r * 0.06f, cy - r * 0.14f))
        }
        eye(cx - r * 0.30f); eye(cx + r * 0.30f)

        val nosePath = Path().apply {
            moveTo(cx, cy + r * 0.20f)
            cubicTo(cx - r * 0.16f, cy + r * 0.13f, cx - r * 0.16f, cy + r * 0.27f, cx, cy + r * 0.27f)
            cubicTo(cx + r * 0.16f, cy + r * 0.27f, cx + r * 0.16f, cy + r * 0.13f, cx, cy + r * 0.20f)
        }
        drawPath(nosePath, noseColor)
        drawCircle(Color.White.copy(alpha = 0.45f), r * 0.035f, Offset(cx + r * 0.08f, cy + r * 0.17f))

        drawArc(color = noseColor, startAngle = 150f, sweepAngle = 70f, useCenter = false,
            topLeft = Offset(cx - r * 0.40f, cy + r * 0.22f), size = Size(r * 0.34f, r * 0.18f),
            style = Stroke(r * 0.048f, cap = StrokeCap.Round))
        drawArc(color = noseColor, startAngle = -20f, sweepAngle = 70f, useCenter = false,
            topLeft = Offset(cx + r * 0.06f, cy + r * 0.22f), size = Size(r * 0.34f, r * 0.18f),
            style = Stroke(r * 0.048f, cap = StrokeCap.Round))

        val tonguePath = Path().apply {
            moveTo(cx - r * 0.17f, cy + r * 0.37f)
            cubicTo(cx - r * 0.24f, cy + r * 0.37f, cx - r * 0.28f, cy + r * 0.60f, cx, cy + r * 0.60f)
            cubicTo(cx + r * 0.28f, cy + r * 0.60f, cx + r * 0.24f, cy + r * 0.37f, cx + r * 0.17f, cy + r * 0.37f)
            close()
        }
        drawPath(tonguePath, tongue)
        drawLine(color = tongue.copy(alpha = 0.55f), start = Offset(cx, cy + r * 0.38f),
            end = Offset(cx, cy + r * 0.59f), strokeWidth = r * 0.032f, cap = StrokeCap.Round)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelegramFosteringScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<FosteringState>(FosteringState.Idle) }
    var loadingPage by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    val doSearch: () -> Unit = {
        if (query.isNotBlank()) {
            state = FosteringState.Loading
            loadingPage = 0
            scope.launch {
                state = fetchFosteringPhotos(query.trim()) { page -> loadingPage = page }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Фото передержек",
            subtitle = "Из Telegram-канала DogIsrael",
            showBack = true,
            onBack = onBack
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Имя собаки") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = doSearch,
                    enabled = query.isNotBlank() && state !is FosteringState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF039BE5))
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Найти", tint = Color.White)
                }
            }

            when (val s = state) {
                is FosteringState.Idle -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Введите имя собаки и нажмите 🔍",
                        color = Color(0xFF9E9E9E),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
                is FosteringState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Поиск... страница $loadingPage из 50",
                            color = Color(0xFF757575),
                            fontSize = 15.sp
                        )
                    }
                }
                is FosteringState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.message,
                        color = Color(0xFFB00020),
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                is FosteringState.Success -> key(s.posts) {
                    val pagerState = rememberPagerState { s.posts.size }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${s.posts.size}",
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 4.dp),
                            fontSize = 13.sp,
                            color = Color(0xFF9E9E9E)
                        )
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val post = s.posts[page]
                            val ctx = LocalContext.current
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx)
                                        .data(post.photoUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Фото передержки",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                if (post.caption.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                    ) {
                                        Text(
                                            text = post.caption,
                                            modifier = Modifier.padding(12.dp),
                                            fontSize = 13.sp,
                                            color = Color(0xFF1C1B1F),
                                            maxLines = 5,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────── Advertising ────────────────────────

@Composable
fun AdvertisingMenuScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val items = remember { buildAdvertisingMenuItems() }
    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "Реклама", subtitle = "Google Ads и Meta Ads", showBack = true, onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item -> MenuCard(item = item, onClick = { item.onClick(context) }) }
        }
    }
}

private fun buildAdvertisingMenuItems(): List<MenuItem> = listOf(
    MenuItem("Google\nРеклама", Icons.Default.Campaign, Color(0xFF4285F4)) { ctx ->
        val launched = ctx.tryLaunchApp("com.google.android.apps.adwords")
        if (!launched) ctx.openUrl("https://ads.google.com")
    },
    MenuItem("Meta Ads", Icons.Default.Insights, Color(0xFF1877F2)) { ctx ->
        val launched = ctx.tryLaunchApp("com.facebook.adsmanager")
        if (!launched) ctx.openUrl("https://www.facebook.com/adsmanager")
    }
)

// ──────────────────────── WhatsApp ────────────────────────

@Composable
fun WhatsAppMenuScreen(onBack: () -> Unit, onReminderClick: (Int) -> Unit) {
    val context = LocalContext.current
    val items = remember { buildWhatsAppMenuItems(onReminderClick) }
    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "WhatsApp", subtitle = "Шаблоны сообщений", showBack = true, onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item -> MenuCard(item = item, onClick = { item.onClick(context) }) }
        }
    }
}

private fun buildWhatsAppMenuItems(onReminderClick: (Int) -> Unit): List<MenuItem> = listOf(
    MenuItem("Напоминание\nо передержке 1", Icons.Default.Notifications, Color(0xFF25D366)) { _ ->
        onReminderClick(1)
    },
    MenuItem("Напоминание\nо передержке 2", Icons.Default.Notifications, Color(0xFF128C7E)) { _ ->
        onReminderClick(2)
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppReminderScreen(reminderType: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDateRangePickerState()
    var startMillis by remember { mutableStateOf<Long?>(null) }
    var endMillis   by remember { mutableStateOf<Long?>(null) }

    val whatsappOptions = remember {
        buildList {
            if (isAppInstalled(context, "com.whatsapp"))     add("com.whatsapp"     to "WhatsApp (Израиль)")
            if (isAppInstalled(context, "com.whatsapp.w4b")) add("com.whatsapp.w4b" to "WhatsApp Business (Россия)")
        }.ifEmpty { listOf("com.whatsapp" to "WhatsApp (Израиль)") }
    }
    var selectedApp by remember { mutableStateOf(whatsappOptions.first().first) }

    var contacts       by remember { mutableStateOf<List<WhatsAppContact>>(emptyList()) }
    var contactSearch  by remember { mutableStateOf("") }
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
    val message  = when (reminderType) {
        1 -> "Здравствуйте. Вы записаны на передержку с $startStr по $endStr. Если что-то изменится, сообщите, пожалуйста."
        else -> "Здравствуйте. Вы записаны на передержку с $startStr по $endStr. Во сколько вас ждать $startStr?"
    }
    val canSend = startMillis != null && endMillis != null && selectedContact != null

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startMillis = datePickerState.selectedStartDateMillis
                        endMillis   = datePickerState.selectedEndDateMillis
                        showDatePicker = false
                    },
                    enabled = datePickerState.selectedStartDateMillis != null &&
                              datePickerState.selectedEndDateMillis   != null
                ) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } }
        ) {
            DateRangePicker(
                state = datePickerState,
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
                            Text(
                                "Дата с",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                datePickerState.selectedStartDateMillis?.let { formatDate(it) } ?: "—",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            "–",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Дата по",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                datePickerState.selectedEndDateMillis?.let { formatDate(it) } ?: "—",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Напоминание $reminderType",
            subtitle = "Сообщение для WhatsApp",
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
            // Dates
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Период передержки", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF757575))
                    if (startMillis != null && endMillis != null) {
                        Text("с $startStr по $endStr", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = { showDatePicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B7D3A))
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.size(8.dp))
                        Text(if (startMillis == null) "Выбрать даты" else "Изменить даты", color = Color.White)
                    }
                }
            }

            // WhatsApp account (only if more than one available)
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

            // Contact picker
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
                                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF1B7D3A)),
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
            if (startMillis != null && endMillis != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Текст сообщения", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF2E7D32))
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

private fun isAppInstalled(context: Context, packageName: String): Boolean =
    try { context.packageManager.getPackageInfo(packageName, 0); true }
    catch (_: PackageManager.NameNotFoundException) { false }

private fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMMM", Locale("ru")).format(Date(millis))

private suspend fun readContacts(context: Context): List<WhatsAppContact> =
    withContext(Dispatchers.IO) {
        val list = mutableListOf<WhatsAppContact>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        ) ?: return@withContext list
        cursor.use {
            val nameCol  = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneCol = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name  = it.getString(nameCol)?.takeIf { n -> n.isNotBlank() } ?: continue
                val phone = it.getString(phoneCol)?.replace(Regex("[^+\\d]"), "") ?: continue
                if (phone.length >= 7) list.add(WhatsAppContact(name, phone))
            }
        }
        list.distinctBy { it.phone }.sortedBy { it.name }
    }

// ──────────────────────── Telegram fostering ────────────────────────

private suspend fun fetchFosteringPhotos(
    query: String,
    onProgress: (Int) -> Unit
): FosteringState {
    return withContext(Dispatchers.IO) {
        try {
            val allPosts = mutableListOf<FosteringPost>()
            val seen = mutableSetOf<String>()
            // Target only actual photo elements: class="tgme_widget_message_photo..."
            // This excludes avatar elements (class="tgme_widget_message_user_photo")
            // because "user_photo" ≠ "photo" at position 20 of the class name
            val photoRegex = Regex(
                """tgme_widget_message_photo(?!_user)[^"]*"[^>]*background-image:url\('([^']+)'\)"""
            )
            val textRegex = Regex("""js-message_text[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            val htmlTagRegex = Regex("<[^>]+>")
            val msgIdRegex = Regex("""data-post="[^/]*/(\d+)"""")

            var beforeId: String? = null
            var fetchedAnyPage = false

            for (page in 0 until 50) {
                onProgress(page + 1)
                val urlStr = "https://t.me/s/DogIsraelTsafon" +
                    (beforeId?.let { "?before=$it" } ?: "")
                val html = fetchHtml(urlStr) ?: break
                fetchedAnyPage = true

                val newBefore = msgIdRegex.findAll(html)
                    .mapNotNull { it.groupValues[1].toLongOrNull() }
                    .minOrNull()?.toString()

                html.split("js-widget_message_wrap").drop(1).forEach { block ->
                    val photoUrls = photoRegex.findAll(block).map { it.groupValues[1] }.toList()
                    if (photoUrls.isEmpty()) return@forEach

                    val rawText = textRegex.find(block)?.groupValues?.get(1) ?: ""
                    val text = rawText
                        .replace(htmlTagRegex, " ")
                        .replace("&amp;", "&").replace("&lt;", "<")
                        .replace("&gt;", ">").replace("&nbsp;", " ").replace("&#39;", "'")
                        .replace(Regex("\\s+"), " ").trim()

                    if (text.contains(query, ignoreCase = true)) {
                        photoUrls.forEach { url ->
                            if (seen.add(url)) allPosts.add(FosteringPost(url, text))
                        }
                    }
                }

                if (newBefore == null || newBefore == beforeId) break
                beforeId = newBefore
            }

            when {
                !fetchedAnyPage -> FosteringState.Error("Нет соединения с интернетом")
                allPosts.isEmpty() -> FosteringState.Error("Фото с именем «$query» не найдены")
                else -> FosteringState.Success(allPosts)
            }
        } catch (e: Exception) {
            FosteringState.Error("Ошибка: ${e.message ?: "Нет соединения"}")
        }
    }
}

private fun fetchHtml(url: String): String? {
    return try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/121.0.0.0 Mobile Safari/537.36"
        )
        if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
        conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    } catch (_: Exception) { null }
}

private fun Context.openInSheets(url: String) {
    val urlWithAccount = Uri.parse(url).buildUpon()
        .appendQueryParameter("authuser", "vlad.yamp@gmail.com")
        .build().toString()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlWithAccount)).apply {
        setPackage("com.google.android.apps.docs")
    }
    try { startActivity(intent) } catch (_: ActivityNotFoundException) { openUrl(urlWithAccount) }
}
