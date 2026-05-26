package com.dodisrael.clavim

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ── Data ──────────────────────────────────────────────────────────────────────

private const val TRAINING_CSV_URL =
    "https://docs.google.com/spreadsheets/d/1k7usk6ZFkPL7x6-CFFfAr87kQvRkI9TBCZZqJFej0X4/export?format=csv&gid=0"

private data class TrainingRow(
    val ownerDog: String,
    val date: LocalDate?,
    val phone: String,
    val breed: String,
    val nickname: String,
    val isFirst: Boolean,
    val isIndividual: Boolean,
    val atClient: Boolean,
    val amount: Double
)

private enum class GroupField(val label: String) {
    NONE("—"),
    OWNER("Хозяин и собака"),
    MONTH("Месяц"),
    DAY_OF_WEEK("День недели"),
    BREED("Порода"),
    IS_FIRST("Первое занятие"),
    IS_INDIVIDUAL("Индивидуально"),
    AT_CLIENT("У клиента")
}

private val BOOLEAN_GROUP_FIELDS = setOf(GroupField.IS_FIRST, GroupField.IS_INDIVIDUAL, GroupField.AT_CLIENT)

private enum class ValueType(val label: String) {
    COUNT("Количество"), SUM("Сумма ₪")
}

private data class GKey(val sort: String, val display: String)
private data class GroupedEntry(val k1: GKey, val k2: GKey, val value: Double)

// ── Parse helpers ─────────────────────────────────────────────────────────────

private fun parseTrDate(s: String): LocalDate? {
    if (s.isBlank()) return null
    val clean = s.trim().substringBefore(" ")   // strip " сб." suffix
    for (fmt in listOf("dd.MM.yyyy", "d.M.yyyy", "dd.MM.yy", "d.M.yy", "yyyy-MM-dd")) {
        try { return LocalDate.parse(clean, DateTimeFormatter.ofPattern(fmt)) } catch (_: Exception) {}
    }
    return null
}

private fun parseBool(s: String): Boolean {
    val v = s.trim().uppercase()
    return v == "TRUE" || v == "ИСТИНА" || v == "✓" || v == "+" || v == "1" || v == "ДА" || v == "YES"
}

private fun parseTrCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var inQ = false; val buf = StringBuilder()
    for (c in line) when {
        c == '"'         -> inQ = !inQ
        c == ',' && !inQ -> { result.add(buf.toString()); buf.clear() }
        else              -> buf.append(c)
    }
    result.add(buf.toString())
    return result
}

private fun parseTrRows(csv: String): List<TrainingRow> =
    csv.lines().drop(1).mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val c = parseTrCsvLine(line)
        val owner = c.getOrElse(0) { "" }.trim()
        if (owner.isBlank()) return@mapNotNull null
        TrainingRow(
            ownerDog     = owner,
            date         = parseTrDate(c.getOrElse(1) { "" }),
            phone        = c.getOrElse(2) { "" }.trim(),
            breed        = c.getOrElse(3) { "" }.trim(),
            nickname     = c.getOrElse(4) { "" }.trim(),
            isFirst      = parseBool(c.getOrElse(5) { "" }),
            isIndividual = parseBool(c.getOrElse(6) { "" }),
            atClient     = parseBool(c.getOrElse(7) { "" }),
            amount       = c.getOrElse(8) { "" }.trim().replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
        )
    }

private fun loadCachedRows(context: Context): List<TrainingRow>? {
    val file = File(context.cacheDir, "training_cache.csv")
    if (!file.exists()) return null
    return try { parseTrRows(file.readText()) } catch (_: Exception) { null }
}

