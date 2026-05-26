package com.dodisrael.clavim

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

private enum class ValueType(val label: String) {
    COUNT("Количество"), SUM("Сумма ₪")
}

private data class GKey(val sort: String, val display: String)
private data class GroupedEntry(val k1: GKey, val k2: GKey, val value: Double)

// ── Parse helpers ─────────────────────────────────────────────────────────────

private fun parseTrDate(s: String): LocalDate? {
    if (s.isBlank()) return null
    // Strip day-name suffix like " сб." from "23.05.26 сб."
    val clean = s.trim().substringBefore(" ")
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

private suspend fun fetchTrainingData(): List<TrainingRow> = withContext(Dispatchers.IO) {
    val conn = URL("$TRAINING_CSV_URL&t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000; conn.readTimeout = 15_000; conn.instanceFollowRedirects = true
    val csv = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    conn.disconnect()
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
}

// ── Grouping ──────────────────────────────────────────────────────────────────

private fun getGroupKey(field: GroupField, row: TrainingRow): GKey = when (field) {
    GroupField.NONE        -> GKey("", "")
    GroupField.OWNER       -> GKey(row.ownerDog, row.ownerDog)
    GroupField.MONTH       -> {
        val d = row.date ?: return GKey("~", "—")
        GKey("%04d%02d".format(d.year, d.monthValue), "%02d.%04d".format(d.monthValue, d.year))
    }
    GroupField.DAY_OF_WEEK -> {
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
    var allRows   by remember { mutableStateOf<List<TrainingRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error     by remember { mutableStateOf("") }

    var monthFrom by remember { mutableStateOf<YearMonth?>(null) }
    var monthTo   by remember { mutableStateOf<YearMonth?>(null) }
    var groupBy1  by remember { mutableStateOf(GroupField.NONE) }
    var groupBy2  by remember { mutableStateOf(GroupField.NONE) }
    var valueType by remember { mutableStateOf(ValueType.COUNT) }

    LaunchedEffect(Unit) {
        try { allRows = fetchTrainingData() }
        catch (e: Exception) { error = e.message ?: "Ошибка загрузки" }
        finally { isLoading = false }
    }

    // Set default range to last 6 months once data is loaded
    LaunchedEffect(isLoading) {
        if (!isLoading && allRows.isNotEmpty() && monthFrom == null && monthTo == null) {
            val now = YearMonth.now()
            monthTo   = now
            monthFrom = now.minusMonths(5)
        }
    }

    val dateFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    // Build sorted list of months present in data (extended to current month)
    val availableMonths = remember(allRows) {
        val ymSet = allRows.mapNotNull { it.date }
            .map { YearMonth.of(it.year, it.monthValue) }
            .toSortedSet()
        if (ymSet.isEmpty()) return@remember emptyList<YearMonth>()
        val minYM = ymSet.first()
        val maxYM = maxOf(ymSet.last(), YearMonth.now())
        val list = mutableListOf<YearMonth>()
        var cur = minYM
        while (!cur.isAfter(maxYM)) { list.add(cur); cur = cur.plusMonths(1) }
        list
    }

    val filteredRows = remember(allRows, monthFrom, monthTo) {
        val from = monthFrom
        val to   = monthTo
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

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // ── Filter card ───────────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FA)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Month range picker
                    TrMonthRangePicker(
                        availableMonths = availableMonths,
                        from = monthFrom,
                        to   = monthTo,
                        onFromChange = { monthFrom = it },
                        onToChange   = { monthTo = it }
                    )

                    // Group by 1
                    TrGroupDropdown("Группировать по", groupBy1, modifier = Modifier.fillMaxWidth()) {
                        groupBy1 = it
                        if (it == GroupField.NONE) groupBy2 = GroupField.NONE
                    }

                    // Group by 2 (only when g1 set)
                    AnimatedVisibility(visible = groupBy1 != GroupField.NONE) {
                        TrGroupDropdown("Затем по", groupBy2, modifier = Modifier.fillMaxWidth()) {
                            groupBy2 = it
                        }
                    }

                    // Value type chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ValueType.values().forEach { vt ->
                            val active = vt == valueType
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (active) Color(0xFF37474F) else Color(0xFFECEFF1))
                                    .clickable { valueType = vt }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    vt.label, fontSize = 12.sp,
                                    color = if (active) Color.White else Color(0xFF37474F),
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
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

                else -> TrGroupedTable(groupedData, groupBy1, groupBy2, valueType)
            }
        }
    }
}

// ── Month range picker ────────────────────────────────────────────────────────

private fun ymDisplay(ym: YearMonth): String {
    val months = listOf(
        "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
        "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"
    )
    return "${months[ym.monthValue - 1]} ${ym.year}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrMonthRangePicker(
    availableMonths: List<YearMonth>,
    from: YearMonth?,
    to: YearMonth?,
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
    label: String,
    selected: YearMonth?,
    options: List<YearMonth>,
    onSelected: (YearMonth?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let { ymDisplay(it) } ?: "—",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("—", fontSize = 14.sp) },
                onClick = { onSelected(null); expanded = false }
            )
            options.forEach { ym ->
                DropdownMenuItem(
                    text = { Text(ymDisplay(ym), fontSize = 14.sp) },
                    onClick = { onSelected(ym); expanded = false }
                )
            }
        }
    }
}

// ── Group dropdown ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrGroupDropdown(
    label: String,
    selected: GroupField,
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
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GroupField.values().forEach { field ->
                DropdownMenuItem(
                    text = { Text(field.label, fontSize = 14.sp) },
                    onClick = { onSelected(field); expanded = false }
                )
            }
        }
    }
}

// ── Raw table ─────────────────────────────────────────────────────────────────

