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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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

private const val BALANCE_CSV_URL =
    "https://docs.google.com/spreadsheets/d/1dCrUUYrWhvaAyUO4nk3yhSgFGklSd4ZUF9f36PbsaLw/export?format=csv&gid=1971716229"

private data class BalRow(
    val month: String,
    val values: List<Double>  // 0=Дрессировка 1=Пансион 2=Лакомство 3=Танцы 4=Помощь 5=Итого
)

private enum class BalCol(
    val label: String,
    val chipLabel: String,
    val color: Color,
    val colIdx: Int,
    val fmt: (Double) -> String
) {
    TRAINING("Дрессировка", "Дрес.",   Color(0xFF1565C0), 0, { "%.0f ₪".format(it) }),
    BOARDING("Пансион",     "Пансион", Color(0xFF2E7D32), 1, { "%.0f ₪".format(it) }),
    TREATS  ("Лакомство",   "Лаком.",  Color(0xFFE65100), 2, { "%.0f ₪".format(it) }),
    DANCING ("Танцы",       "Танцы",   Color(0xFF6A1B9A), 3, { "%.0f ₪".format(it) }),
    HELP    ("Помощь",      "Помощь",  Color(0xFF00695C), 4, { "%.0f ₪".format(it) }),
    TOTAL   ("Итого",       "Итого",   Color(0xFF37474F), 5, { "%.0f ₪".format(it) })
}

private val BAL_PIE_COLS = BalCol.values().filter { it != BalCol.TOTAL }

private fun parseBalCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var inQuotes = false
    val buf = StringBuilder()
    for (c in line) {
        when {
            c == '"'               -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> { result.add(buf.toString()); buf.clear() }
            else                   -> buf.append(c)
        }
    }
    result.add(buf.toString())
    return result
}

private fun normalizeBalMonth(raw: String): String {
    val parts = raw.trim().split(".")
    val mm    = parts.getOrNull(0) ?: return raw
    val yy    = parts.getOrNull(1) ?: return raw
    return "$mm.${if (yy.length == 4) yy.takeLast(2) else yy}"
}

private fun parseNum(s: String?): Double {
    val raw = s?.trim() ?: return 0.0
    val neg = raw.startsWith("-")
    val v   = raw.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return 0.0
    return if (neg) -v else v
}

private suspend fun fetchBalanceData(): List<BalRow> = withContext(Dispatchers.IO) {
    val conn = URL("$BALANCE_CSV_URL&t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
    conn.connectTimeout  = 15_000
    conn.readTimeout     = 15_000
    conn.instanceFollowRedirects = true
    val csv = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    conn.disconnect()
    csv.lines().drop(1).mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val c        = parseBalCsvLine(line)
        val rawMonth = c.getOrNull(0)?.trim() ?: return@mapNotNull null
        if (rawMonth.isBlank()) return@mapNotNull null
        BalRow(
            month  = normalizeBalMonth(rawMonth),
            values = listOf(
                parseNum(c.getOrNull(1)),
                parseNum(c.getOrNull(2)),
                parseNum(c.getOrNull(3)),
                parseNum(c.getOrNull(4)),
                parseNum(c.getOrNull(5)),
                parseNum(c.getOrNull(6))
            )
        )
    }
}

// ── Main screen ───────────────────────────────────────────────────────────────

@Composable
fun BalanceScreen(onBack: () -> Unit) {
    var rows      by remember { mutableStateOf<List<BalRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error     by remember { mutableStateOf("") }
    var selCol    by remember { mutableStateOf(BalCol.TOTAL) }

    val cal          = remember { java.util.Calendar.getInstance() }
    val currentMonth = remember {
        "%02d.%02d".format(
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.YEAR) % 100
        )
    }

    LaunchedEffect(Unit) {
        try {
            rows = fetchBalanceData()
        } catch (e: Exception) {
            error = e.message ?: "Ошибка загрузки"
        } finally {
            isLoading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(
            title    = "Баланс",
            subtitle = "",
            showBack = true,
            onBack   = onBack,
            compact  = true
        )
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF37474F))
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
            else -> BalanceBody(
                rows         = rows,
                selCol       = selCol,
                currentMonth = currentMonth,
                onColClick   = { selCol = it }
            )
        }
    }
}

// ── Body ──────────────────────────────────────────────────────────────────────

