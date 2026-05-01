package com.dodisrael.clavim

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