private suspend fun fetchAndCacheRows(context: Context): List<TrainingRow> = withContext(Dispatchers.IO) {
    val conn = URL("$TRAINING_CSV_URL&t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000; conn.readTimeout = 15_000; conn.instanceFollowRedirects = true
    val csv = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    conn.disconnect()
    try { File(context.cacheDir, "training_cache.csv").writeText(csv) } catch (_: Exception) {}
    parseTrRows(csv)
}

// ── Grouping ──────────────────────────────────────────────────────────────────

private fun getGroupKey(field: GroupField, row: TrainingRow): GKey = when (field) {
    GroupField.NONE          -> GKey("", "")
    GroupField.OWNER         -> GKey(row.ownerDog, row.ownerDog)
    GroupField.MONTH         -> {
        val d = row.date ?: return GKey("~", "—")
        GKey("%04d%02d".format(d.year, d.monthValue), "%02d.%04d".format(d.monthValue, d.year))
    }
    GroupField.DAY_OF_WEEK   -> {
        val d = row.date ?: return GKey("9", "—")
        val names = mapOf(
            DayOfWeek.MONDAY to "Понедельник", DayOfWeek.TUESDAY to "Вторник",
            DayOfWeek.WEDNESDAY to "Среда",    DayOfWeek.THURSDAY to "Четверг",
            DayOfWeek.FRIDAY to "Пятница",     DayOfWeek.SATURDAY to "Суббота",
            DayOfWeek.SUNDAY to "Воскресенье"
        )
        GKey(d.dayOfWeek.value.toString(), names[d.dayOfWeek] ?: "—")
    }
    GroupField.BREED         -> GKey(row.breed.ifBlank { "~" }, row.breed.ifBlank { "—" })
    GroupField.IS_FIRST      -> GKey(if (row.isFirst) "0" else "1", if (row.isFirst) "Да" else "Нет")
    GroupField.IS_INDIVIDUAL -> GKey(if (row.isIndividual) "0" else "1", if (row.isIndividual) "Да" else "Нет")
    GroupField.AT_CLIENT     -> GKey(if (row.atClient) "0" else "1", if (row.atClient) "Да" else "Нет")
}

private fun computeGrouped(
    rows: List<TrainingRow>, g1: GroupField, g2: GroupField, vt: ValueType
): List<GroupedEntry> =
    rows.groupBy { getGroupKey(g1, it) to getGroupKey(g2, it) }
        .map { (keys, grpRows) ->
            val v = if (vt == ValueType.COUNT) grpRows.size.toDouble() else grpRows.sumOf { it.amount }
            GroupedEntry(keys.first, keys.second, v)
        }
        .sortedWith(compareBy({ it.k1.sort }, { it.k2.sort }))

// ── Main screen ───────────────────────────────────────────────────────────────

@Composable
fun TrainingScreen(onBack: () -> Unit) {
    val context   = LocalContext.current
    var allRows   by remember { mutableStateOf<List<TrainingRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error     by remember { mutableStateOf("") }

    var monthFrom by remember { mutableStateOf<YearMonth?>(null) }
    var monthTo   by remember { mutableStateOf<YearMonth?>(null) }
    var groupBy1  by remember { mutableStateOf(GroupField.NONE) }
    var groupBy2  by remember { mutableStateOf(GroupField.NONE) }
    var valueType by remember { mutableStateOf(ValueType.COUNT) }
    var showChart by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Phase 1: show cache immediately so the screen feels instant
        val cached = withContext(Dispatchers.IO) { loadCachedRows(context) }
        if (cached != null) {
            allRows   = cached
            isLoading = false
        }
        // Phase 2: fetch fresh data in background (silently updates when done)
        try {
            val fresh = fetchAndCacheRows(context)
            allRows   = fresh
        } catch (e: Exception) {
            if (cached == null) error = e.message ?: "Ошибка загрузки"
        } finally {
            isLoading = false
        }
    }

    // Set default month filter once data is available for the first time
    LaunchedEffect(isLoading) {
        if (!isLoading && allRows.isNotEmpty() && monthFrom == null && monthTo == null) {
            val now = YearMonth.now()
            monthTo   = now
            monthFrom = now
        }
    }

    val dateFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    val availableMonths = remember(allRows) {
        val ymSet = allRows.mapNotNull { it.date }
            .map { YearMonth.of(it.year, it.monthValue) }.toSortedSet()
        if (ymSet.isEmpty()) return@remember emptyList<YearMonth>()
        val minYM = ymSet.first()
        val maxYM = maxOf(ymSet.last(), YearMonth.now())
        val list = mutableListOf<YearMonth>()
        var cur = minYM
        while (!cur.isAfter(maxYM)) { list.add(cur); cur = cur.plusMonths(1) }
        list
    }

    val filteredRows = remember(allRows, monthFrom, monthTo) {
        val from = monthFrom; val to = monthTo
        allRows.filter { row ->
            val d = row.date ?: return@filter false
            val ym = YearMonth.of(d.year, d.monthValue)
            (from == null || !ym.isBefore(from)) && (to == null || !ym.isAfter(to))
        }
    }

    val groupedData = remember(filteredRows, groupBy1, groupBy2, valueType) {
        if (groupBy1 == GroupField.NONE) emptyList()
        else computeGrouped(filteredRows, groupBy1, groupBy2, valueType)
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(title = "Дрессировка", subtitle = "", showBack = true, onBack = onBack, compact = true)

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            // ── Filter card ───────────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FA)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrMonthRangePicker(availableMonths, monthFrom, monthTo,
                        onFromChange = { monthFrom = it }, onToChange = { monthTo = it })

                    TrGroupDropdown("Группировать по", groupBy1, Modifier.fillMaxWidth()) {
                        groupBy1 = it
                        if (it == GroupField.NONE) { groupBy2 = GroupField.NONE; showChart = false }
                    }

                    AnimatedVisibility(visible = groupBy1 != GroupField.NONE) {
                        TrGroupDropdown("Затем по", groupBy2, Modifier.fillMaxWidth()) { groupBy2 = it }
                    }

                    // Value type chips + chart toggle
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ValueType.values().forEach { vt ->
                            val active = vt == valueType
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (active) Color(0xFF37474F) else Color(0xFFECEFF1))
                                    .clickable { valueType = vt }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(vt.label, fontSize = 12.sp,
                                    color = if (active) Color.White else Color(0xFF37474F),
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                        AnimatedVisibility(visible = groupBy1 != GroupField.NONE) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (showChart) Color(0xFF37474F) else Color(0xFFECEFF1))
                                    .clickable { showChart = !showChart },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (showChart) Icons.Default.TableChart else Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = if (showChart) Color.White else Color(0xFF37474F),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Results ───────────────────────────────────────────────────
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00695C))
                }
                error.isNotBlank() -> Box(
                    Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center
                ) { Text(error, color = Color(0xFFC62828), textAlign = TextAlign.Center) }
                groupBy1 == GroupField.NONE -> TrRawTable(filteredRows, dateFmt)
                showChart -> TrGroupedChart(groupedData, groupBy1, groupBy2, valueType)
                else -> TrGroupedTable(groupedData, groupBy1, groupBy2, valueType)
            }
        }
    }
}

