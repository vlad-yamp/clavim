package com.dodisrael.clavim

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InfoMenuScreen(
    onBack: () -> Unit,
    onExchangeRatesClick: () -> Unit,
    onOurDataClick: () -> Unit,
    onAddressesClick: () -> Unit,
    onWebViewClick: (url: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val items = remember { buildInfoMenuItems(onExchangeRatesClick, onOurDataClick, onAddressesClick, onWebViewClick) }

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

private fun buildInfoMenuItems(
    onExchangeRatesClick: () -> Unit,
    onOurDataClick: () -> Unit,
    onAddressesClick: () -> Unit,
    onWebViewClick: (url: String, title: String) -> Unit
): List<MenuItem> = listOf(
    MenuItem("Курсы\nвалют", Icons.Default.CurrencyExchange, Color(0xFF00695C)) { _ ->
        onExchangeRatesClick()
    },
    MenuItem("Наши\nданные", Icons.Default.ContactPage, Color(0xFF0D47A1)) { _ ->
        onOurDataClick()
    },
    MenuItem("Адреса", Icons.Default.LocationOn, Color(0xFFAD1457)) { _ ->
        onAddressesClick()
    },
    MenuItem("Список\nпород", Icons.Default.Pets, Color(0xFF8D6E63)) { _ ->
        onWebViewClick("https://petstory.ru/knowledge/dogs/dog-breeds/", "Список пород")
    },
    MenuItem("Open AI", Icons.Default.Psychology, Color(0xFF10A37F)) { ctx ->
        ctx.openUrl("https://platform.openai.com/usage")
    }
)

@Composable
fun OurDataScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun copyText(value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("", value))
        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Наши данные",
            subtitle = "Контакты и реквизиты",
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
            OurDataCard("Контакты") {
                OurDataRow("Тел: Вова", "0557702750") { copyText("0557702750") }
                OurDataRow("Тел: Оля", "0506072764") { copyText("0506072764") }
                OurDataRow("Тэудат зэут Вова", "347839219") { copyText("347839219") }
                OurDataRow("Тэудат зэут Оля", "347839227") { copyText("347839227") }
            }
            OurDataCard("Банковские реквизиты") {
                OurDataRow("Для", "1122 4023")
                OurDataRow("Для", "5896 3246 (854)")
                OurDataRow("Для", "8337 4855")
                OurDataRow("К (карта)", "4177 4901 4299 8337") { copyText("4177 4901 4299 8337") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OurDataCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF757575))
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun OurDataRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = Color(0xFF9E9E9E))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C1B1F))
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Скопировать",
                    tint = Color(0xFF757575),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
