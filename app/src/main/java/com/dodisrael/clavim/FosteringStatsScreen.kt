package com.dodisrael.clavim

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val STATS_CSV_URL =
    "https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/export?format=csv&gid=1942354392"

// Month key: "08.25" → 2508, used for chronological comparison
private fun monthKey(s: String): Int {
    val p = s.trim().split(".")
    val mm = p.getOrNull(0)?.toIntOrNull() ?: 0
    val yy = p.getOrNull(1)?.toIntOrNull() ?: 0
    return yy * 100 + mm
}

private val CUTOFF_KEY = monthKey("08.25")

private data class StatsRow(
    val month: String,
    val dogDays: Int,
    val avgDogs: Double,
    val total: Int,
    val clients: Int,
    val newClients: Int,
    val repeatClients: Int
)

private enum class StatsCol(
    val label: String,
    val chipLabel: String,
    val color: Color,
    val getter: (StatsRow) -> Double,
    val fmt: (Double) -> String
) {
    DOG_DAYS(
        "Собако-дни", "Соб. дни", Color(0xFF1565C0),
        { it.dogDays.toDouble() },
        { it.toInt().toString() }
    ),
    AVG_DOGS(
        "Ср. собак/день", "Ср./день", Color(0xFF00897B),
        { it.avgDogs },
        { "%.1f".format(it) }
    ),
    TOTAL(
        "Сумма ₪", "Сумма", Color(0xFF2E7D32),
        { it.total.toDouble() },
        { java.util.Locale.US.let { l -> "%,d".format(it.toInt()) } + " ₪" }
    ),
    CLIENTS(
        "Клиентов", "Клиент.", Color(0xFF6A1B9A),
        { it.clients.toDouble() },
        { it.toInt().toString() }
    ),
    NEW_CLIENTS(
        "Новых", "Новых", Color(0xFFE65100),
        { it.newClients.toDouble() },
        { it.toInt().toString() }
    ),
    REPEAT(
        "Повторных", "Повтор.", Color(0xFFAD1457),
        { it.repeatClients.toDouble() },
        { it.toInt().toString() }
    )
}

private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var inQuotes = false
    val buf = StringBuilder()
    for (c in line) {
        when {
            c == '"'              -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> { result.add(buf.toString()); buf.clear() }
            else                  -> buf.append(c)
        }
    }
    result.add(buf.toString())
    return result
}

private suspend fun fetchStatsData(): List<StatsRow> = withContext(Dispatchers.IO) {
    val conn = URL("$STATS_CSV_URL&t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout    = 15_000
    conn.instanceFollowRedirects = true
    val csv = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    conn.disconnect()
    csv.lines().drop(1).mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val c = parseCsvLine(line)
        val month = c.getOrNull(0)?.trim() ?: return@mapNotNull null
        if (month.isBlank() || monthKey(month) < CUTOFF_KEY) return@mapNotNull null
        // num: strips non-digits (integers). numDec: replaces comma→dot first (decimal).
        fun num(idx: Int)    = c.getOrNull(idx)?.trim()?.replace(Regex("[^0-9.]"), "")
        fun numDec(idx: Int) = c.getOrNull(idx)?.trim()?.replace(",", ".")?.replace(Regex("[^0-9.]"), "")
        StatsRow(
            month         = month,
            dogDays       = num(1)?.toIntOrNull()     ?: 0,
            avgDogs       = numDec(2)?.toDoubleOrNull() ?: 0.0,
            total         = num(3)?.toIntOrNull()    ?: 0,
            clients       = num(5)?.toIntOrNull()    ?: 0,
            newClients    = num(6)?.toIntOrNull()    ?: 0,
            repeatClients = num(7)?.toIntOrNull()    ?: 0
        )
    }
}

// ── Main screen ───────────────────────────────────────────────────────────────

@Composable
fun FosteringStatsScreen(onBack: () -> Unit, onClientMonthClick: (month: Int, year: Int) -> Unit = { _, _ -> }) {
    var rows      by remember { mutableStateOf<List<StatsRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error     by remember { mutableStateOf("") }
    var selCol    by remember { mutableStateOf<StatsCol?>(StatsCol.TOTAL) }
    val currentMonth = remember {
        java.util.Calendar.getInstance().let { cal ->
            "%02d.%02d".format(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR) % 100)
        }
    }

    LaunchedEffect(Unit) {
        try   { rows = fetchStatsData() }
        catch (e: Exception) { error = e.message ?: "Ошибка загрузки" }
        finally { isLoading = false }
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader("Статистика", "", showBack = true, onBack = onBack, compact = true)
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF388E3C))
            }
            error.isNotBlank() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(error, color = Color(0xFFC62828), textAlign = TextAlign.Center)
            }
            rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Нет данных", color = Color(0xFF9E9E9E))
            }
            else -> StatsBody(rows, selCol, currentMonth, onClientMonthClick) { col -> selCol = if (selCol == col) null else col }
        }
    }
}