// ── Month range picker ────────────────────────────────────────────────────────

private fun ymDisplay(ym: YearMonth): String {
    val months = listOf("Янв","Фев","Мар","Апр","Май","Июн","Июл","Авг","Сен","Окт","Ноя","Дек")
    return "${months[ym.monthValue - 1]} ${ym.year}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrMonthRangePicker(
    availableMonths: List<YearMonth>,
    from: YearMonth?, to: YearMonth?,
    onFromChange: (YearMonth?) -> Unit,
    onToChange: (YearMonth?) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrMonthDropdown("С", from, availableMonths, onFromChange, Modifier.weight(1f))
        TrMonthDropdown("По", to, availableMonths, onToChange, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrMonthDropdown(
    label: String, selected: YearMonth?,
    options: List<YearMonth>,
    onSelected: (YearMonth?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let { ymDisplay(it) } ?: "—",
            onValueChange = {}, readOnly = true,
            label = { Text(label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp), singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("—", fontSize = 14.sp) },
                onClick = { onSelected(null); expanded = false })
            options.forEach { ym ->
                DropdownMenuItem(text = { Text(ymDisplay(ym), fontSize = 14.sp) },
                    onClick = { onSelected(ym); expanded = false })
            }
        }
    }
}

// ── Group dropdown ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrGroupDropdown(
    label: String, selected: GroupField,
    modifier: Modifier = Modifier,
    onSelected: (GroupField) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.label, onValueChange = {}, readOnly = true,
            label = { Text(label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp), singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GroupField.values().forEach { field ->
                DropdownMenuItem(text = { Text(field.label, fontSize = 14.sp) },
                    onClick = { onSelected(field); expanded = false })
            }
        }
    }
}

// ── Column widths ─────────────────────────────────────────────────────────────

private val W_TR_OWN   = 140.dp
private val W_TR_DATE  = 88.dp
private val W_TR_BREED = 90.dp
private val W_TR_BOOL  = 48.dp
private val W_TR_AMT   = 72.dp
private val W_TR_GK    = 140.dp
private val W_TR_VAL   = 100.dp

// ── Cell helpers ──────────────────────────────────────────────────────────────

@Composable
private fun TrHCell(text: String, width: Dp) =
    Box(Modifier.width(width).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
            textAlign = TextAlign.Center, maxLines = 1)
    }