@Composable
private fun BalanceBody(
    rows: List<BalRow>,
    selCol: BalCol,
    currentMonth: String,
    onColClick: (BalCol) -> Unit
) {
    val density       = LocalDensity.current
    val minChartPx    = with(density) { 80.dp.toPx() }
    val maxChartPx    = with(density) { 380.dp.toPx() }
    var chartHeightPx by remember { mutableFloatStateOf(with(density) { 160.dp.toPx() }) }
    val hScroll       = rememberScrollState()
    val vScroll       = rememberScrollState()

    var selectedBarMonth by remember { mutableStateOf<String?>(null) }
    val selectedRow = remember(selectedBarMonth, rows) {
        rows.firstOrNull { it.month == selectedBarMonth }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Column chip selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BalCol.values().forEach { col ->
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
                        fontSize   = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color      = if (active) Color.White else col.color
                    )
                }
            }
        }

        // Bar chart
        BalBarChart(
            rows         = rows,
            col          = selCol,
            currentMonth = currentMonth,
            onBarTap     = { month -> selectedBarMonth = month },
            modifier     = Modifier
                .fillMaxWidth()
                .height(with(density) { chartHeightPx.toDp() })
                .padding(horizontal = 12.dp)
        )

        // Draggable divider
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

        if (selectedRow != null) {
            // Pie chart section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F7FA))
                    .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Структура за ${selectedRow.month}",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color(0xFF37474F),
                    modifier   = Modifier.weight(1f)
                )
                IconButton(
                    onClick  = { selectedBarMonth = null },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.TableChart,
                        contentDescription = "К таблице",
                        tint     = Color(0xFF37474F),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            HorizontalDivider(color = Color(0xFFBBBBBB), thickness = 0.5.dp)
            BalPieView(
                row      = selectedRow,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Table
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                BalMonthHeader()
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFFBBBBBB)))
                Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                    BalDataHeader(selCol)
                }
            }
            HorizontalDivider(color = Color(0xFF37474F), thickness = 1.dp)

            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(W_BAL_MONTH).fillMaxHeight().verticalScroll(vScroll)) {
                    rows.forEachIndexed { i, row ->
                        BalMonthCell(row, even = i % 2 == 0, isCurrent = row.month == currentMonth)
                        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                    }
                    Spacer(Modifier.height(20.dp))
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFFBBBBBB)))
                Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                    Column(Modifier.verticalScroll(vScroll)) {
                        rows.forEachIndexed { i, row ->
                            BalDataRow(row, even = i % 2 == 0, selCol = selCol, isCurrent = row.month == currentMonth)
                            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

// ── Bar chart ─────────────────────────────────────────────────────────────────

private val BAL_SLOT_DP = 52.dp

@Composable
private fun BalBarChart(
    rows: List<BalRow>,
    col: BalCol,
    currentMonth: String,
    onBarTap: (String) -> Unit,
    modifier: Modifier
) {
    val values    = remember(rows, col) { rows.map { it.values[col.colIdx] } }
    val maxVal    = remember(values) { values.filter { it > 0 }.maxOrNull() ?: 1.0 }

    // Reference lines exclude current (incomplete) month
    val histVals  = remember(rows, col, currentMonth) {
        rows.filter { it.month != currentMonth }.map { it.values[col.colIdx] }.filter { it > 0 }
    }
    val refMin    = remember(histVals) { histVals.minOrNull() ?: 0.0 }
    val refAvg    = remember(histVals) { if (histVals.isEmpty()) 0.0 else histVals.average() }
    val refMax    = remember(histVals) { histVals.maxOrNull() ?: 0.0 }

    val totalWidth = BAL_SLOT_DP * rows.size
    val hScroll    = rememberScrollState()
    val density    = LocalDensity.current

    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 10.dp, start = 10.dp, end = 10.dp, bottom = 2.dp)
        ) {
            Text(
                col.label,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = col.color,
                modifier   = Modifier.padding(bottom = 4.dp)
            )
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val visiblePx = with(density) { maxWidth.toPx() }

                LaunchedEffect(visiblePx) {
                    if (visiblePx > 0f) hScroll.scrollTo(Int.MAX_VALUE)
                }

                Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                    Canvas(
                        Modifier
                            .width(totalWidth)
                            .fillMaxHeight()
                            .pointerInput(rows) {
                                detectTapGestures { offset ->
                                    val slotW = size.width.toFloat() / rows.size.coerceAtLeast(1)
                                    val idx   = (offset.x / slotW).toInt().coerceIn(0, rows.lastIndex)
                                    onBarTap(rows[idx].month)
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
                            textSize       = 7.5.sp.toPx()
                            isFakeBoldText = true
                            isAntiAlias    = true
                            color          = col.color.copy(alpha = 0.9f).toArgb()
                        }

                        // Background grid
                        repeat(3) { k ->
                            val gy = padTop + chartH * (1f - (k + 1) / 4f)
                            drawLine(Color(0xFFE8E8E8), Offset(0f, gy), Offset(size.width, gy), 0.7.dp.toPx())
                        }
                        drawLine(
                            Color(0xFFBBBBBB),
                            Offset(0f, padTop + chartH),
                            Offset(size.width, padTop + chartH),
                            1.dp.toPx()
                        )

                        // Min / Avg / Max dashed reference lines (no labels)
                        if (histVals.isNotEmpty()) {
                            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
                            listOf(
                                refMin to Color(0xFF43A047),
                                refAvg to Color(0xFF1565C0),
                                refMax to Color(0xFFC62828)
                            ).forEach { (v, clr) ->
                                val yRef = padTop + chartH * (1f - (v / maxVal).toFloat())
                                drawLine(
                                    color       = clr.copy(alpha = 0.55f),
                                    start       = Offset(0f, yRef),
                                    end         = Offset(size.width, yRef),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect  = dash
                                )
                            }
                        }

                        // Bars
                        rows.forEachIndexed { idx, row ->
                            val isCurrent = row.month == currentMonth
                            val v         = row.values[col.colIdx]
                            val barH      = ((v / maxVal) * chartH).toFloat().coerceAtLeast(if (v > 0) 2.dp.toPx() else 0f)
                            val cx        = slotW * idx + slotW / 2f
                            val barTop    = padTop + chartH - barH

                            if (barH > 0f) {
                                drawRoundRect(
                                    color        = if (isCurrent) Color(0xFFE65100) else col.color.copy(alpha = 0.65f),
                                    topLeft      = Offset(cx - barW / 2f, barTop),
                                    size         = Size(barW, barH),
                                    cornerRadius = CornerRadius(3.dp.toPx())
                                )
                                drawRoundRect(
                                    color        = Color.Black,
                                    topLeft      = Offset(cx - barW / 2f, barTop),
                                    size         = Size(barW, barH),
                                    cornerRadius = CornerRadius(3.dp.toPx()),
                                    style        = Stroke(width = 0.8.dp.toPx())
                                )
                            }

                            val lp = if (isCurrent) Paint().apply {
                                textAlign      = Paint.Align.CENTER
                                textSize       = 8.sp.toPx()
                                isFakeBoldText = true
                                isAntiAlias    = true
                                color          = android.graphics.Color.argb(220, 230, 81, 0)
                            } else labelPaint
                            drawContext.canvas.nativeCanvas.drawText(row.month, cx, size.height, lp)

                            if (isCurrent) {
                                drawCircle(
                                    color  = Color(0xFFE65100),
                                    radius = 3.dp.toPx(),
                                    center = Offset(cx, barTop - 8.dp.toPx())
                                )
                            }

                            if (barH > 16.dp.toPx()) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    col.fmt(v), cx,
                                    barTop - (if (isCurrent) 14.dp else 4.dp).toPx(),
                                    valuePaint
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Pie chart ─────────────────────────────────────────────────────────────────

@Composable
private fun BalPieView(row: BalRow, modifier: Modifier = Modifier) {
    val slices = remember(row) {
        BAL_PIE_COLS.mapNotNull { col ->
            val v = row.values[col.colIdx]
            if (v <= 0) null else Triple(col.label, v, col.color)
        }
    }
    val total = remember(slices) { slices.sumOf { it.second } }

    if (slices.isEmpty() || total <= 0.0) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Нет данных", color = Color(0xFF9E9E9E))
        }
        return
    }

    val gapDeg          = 1.8f
    val minSweepForLabel = 10f
    val namePaint = remember { Paint().apply { isAntiAlias = true } }
    val valPaint  = remember { Paint().apply { isAntiAlias = true } }

    Canvas(modifier = modifier) {
        val cx      = size.width / 2f
        val cy      = size.height / 2f
        val sw      = minOf(size.width, size.height) * 0.14f
        val dR      = size.width * 0.20f
        val oe      = dR + sw / 2f
        val lineLen = size.width * 0.05f
        val tickLen = size.width * 0.04f
        val lineW   = 1.4.dp.toPx()
        val arcTL   = Offset(cx - dR, cy - dR)
        val arcSz   = Size(dR * 2f, dR * 2f)

        namePaint.textSize = 10.5.sp.toPx()
        namePaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        valPaint.textSize  = 9.sp.toPx()
        valPaint.typeface  = android.graphics.Typeface.DEFAULT

        var startAngle = -90f
        slices.forEach { (label, value, color) ->
            val sweep = (value / total * 360f - gapDeg).toFloat().coerceAtLeast(0.5f)

            drawArc(
                color       = color.copy(alpha = 0.84f),
                startAngle  = startAngle,
                sweepAngle  = sweep,
                useCenter   = false,
                topLeft     = arcTL,
                size        = arcSz,
                style       = Stroke(width = sw)
            )

            if (sweep >= minSweepForLabel) {
                val midRad  = Math.toRadians((startAngle + sweep / 2f).toDouble())
                val cos     = Math.cos(midRad).toFloat()
                val sin     = Math.sin(midRad).toFloat()
                val isRight = cos >= 0f

                val x1 = cx + oe * cos
                val y1 = cy + oe * sin
                val x2 = cx + (oe + lineLen) * cos
                val y2 = cy + (oe + lineLen) * sin
                val x3 = x2 + (if (isRight) tickLen else -tickLen)

                drawLine(color.copy(alpha = 0.6f), Offset(x1, y1), Offset(x2, y2), lineW)
                drawLine(color.copy(alpha = 0.6f), Offset(x2, y2), Offset(x3, y2), lineW)

                drawIntoCanvas { canvas ->
                    val gap    = 3.dp.toPx()
                    val textX  = x3 + (if (isRight) gap else -gap)
                    val align  = if (isRight) Paint.Align.LEFT else Paint.Align.RIGHT

                    namePaint.color     = color.toArgb()
                    namePaint.textAlign = align
                    valPaint.color      = color.copy(alpha = 0.75f).toArgb()
                    valPaint.textAlign  = align

                    val availW = if (isRight) (size.width - textX) else textX
                    val displayName = if (namePaint.measureText(label) <= availW) label
                        else {
                            var t = label
                            while (t.isNotEmpty() && namePaint.measureText("$t…") > availW) t = t.dropLast(1)
                            if (t.isEmpty()) "…" else "$t…"
                        }
                    val valText = "%.0f ₪".format(value)

                    val fm    = namePaint.fontMetrics
                    val lineH = fm.descent - fm.ascent
                    val c2b   = -(fm.ascent + fm.descent) / 2f
                    canvas.nativeCanvas.drawText(displayName, textX, (y2 - lineH / 2f) + c2b, namePaint)
                    canvas.nativeCanvas.drawText(valText,     textX, (y2 + lineH / 2f) + c2b, valPaint)
                }
            }

            startAngle += sweep + gapDeg
        }
    }
}

// ── Table ─────────────────────────────────────────────────────────────────────

private val W_BAL_MONTH = 62.dp
private val W_BAL_COL   = 84.dp
private val BAL_ROW_PAD = 9.dp

@Composable
private fun BalMonthHeader() {
    Box(
        Modifier
            .width(W_BAL_MONTH)
            .fillMaxHeight()
            .background(Color(0xFF263238))
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Месяц", fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = Color.White, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BalMonthCell(row: BalRow, even: Boolean, isCurrent: Boolean) {
    Box(
        Modifier
            .width(W_BAL_MONTH)
            .background(
                when {
                    isCurrent -> Color(0xFFFFF3E0)
                    even      -> Color(0xFFF5F7FA)
                    else      -> Color.White
                }
            )
            .then(if (isCurrent) Modifier.padding(start = 3.dp) else Modifier)
            .padding(horizontal = 8.dp, vertical = BAL_ROW_PAD)
    ) {
        Text(
            row.month,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
            color      = if (isCurrent) Color(0xFFE65100) else Color(0xFF1A237E)
        )
    }
}

@Composable
private fun BalDataHeader(selCol: BalCol) {
    Row(
        Modifier
            .background(Color(0xFF263238))
            .padding(vertical = 10.dp)
    ) {
        BalCol.values().forEach { col ->
            Box(
                Modifier
                    .width(W_BAL_COL)
                    .then(if (col == selCol) Modifier.background(col.color.copy(alpha = 0.45f)) else Modifier)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    col.label, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, textAlign = TextAlign.Center, lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
private fun BalDataRow(row: BalRow, even: Boolean, selCol: BalCol, isCurrent: Boolean) {
    Row(
        Modifier.background(
            when {
                isCurrent -> Color(0xFFFFF3E0)
                even      -> Color(0xFFF5F7FA)
                else      -> Color.White
            }
        ).padding(vertical = BAL_ROW_PAD)
    ) {
        BalCol.values().forEach { col ->
            val active = col == selCol
            val v      = row.values[col.colIdx]
            Box(
                Modifier
                    .width(W_BAL_COL)
                    .then(if (active) Modifier.background(col.color.copy(alpha = 0.07f)) else Modifier)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    if (v == 0.0) "—" else col.fmt(v),
                    fontSize   = 12.sp,
                    color      = if (active) col.color else Color(0xFF212121),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign  = TextAlign.End
                )
            }
        }
    }
}
