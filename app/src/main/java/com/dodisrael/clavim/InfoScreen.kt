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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Slideshow
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
    onTelegramFosteringClick: () -> Unit,
    onOurDataClick: () -> Unit
) {
    val context = LocalContext.current
    val items = remember { buildInfoMenuItems(onExchangeRatesClick, onTelegramFosteringClick, onOurDataClick) }

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
    onTelegramFosteringClick: () -> Unit,
    onOurDataClick: () -> Unit
): List<MenuItem> = listOf(
    MenuItem("Фото из\nTelegram", Icons.Default.Slideshow, Color(0xFF039BE5)) { _ ->
        onTelegramFosteringClick()
    },
    MenuItem("Курсы\nвалют", Icons.Default.CurrencyExchange, Color(0xFF00695C)) { _ ->
        onExchangeRatesClick()
    },
    MenuItem("Наши\nданные", Icons.Default.ContactPage, Color(0xFF0D47A1)) { _ ->
        onOurDataClick()
    },
    MenuItem("Места\nна передержке", Icons.Default.Bed, Color(0xFF37474F)) { ctx ->
        ctx.openUrl("https://docs.google.com/spreadsheets/d/e/2PACX-1vQid7xTSYIm4dvkLqAP9ewvl7Pq41CFAXDDGldLdVCSQEtpK8KSXTa6f-_MHP4Zt2ezLwVV-Se5BJoG/pubhtml?authuser=0&widget=true&headers=false#gid=0?&single=true")
    },
    MenuItem("Отзывы\nклиентов", Icons.Default.RateReview, Color(0xFFF57F17)) { ctx ->
        ctx.openUrl("https://www.google.com/search?newwindow=1&authuser=1&sxsrf=AHTn8zrbLwwF9wGEA5poSjJh8dL3L95WuQ:1744786758568&si=APYL9bs7Hg2KMLB-4tSoTdxuOx8BdRvHbByC_AuVpNyh0x2KzYKIj8l0ArlFedGpiIPiM2wpnktdq0_hR9JLB-xTz_vgQx7xk0mKoPgtE06bMRpG7JD0lxcEoXkR3fen8REzMIFCmpEzDu54OtHTV8GPWqqaQHMkFU9d06MYqRw3YY4eaCF3KxPYVzEvPNfnq0w1VvKbXb_1u4Bk0XhB8vBd-Va6Bt1KeJHAQTSeyDJipBeX-_DFsngm5CyL3Mfe2HROqSZCYOoZqoYOkDjqPYn37_9-yaswD5fRjNQyV-Q2S-q4xEG-U-htIFfSBPrbZAw5kUt7AqHk&q=DogIsrael+-+%D0%B4%D1%80%D0%B5%D1%81%D1%81%D0%B8%D1%80%D0%BE%D0%B2%D0%BA%D0%B0+%D0%B8+%D0%BF%D0%B5%D1%80%D0%B5%D0%B4%D0%B5%D1%80%D0%B6%D0%BA%D0%B0+%28%D0%B4%D0%BE%D0%BC%D0%B0%D1%88%D0%BD%D0%B8%D0%B9+%D0%BF%D0%B0%D0%BD%D1%81%D0%B8%D0%BE%D0%BD%29+%D0%B4%D0%BB%D1%8F+%D1%81%D0%BE%D0%B1%D0%B0%D0%BA.+%D0%A5%D0%B0%D0%B9%D1%84%D0%B0,+%D0%9A%D1%80%D0%B0%D0%B9%D0%BE%D1%82,+%D0%A1%D0%B5%D0%B2%D0%B5%D1%80+Reviews")
    },
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