// ── Body ──────────────────────────────────────────────────────────────────────

@Composable
private fun StatsBody(
    rows: List<StatsRow>,
    selCol: StatsCol?,
    currentMonth: String,
    onClientMonthClick: (month: Int, year: Int) -> Unit,
    onColClick: (StatsCol) -> Unit
) {
    val density = LocalDensity.current
    val minChartPx = with(density) { 80.dp.toPx() }
    val maxChartPx = with(density) { 380.dp.toPx() }
    var chartHeightPx by remember { mutableFloatStateOf(with(density) { 200.dp.toPx() }) }

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Column selector chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatsCol.values().forEach { col ->
                val active = col == selCol
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (active) col.color else col.color.copy(alpha = 0.10f))
                        .clickable { onColClick(col) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        col.chipLabel,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) Color.White else col.color
                    )
                }
            }
        }

        // Bar chart — shown only when a column is selected
        if (selCol != null) {
            BarChart(
                rows = rows,
                col  = selCol,
                currentMonth = currentMonth,
                onBarClick = { monthStr ->
                    val p  = monthStr.split(".")
                    val m  = p.getOrNull(0)?.toIntOrNull()
                    val yy = p.getOrNull(1)?.toIntOrNull()
                    if (m != null && yy != null) onClientMonthClick(m, 2000 + yy)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { chartHeightPx.toDp() })
                    .padding(horizontal = 12.dp)
            )

            // Draggable divider between chart and table
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            chartHeightPx = (chartHeightPx + delta).coerceIn(minChartPx, maxChartPx)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFBBBBBB))
                )
            }
        }

        // Frozen header row — FrozenMonthHeader and DataColumnsHeader side by side
        // IntrinsicSize.Max makes both cells the same height
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            FrozenMonthHeader()
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFFBBBBBB)))
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                DataColumnsHeader(selCol)
            }
        }
        HorizontalDivider(color = Color(0xFF388E3C), thickness = 1.dp)

        // Data rows: frozen month column + scrollable data columns, synced vertically
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(W_MONTH).fillMaxHeight().verticalScroll(vScroll)) {
                rows.forEachIndexed { i, row ->
                    FrozenMonthCell(row, even = i % 2 == 0, isCurrent = row.month == currentMonth)
                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                }
                Spacer(Modifier.height(20.dp))
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFFBBBBBB)))
            Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                Column(Modifier.verticalScroll(vScroll)) {
                    rows.forEachIndexed { i, row ->
                        DataColumnsRow(row = row, even = i % 2 == 0, selCol = selCol, isCurrent = row.month == currentMonth)
                        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

// ── Table ─────────────────────────────────────────────────────────────────────

private val W_MONTH = 62.dp
private val W_COL   = 76.dp
private val ROW_PAD = 9.dp  // vertical padding — must match in frozen + data columns

@Composable
private fun FrozenMonthHeader() {
    Box(
        Modifier
            .width(W_MONTH)
            .fillMaxHeight()
            .background(Color(0xFF1B5E20))
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Месяц", fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FrozenMonthCell(row: StatsRow, even: Boolean, isCurrent: Boolean) {
    Box(
        Modifier
            .width(W_MONTH)
            .background(
                when {
                    isCurrent -> Color(0xFFFFF3E0)
                    even      -> Color(0xFFF5F7FA)
                    else      -> Color.White
                }
            )
            .then(if (isCurrent) Modifier.padding(start = 3.dp) else Modifier)
            .padding(horizontal = 8.dp, vertical = ROW_PAD)
    ) {
        Text(
            row.month,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Bold,
            color      = if (isCurrent) Color(0xFFE65100) else Color(0xFF1A237E)
        )
    }
}

@Composable
private fun DataColumnsHeader(selCol: StatsCol?) {
    Row(
        Modifier
            .background(Color(0xFF1B5E20))
            .padding(vertical = 10.dp)
    ) {
        StatsCol.values().forEach { col ->
            val highlight = col == selCol
            Box(
                Modifier
                    .width(W_COL)
                    .then(if (highlight) Modifier.background(col.color.copy(alpha = 0.50f)) else Modifier)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(col.label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, textAlign = TextAlign.Center, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun DataColumnsRow(row: StatsRow, even: Boolean, selCol: StatsCol?, isCurrent: Boolean) {
    Row(
        Modifier
            .background(
                when {
                    isCurrent -> Color(0xFFFFF3E0)
                    even      -> Color(0xFFF5F7FA)
                    else      -> Color.White
                }
            )
            .padding(vertical = ROW_PAD)
    ) {
        StatsCol.values().forEach { col ->
            val active = col == selCol
            Box(
                Modifier
                    .width(W_COL)
                    .then(if (active) Modifier.background(col.color.copy(alpha = 0.07f)) else Modifier)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    col.fmt(col.getter(row)),
                    fontSize = 13.sp,
                    color = if (active) col.color else Color(0xFF212121),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// ── Bar chart ─────────────────────────────────────────────────────────────────

private val CHART_SLOT_DP = 44.dp

@Composable
private fun BarChart(
    rows: List<StatsRow>,
    col: StatsCol,
    currentMonth: String,
    onBarClick: (String) -> Unit,
    modifier: Modifier
) {
    val currentIdx = remember(rows, currentMonth) { rows.indexOfFirst { it.month == currentMonth } }
    val values     = remember(rows, col) { rows.map { col.getter(it) } }
    val maxVal     = remember(values) { values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0 }
    val totalWidth = CHART_SLOT_DP * rows.size
    val hScroll    = rememberScrollState()
    val density    = LocalDensity.current

    Card(
        modifier = modifier,
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 10.dp, start = 10.dp, end = 10.dp, bottom = 2.dp)
        ) {
            Text(
                col.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = col.color, modifier = Modifier.padding(bottom = 4.dp)
            )
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val visiblePx = with(density) { maxWidth.toPx() }

                LaunchedEffect(currentIdx, visiblePx) {
                    if (currentIdx >= 0 && visiblePx > 0f) {
                        val slotPx  = with(density) { CHART_SLOT_DP.toPx() }
                        val centerPx = currentIdx * slotPx + slotPx / 2f
                        val target  = (centerPx - visiblePx / 2f).coerceAtLeast(0f)
                        hScroll.scrollTo(target.toInt())
                    }
                }

            Box(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(hScroll)
            ) {
                Canvas(
                    Modifier
                        .width(totalWidth)
                        .fillMaxHeight()
                        .pointerInput(rows) {
                            detectTapGestures { offset ->
                                val slotW = size.width.toFloat() / rows.size.coerceAtLeast(1)
                                val idx   = (offset.x / slotW).toInt().coerceIn(0, rows.lastIndex)
                                onBarClick(rows[idx].month)
                            }
                        }
                ) {
                    val n = rows.size
                    if (n == 0) return@Canvas

                    val padBottom = 20.dp.toPx()
                    val padTop    = 20.dp.toPx()
                    val chartH    = (size.height - padBottom - padTop).coerceAtLeast(1f)
                    val slotW     = size.width / n
                    val barW      = slotW * 0.55f

                    val labelPaint = Paint().apply {
                        textAlign   = Paint.Align.CENTER
                        textSize    = 8.sp.toPx()
                        isAntiAlias = true
                        color       = android.graphics.Color.argb(160, 80, 80, 80)
                    }
                    val valuePaint = Paint().apply {
                        textAlign      = Paint.Align.CENTER
                        textSize       = 8.sp.toPx()
                        isFakeBoldText = true
                        isAntiAlias    = true
                        color          = col.color.copy(alpha = 0.9f).toArgb()
                    }

                    // Grid lines
                    repeat(3) { k ->
                        val gy = padTop + chartH * (1f - (k + 1) / 4f)
                        drawLine(Color(0xFFE8E8E8), Offset(0f, gy), Offset(size.width, gy), 0.7.dp.toPx())
                    }
                    // Baseline
                    drawLine(
                        Color(0xFFBBBBBB),
                        Offset(0f, padTop + chartH),
                        Offset(size.width, padTop + chartH),
                        1.dp.toPx()
                    )

                    rows.forEachIndexed { idx, row ->
                        val isCurrent = row.month == currentMonth
                        val v    = col.getter(row)
                        val barH = ((v / maxVal) * chartH).toFloat().coerceAtLeast(2.dp.toPx())
                        val cx   = slotW * idx + slotW / 2f
                        val barTop = padTop + chartH - barH

                        drawRoundRect(
                            color        = if (isCurrent) Color(0xFFE65100) else col.color.copy(alpha = 0.65f),
                            topLeft      = Offset(cx - barW / 2f, barTop),
                            size         = Size(barW, barH),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )

                        // Month label below bar — bold orange for current month
                        val lp = if (isCurrent) Paint().apply {
                            textAlign      = Paint.Align.CENTER
                            textSize       = 8.sp.toPx()
                            isFakeBoldText = true
                            isAntiAlias    = true
                            color          = android.graphics.Color.argb(220, 230, 81, 0)
                        } else labelPaint
                        drawContext.canvas.nativeCanvas.drawText(row.month, cx, size.height, lp)

                        // Small dot above current month bar
                        if (isCurrent) {
                            drawCircle(
                                color  = Color(0xFFE65100),
                                radius = 3.dp.toPx(),
                                center = Offset(cx, barTop - 8.dp.toPx())
                            )
                        }

                        // Value label above bar
                        if (barH > 16.dp.toPx()) {
                            drawContext.canvas.nativeCanvas.drawText(
                                col.fmt(v), cx, barTop - (if (isCurrent) 14.dp else 4.dp).toPx(), valuePaint
                            )
                        }
                    }
                }
            }
            } // BoxWithConstraints
        }
    }
}