@Composable
private fun TrDCell(
    text: String, width: Dp,
    end: Boolean = false, bold: Boolean = false, color: Color = Color(0xFF212121)
) = Box(
    Modifier.width(width).padding(horizontal = 6.dp),
    contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart
) {
    Text(text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = color, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
}

@Composable
private fun TrBoolCell(value: Boolean, width: Dp) =
    Box(Modifier.width(width), contentAlignment = Alignment.Center) {
        Text(if (value) "✓" else "—", fontSize = 13.sp,
            color = if (value) Color(0xFF2E7D32) else Color(0xFFBBBBBB))
    }

// ── Raw table (frozen first column) ──────────────────────────────────────────

@Composable
private fun TrRawTable(rows: List<TrainingRow>, dateFmt: DateTimeFormatter) {
    val hScroll  = rememberScrollState()
    val vScroll  = rememberScrollState()
    val headerBg = Color(0xFF263238)

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(Modifier.fillMaxWidth().background(headerBg).padding(vertical = 8.dp)) {
            TrHCell("Хозяин/собака", W_TR_OWN)
            Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                TrHCell("Дата", W_TR_DATE)
                TrHCell("Порода", W_TR_BREED)
                TrHCell("Перв.", W_TR_BOOL)
                TrHCell("Инд.", W_TR_BOOL)
                TrHCell("У кл.", W_TR_BOOL)
                TrHCell("Сумма", W_TR_AMT)
            }
        }
        HorizontalDivider(color = Color(0xFF00695C), thickness = 1.dp)

        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
            Column(Modifier.fillMaxWidth()) {
                rows.forEachIndexed { i, row ->
                    val bg = if (i % 2 == 0) Color(0xFFF5F7FA) else Color.White
                    Row(Modifier.fillMaxWidth().background(bg).padding(vertical = 7.dp)) {
                        TrDCell(row.ownerDog, W_TR_OWN)
                        Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                            TrDCell(row.date?.format(dateFmt) ?: "—", W_TR_DATE)
                            TrDCell(row.breed.ifBlank { "—" }, W_TR_BREED)
                            TrBoolCell(row.isFirst, W_TR_BOOL)
                            TrBoolCell(row.isIndividual, W_TR_BOOL)
                            TrBoolCell(row.atClient, W_TR_BOOL)
                            TrDCell(if (row.amount > 0) "%.0f ₪".format(row.amount) else "—",
                                W_TR_AMT, end = true)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                }
                if (rows.isNotEmpty()) {
                    val total = rows.sumOf { it.amount }
                    Row(Modifier.fillMaxWidth().background(Color(0xFFE8F5E9)).padding(vertical = 8.dp)) {
                        Box(Modifier.width(W_TR_OWN).padding(horizontal = 8.dp)) {
                            Text("Итого: ${rows.size} занятий", fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                        }
                        Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                            Spacer(Modifier.width(W_TR_DATE + W_TR_BREED + W_TR_BOOL * 3))
                            TrDCell("%.0f ₪".format(total), W_TR_AMT, end = true,
                                bold = true, color = Color(0xFF2E7D32))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ── Grouped table (frozen first column) ──────────────────────────────────────

@Composable
private fun TrGroupedTable(
    entries: List<GroupedEntry>, g1: GroupField, g2: GroupField, vt: ValueType
) {
    val hScroll    = rememberScrollState()
    val vScroll    = rememberScrollState()
    val headerBg   = Color(0xFF263238)
    val hasG2      = g2 != GroupField.NONE
    val totalValue = entries.sumOf { it.value }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(headerBg).padding(vertical = 8.dp)) {
            TrHCell(g1.label, W_TR_GK)
            Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                if (hasG2) TrHCell(g2.label, W_TR_GK)
                TrHCell(vt.label, W_TR_VAL)
            }
        }
        HorizontalDivider(color = Color(0xFF00695C), thickness = 1.dp)

        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
            Column(Modifier.fillMaxWidth()) {
                entries.forEachIndexed { i, entry ->
                    Row(Modifier.fillMaxWidth()
                        .background(if (i % 2 == 0) Color(0xFFF5F7FA) else Color.White)
                        .padding(vertical = 8.dp)
                    ) {
                        TrDCell(entry.k1.display, W_TR_GK)
                        Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                            if (hasG2) TrDCell(entry.k2.display, W_TR_GK)
                            TrDCell(
                                if (vt == ValueType.COUNT) entry.value.toInt().toString()
                                else "%.0f ₪".format(entry.value),
                                W_TR_VAL, end = true
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                }
                if (entries.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().background(Color(0xFFE8F5E9)).padding(vertical = 8.dp)) {
                        Box(Modifier.width(W_TR_GK).padding(horizontal = 6.dp)) {
                            Text("Итого", fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                        }
                        Row(Modifier.weight(1f).horizontalScroll(hScroll)) {
                            if (hasG2) Spacer(Modifier.width(W_TR_GK))
                            TrDCell(
                                if (vt == ValueType.COUNT) totalValue.toInt().toString()
                                else "%.0f ₪".format(totalValue),
                                W_TR_VAL, end = true, bold = true, color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ── Chart ─────────────────────────────────────────────────────────────────────

private val CHART_COLORS = listOf(
    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFE65100),
    Color(0xFF6A1B9A), Color(0xFF00695C), Color(0xFFC62828),
    Color(0xFF37474F), Color(0xFFF57F17)
)

@Composable
private fun TrGroupedChart(
    entries: List<GroupedEntry>, g1: GroupField, g2: GroupField, vt: ValueType
) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет данных", color = Color(0xFF9E9E9E))
        }
        return
    }

    val hasG2    = g2 != GroupField.NONE
    // Boolean g2 fields use stacked bars; others use grouped bars
    val stacked  = hasG2 && g2 in BOOLEAN_GROUP_FIELDS

    val g1Keys = entries.map { it.k1 }.distinctBy { it.sort }.sortedBy { it.sort }
    val g2Keys = if (hasG2) entries.map { it.k2 }.distinctBy { it.sort }.sortedBy { it.sort }
                 else listOf(GKey("", ""))

    val maxVal: Double = if (stacked) {
        g1Keys.maxOf { k1 ->
            g2Keys.sumOf { k2 -> entries.find { it.k1.sort == k1.sort && it.k2.sort == k2.sort }?.value ?: 0.0 }
        }
    } else {
        entries.maxOf { it.value }
    }.coerceAtLeast(1.0)

    val valueLookup = entries.associateBy { it.k1.sort to it.k2.sort }

    val hScroll = rememberScrollState()
    LaunchedEffect(Unit) { hScroll.scrollTo(Int.MAX_VALUE) }

    val maxLabelLen  = g1Keys.maxOf { it.display.length }.coerceAtLeast(1)
    val rotateLabels = maxLabelLen > 5

    val barWdp       = if (stacked) 44.dp else 28.dp
    val barGapDp     = 4.dp
    val groupGapDp   = 16.dp
    val yAxisWdp     = 52.dp
    val xLabelHdp    = if (rotateLabels) 90.dp else 44.dp
    val topPadDp     = 20.dp
    val barsPerGroup = if (stacked) 1 else g2Keys.size
    val groupWdp     = barWdp * barsPerGroup + barGapDp * (barsPerGroup - 1).coerceAtLeast(0)
    val totalChartW  = groupGapDp / 2 + (groupWdp + groupGapDp) * g1Keys.size

    Column(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
        // Legend
        if (hasG2) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                g2Keys.forEachIndexed { idx, k2 ->
                    val color = CHART_COLORS.getOrElse(idx) { Color.Gray }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)) {
                        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text(k2.display, fontSize = 11.sp, color = Color(0xFF37474F))
                    }
                }
                if (stacked) {
                    Spacer(Modifier.width(4.dp))
                    Text("(столбики сложены)", fontSize = 10.sp, color = Color(0xFF9E9E9E))
                }
            }
        }

        Row(Modifier.weight(1f).fillMaxWidth()) {
            // Fixed Y-axis
            BoxWithConstraints(Modifier.width(yAxisWdp).fillMaxHeight()) {
                val chartH = maxHeight - xLabelHdp - topPadDp
                Canvas(Modifier.fillMaxSize()) {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#9E9E9E")
                        textSize = 26f
                        textAlign = android.graphics.Paint.Align.RIGHT
                        isAntiAlias = true
                    }
                    val steps = 4
                    for (i in 0..steps) {
                        val v = maxVal * i / steps
                        val y = topPadDp.toPx() + chartH.toPx() * (1f - i.toFloat() / steps)
                        val lbl = if (vt == ValueType.COUNT) v.toInt().toString() else "%.0f".format(v)
                        drawIntoCanvas { it.nativeCanvas.drawText(lbl, size.width - 4.dp.toPx(), y + 9f, paint) }
                        drawLine(Color(0xFFEEEEEE), Offset(size.width - 4.dp.toPx(), y), Offset(size.width, y), 1.dp.toPx())
                    }
                }
            }

            // Scrollable bars
            Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
                BoxWithConstraints(Modifier.width(totalChartW).fillMaxHeight()) {
                    val chartH = maxHeight - xLabelHdp - topPadDp
                    Canvas(Modifier.fillMaxSize()) {
                        val barW     = barWdp.toPx()
                        val barGap   = barGapDp.toPx()
                        val groupGap = groupGapDp.toPx()
                        val groupW   = barW * barsPerGroup + barGap * (barsPerGroup - 1).coerceAtLeast(0)
                        val ch       = chartH.toPx()
                        val top      = topPadDp.toPx()
                        val steps    = 4

                        // Grid lines
                        for (i in 0..steps) {
                            val y = top + ch * (1f - i.toFloat() / steps)
                            drawLine(Color(0xFFEEEEEE), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }

                        val lPaint = android.graphics.Paint().apply {
                            isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
                        }

                        g1Keys.forEachIndexed { gIdx, k1 ->
                            val groupLeft   = groupGap / 2 + gIdx * (groupW + groupGap)
                            val groupCenter = groupLeft + groupW / 2

                            if (stacked) {
                                // Stacked segments bottom-to-top
                                var yBottom = top + ch
                                g2Keys.forEachIndexed { bIdx, k2 ->
                                    val v    = valueLookup[k1.sort to k2.sort]?.value ?: 0.0
                                    val segH = (v / maxVal).toFloat() * ch
                                    val col  = CHART_COLORS.getOrElse(bIdx) { Color.Gray }
                                    if (segH > 0.5f) {
                                        val isTop = bIdx == g2Keys.size - 1
                                        if (isTop) {
                                            drawRoundRect(col,
                                                topLeft = Offset(groupLeft, yBottom - segH),
                                                size = Size(barW, segH),
                                                cornerRadius = CornerRadius(4.dp.toPx()))
                                        } else {
                                            drawRect(col,
                                                topLeft = Offset(groupLeft, yBottom - segH),
                                                size = Size(barW, segH + 1f))
                                        }
                                        if (segH > 20.dp.toPx()) {
                                            lPaint.textSize = 24f
                                            lPaint.color = android.graphics.Color.WHITE
                                            val lbl = if (vt == ValueType.COUNT) v.toInt().toString() else "%.0f".format(v)
                                            drawIntoCanvas { c ->
                                                c.nativeCanvas.drawText(lbl, groupLeft + barW / 2, yBottom - segH / 2 + 9f, lPaint)
                                            }
                                        }
                                    }
                                    yBottom -= segH
                                }
                            } else {
                                // Grouped bars side by side
                                g2Keys.forEachIndexed { bIdx, k2 ->
                                    val v    = valueLookup[k1.sort to k2.sort]?.value ?: 0.0
                                    val barH = (v / maxVal).toFloat() * ch
                                    val barL = groupLeft + bIdx * (barW + barGap)
                                    val col  = if (hasG2) CHART_COLORS.getOrElse(bIdx) { Color.Gray }
                                               else Color(0xFF1565C0)
                                    val minH = 2.dp.toPx()
                                    drawRoundRect(col,
                                        topLeft = Offset(barL, top + ch - barH.coerceAtLeast(minH)),
                                        size = Size(barW, barH.coerceAtLeast(minH)),
                                        cornerRadius = CornerRadius(3.dp.toPx()))
                                    if (barH > 16.dp.toPx()) {
                                        lPaint.textSize = 22f
                                        lPaint.color = android.graphics.Color.parseColor("#37474F")
                                        val lbl = if (vt == ValueType.COUNT) v.toInt().toString() else "%.0f".format(v)
                                        drawIntoCanvas { c ->
                                            c.nativeCanvas.drawText(lbl, barL + barW / 2, top + ch - barH - 4.dp.toPx(), lPaint)
                                        }
                                    }
                                }
                            }

                            // X-axis label
                            lPaint.textSize = 25f
                            lPaint.color = android.graphics.Color.parseColor("#616161")
                            lPaint.textAlign = android.graphics.Paint.Align.CENTER
                            if (rotateLabels) {
                                drawIntoCanvas { c ->
                                    val nc = c.nativeCanvas
                                    nc.save()
                                    // Translate to center of label area below this bar group, then rotate
                                    nc.translate(groupCenter, top + ch + xLabelHdp.toPx() / 2)
                                    nc.rotate(-90f)
                                    nc.drawText(k1.display.take(16), 0f, 9f, lPaint)
                                    nc.restore()
                                }
                            } else {
                                drawIntoCanvas { c ->
                                    c.nativeCanvas.drawText(k1.display.take(12), groupCenter, top + ch + 18.dp.toPx(), lPaint)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