private val W_TR_OWN   = 140.dp
private val W_TR_DATE  = 72.dp
private val W_TR_BREED = 90.dp
private val W_TR_BOOL  = 48.dp
private val W_TR_AMT   = 72.dp

@Composable
private fun TrRawTable(rows: List<TrainingRow>, dateFmt: DateTimeFormatter) {
    val hScroll  = rememberScrollState()
    val vScroll  = rememberScrollState()
    val headerBg = Color(0xFF263238)

    Column(Modifier.fillMaxSize()) {
        // Frozen header
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(hScroll)
                .background(headerBg)
                .padding(vertical = 8.dp)
        ) {
            TrHCell("Хозяин/собака", W_TR_OWN)
            TrHCell("Дата", W_TR_DATE)
            TrHCell("Порода", W_TR_BREED)
            TrHCell("Перв.", W_TR_BOOL)
            TrHCell("Инд.", W_TR_BOOL)
            TrHCell("У кл.", W_TR_BOOL)
            TrHCell("Сумма", W_TR_AMT)
        }
        HorizontalDivider(color = Color(0xFF00695C), thickness = 1.dp)

        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
            Column(Modifier.fillMaxWidth()) {
                rows.forEachIndexed { i, row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(hScroll)
                            .background(if (i % 2 == 0) Color(0xFFF5F7FA) else Color.White)
                            .padding(vertical = 7.dp)
                    ) {
                        TrDCell(row.ownerDog, W_TR_OWN)
                        TrDCell(row.date?.format(dateFmt) ?: "—", W_TR_DATE)
                        TrDCell(row.breed.ifBlank { "—" }, W_TR_BREED)
                        TrBoolCell(row.isFirst, W_TR_BOOL)
                        TrBoolCell(row.isIndividual, W_TR_BOOL)
                        TrBoolCell(row.atClient, W_TR_BOOL)
                        TrDCell(if (row.amount > 0) "%.0f ₪".format(row.amount) else "—", W_TR_AMT, end = true)
                    }
                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                }
                // Summary row
                if (rows.isNotEmpty()) {
                    val total = rows.sumOf { it.amount }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(hScroll)
                            .background(Color(0xFFE8F5E9))
                            .padding(vertical = 8.dp)
                    ) {
                        Box(Modifier.width(W_TR_OWN + W_TR_DATE + W_TR_BREED + W_TR_BOOL * 3).padding(horizontal = 8.dp)) {
                            Text("Итого: ${rows.size} занятий", fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                        }
                        TrDCell("%.0f ₪".format(total), W_TR_AMT, end = true,
                            bold = true, color = Color(0xFF2E7D32))
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun TrHCell(text: String, width: androidx.compose.ui.unit.Dp) =
    Box(Modifier.width(width).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
            textAlign = TextAlign.Center, maxLines = 1)
    }

@Composable
private fun TrDCell(
    text: String, width: androidx.compose.ui.unit.Dp,
    end: Boolean = false, bold: Boolean = false, color: Color = Color(0xFF212121)
) = Box(
    Modifier.width(width).padding(horizontal = 6.dp),
    contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart
) {
    Text(text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = color, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
}

@Composable
private fun TrBoolCell(value: Boolean, width: androidx.compose.ui.unit.Dp) =
    Box(Modifier.width(width), contentAlignment = Alignment.Center) {
        Text(if (value) "✓" else "—", fontSize = 13.sp,
            color = if (value) Color(0xFF2E7D32) else Color(0xFFBBBBBB))
    }

// ── Grouped table ─────────────────────────────────────────────────────────────

private val W_TR_GK  = 140.dp
private val W_TR_VAL = 100.dp

@Composable
private fun TrGroupedTable(
    entries: List<GroupedEntry>,
    g1: GroupField,
    g2: GroupField,
    vt: ValueType
) {
    val hScroll    = rememberScrollState()
    val vScroll    = rememberScrollState()
    val headerBg   = Color(0xFF263238)
    val hasG2      = g2 != GroupField.NONE
    val totalValue = entries.sumOf { it.value }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(hScroll)
                .background(headerBg)
                .padding(vertical = 8.dp)
        ) {
            TrHCell(g1.label, W_TR_GK)
            if (hasG2) TrHCell(g2.label, W_TR_GK)
            TrHCell(vt.label, W_TR_VAL)
        }
        HorizontalDivider(color = Color(0xFF00695C), thickness = 1.dp)

        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
            Column(Modifier.fillMaxWidth()) {
                entries.forEachIndexed { i, entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(hScroll)
                            .background(if (i % 2 == 0) Color(0xFFF5F7FA) else Color.White)
                            .padding(vertical = 8.dp)
                    ) {
                        TrDCell(entry.k1.display, W_TR_GK)
                        if (hasG2) TrDCell(entry.k2.display, W_TR_GK)
                        TrDCell(
                            if (vt == ValueType.COUNT) entry.value.toInt().toString()
                            else "%.0f ₪".format(entry.value),
                            W_TR_VAL, end = true
                        )
                    }
                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
                }
                // Summary row
                if (entries.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(hScroll)
                            .background(Color(0xFFE8F5E9))
                            .padding(vertical = 8.dp)
                    ) {
                        val totalWidth = W_TR_GK + (if (hasG2) W_TR_GK else 0.dp)
                        Box(Modifier.width(totalWidth).padding(horizontal = 6.dp)) {
                            Text("Итого", fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                        }
                        TrDCell(
                            if (vt == ValueType.COUNT) totalValue.toInt().toString()
                            else "%.0f ₪".format(totalValue),
                            W_TR_VAL, end = true, bold = true, color = Color(0xFF2E7D32)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
