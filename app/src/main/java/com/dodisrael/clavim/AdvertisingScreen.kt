package com.dodisrael.clavim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AdvertisingMenuScreen(onBack: () -> Unit, onWebViewClick: (url: String, title: String) -> Unit) {
    val context = LocalContext.current
    val items = remember { buildAdvertisingMenuItems(onWebViewClick) }
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

private fun buildAdvertisingMenuItems(onWebViewClick: (url: String, title: String) -> Unit): List<MenuItem> = listOf(
    MenuItem("Поиск в\nинтернете", Icons.Default.Search, Color(0xFF0F9D58)) { _ ->
        val query = Uri.encode("Дрессировка собак Хайфа")
        onWebViewClick("https://www.google.com/search?q=$query", "Поиск в интернете")
    },
    MenuItem("Google\nРеклама", Icons.Default.Campaign, Color(0xFF4285F4)) { ctx ->
        val launched = ctx.tryLaunchApp("com.google.android.apps.adwords")
        if (!launched) ctx.openUrl("https://ads.google.com")
    },
    MenuItem("Meta Ads", Icons.Default.Insights, Color(0xFF1877F2)) { ctx ->
        val launched = ctx.tryLaunchApp("com.facebook.adsmanager")
        if (!launched) ctx.openUrl("https://www.facebook.com/adsmanager")
    },
    MenuItem("Тильда", Icons.Default.BarChart, Color(0xFF00897B)) { ctx ->
        ctx.openUrl("https://stats.tilda.cc/projects/statistics/?projectid=7284816&from=sitesettings")
    },
    MenuItem("Места\nна передержке", Icons.Default.Bed, Color(0xFF37474F)) { ctx ->
        ctx.openUrl("https://docs.google.com/spreadsheets/d/e/2PACX-1vQid7xTSYIm4dvkLqAP9ewvl7Pq41CFAXDDGldLdVCSQEtpK8KSXTa6f-_MHP4Zt2ezLwVV-Se5BJoG/pubhtml?authuser=0&widget=true&headers=false#gid=0?&single=true")
    },
    MenuItem("Отзывы\nклиентов", Icons.Default.RateReview, Color(0xFFF57F17)) { _ ->
        onWebViewClick(
            "https://www.google.com/search?newwindow=1&authuser=1&sxsrf=AHTn8zrbLwwF9wGEA5poSjJh8dL3L95WuQ:1744786758568&si=APYL9bs7Hg2KMLB-4tSoTdxuOx8BdRvHbByC_AuVpNyh0x2KzYKIj8l0ArlFedGpiIPiM2wpnktdq0_hR9JLB-xTz_vgQx7xk0mKoPgtE06bMRpG7JD0lxcEoXkR3fen8REzMIFCmpEzDu54OtHTV8GPWqqaQHMkFU9d06MYqRw3YY4eaCF3KxPYVzEvPNfnq0w1VvKbXb_1u4Bk0XhB8vBd-Va6Bt1KeJHAQTSeyDJipBeX-_DFsngm5CyL3Mfe2HROqSZCYOoZqoYOkDjqPYn37_9-yaswD5fRjNQyV-Q2S-q4xEG-U-htIFfSBPrbZAw5kUt7AqHk&q=DogIsrael+-+%D0%B4%D1%80%D0%B5%D1%81%D1%81%D0%B8%D1%80%D0%BE%D0%B2%D0%BA%D0%B0+%D0%B8+%D0%BF%D0%B5%D1%80%D0%B5%D0%B4%D0%B5%D1%80%D0%B6%D0%BA%D0%B0+%28%D0%B4%D0%BE%D0%BC%D0%B0%D1%88%D0%BD%D0%B9+%D0%BF%D0%B0%D0%BD%D1%81%D0%B8%D0%BE%D0%BD%29+%D0%B4%D0%BB%D1%8F+%D1%81%D0%BE%D0%B1%D0%B0%D0%BA.+%D0%A5%D0%B0%D0%B9%D1%84%D0%B0,+%D0%9A%D1%80%D0%B0%D0%B9%D0%BE%D1%82,+%D0%A1%D0%B5%D0%B2%D0%B5%D1%80+Reviews",
            "Отзывы клиентов"
        )
    },
    MenuItem("Фото\nпередержки", Icons.Default.PhotoLibrary, Color(0xFF558B2F)) { ctx ->
        ctx.openUrl("https://docs.google.com/document/d/e/2PACX-1vSugkyexuBk6HAV1pyH2SiOpkdiIH9M7y3e1e75zFiNR3MIB1V9adrwgAyNgmLGHjf-SrJIyR8Ac7vk/pub?widget=true&headers=false#gid=0?&single=true")
    }
)
