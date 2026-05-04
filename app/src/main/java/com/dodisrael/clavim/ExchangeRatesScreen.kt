package com.dodisrael.clavim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

                is RatesState.Error -> Text(
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
                        CurrencyCalculatorCard(
                            usdRub = state.usdRub,
                            usdIls = state.usdIls,
                            ilsRub = state.ilsRub
                        )

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

private enum class CalcCurrency(val title: String, val sign: String) {
    USD("USD", "$"),
    RUB("RUB", "₽"),
    ILS("ILS", "₪")
}

@Composable
private fun CurrencyCalculatorCard(
    usdRub: Double,
    usdIls: Double,
    ilsRub: Double
) {
    var from by remember { mutableStateOf(CalcCurrency.USD) }
    var to by remember { mutableStateOf(CalcCurrency.RUB) }
    var amountText by remember { mutableStateOf("") }

    fun rate(f: CalcCurrency, t: CalcCurrency): Double {
        if (f == t) return 1.0
        return when (f to t) {
            CalcCurrency.USD to CalcCurrency.RUB -> usdRub
            CalcCurrency.RUB to CalcCurrency.USD -> 1.0 / usdRub
            CalcCurrency.USD to CalcCurrency.ILS -> usdIls
            CalcCurrency.ILS to CalcCurrency.USD -> 1.0 / usdIls
            CalcCurrency.ILS to CalcCurrency.RUB -> ilsRub
            CalcCurrency.RUB to CalcCurrency.ILS -> 1.0 / ilsRub
            else -> 1.0
        }
    }

    val result = amountText.toDoubleOrNull()?.let { it * rate(from, to) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFE3F2FD), Color(0xFFE1F5FE)),
                    start = Offset(0f, 0f),
                    end = Offset(900f, 600f)
                )
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Text(
                text = "Расчёт суммы",
                color = Color(0xFF0D47A1),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            // FROM
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Из", color = Color(0xFF546E7A), fontSize = 12.sp)
                CurrencyChipRow(selected = from, onSelect = { from = it })
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { v ->
                        if (v.all { it.isDigit() || it == '.' || it == ',' })
                            amountText = v.replace(',', '.')
                    },
                    placeholder = {
                        Text(
                            "0",
                            fontSize = 28.sp,
                            color = Color(0xFFB0BEC5),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F),
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1565C0),
                        unfocusedBorderColor = Color(0xFFB0BEC5),
                        cursorColor = Color(0xFF1565C0),
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Кнопка обмена
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = { val tmp = from; from = to; to = tmp },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF1565C0).copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = "Поменять", tint = Color(0xFF1565C0))
                }
            }

            // TO
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("В", color = Color(0xFF546E7A), fontSize = 12.sp)
                CurrencyChipRow(selected = to, onSelect = { to = it })
            }

            // Результат
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (result != null) Color(0xFF1565C0).copy(alpha = 0.08f) else Color(0x00000000))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (result != null) {
                    Text(
                        text = "= ${"%.2f".format(result)} ${to.sign}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D47A1),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Введите сумму",
                        fontSize = 14.sp,
                        color = Color(0xFFB0BEC5),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyChipRow(
    selected: CalcCurrency,
    onSelect: (CalcCurrency) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalcCurrency.values().forEach { currency ->
            val isSelected = currency == selected
            Button(
                onClick = { onSelect(currency) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Color(0xFF1565C0) else Color.White,
                    contentColor = if (isSelected) Color.White else Color(0xFF546E7A)
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = "${currency.sign} ${currency.title}",
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun RateCard(
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
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
private fun Sparkline(
    values: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
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

        val fillPath = Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.30f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height
            )
        )

        val linePath = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        drawCircle(lineColor, radius = 4.dp.toPx(), center = pts.last())
        drawCircle(Color.White, radius = 2.dp.toPx(), center = pts.last())
    }
}

private suspend fun fetchExchangeRates(): RatesState {
    return withContext(Dispatchers.IO) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val labelSdf = SimpleDateFormat("dd.MM", Locale.US)

            val dateEntries = (0..27 step 3).map { daysAgo ->
                val cal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -daysAgo)
                }
                Pair(sdf.format(cal.time), labelSdf.format(cal.time))
            }.reversed()

            val fetched = coroutineScope {
                dateEntries.map { (apiDate, label) ->
                    async {
                        Triple(apiDate, label, fetchSingleDate(apiDate))
                    }
                }.awaitAll()
            }

            val valid = fetched.filter { it.third != null }

            if (valid.isEmpty()) {
                return@withContext RatesState.Error("Нет данных")
            }

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
        val url = URL(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$date/v1/currencies/usd.json"
        )

        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000

        val json = conn.inputStream.bufferedReader().readText()

        val rub = Regex("\"rub\"\\s*:\\s*([\\d.]+)")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()

        val ils = Regex("\"ils\"\\s*:\\s*([\\d.]+)")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()

        if (rub != null && ils != null) {
            Pair(rub, ils)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}