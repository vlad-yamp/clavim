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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun TelegramMenuScreen(onBack: () -> Unit, onTelegramFosteringClick: () -> Unit) {
    val context = LocalContext.current
    val items = remember { buildTelegramMenuItems(onTelegramFosteringClick) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "Телеграм", subtitle = "Каналы и боты", showBack = true, onBack = onBack)
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

private fun buildTelegramMenuItems(onTelegramFosteringClick: () -> Unit): List<MenuItem> = listOf(
    MenuItem("Телеграм-канал", Icons.AutoMirrored.Filled.Send, Color(0xFF0277BD)) { ctx ->
        val url = "https://t.me/DogIsraelTsafon"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage("org.telegram.messenger") }
        try { ctx.startActivity(intent) } catch (_: ActivityNotFoundException) { ctx.openUrl(url) }
    },
    MenuItem("Фото из\nTelegram", Icons.Default.Slideshow, Color(0xFF039BE5)) { _ ->
        onTelegramFosteringClick()
    },
    MenuItem("DogIsrael\nReminder", Icons.Default.NotificationsActive, Color(0xFF1565C0)) { ctx ->
        val url = "https://t.me/dogisrael_reminder_bot"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage("org.telegram.messenger") }
        try { ctx.startActivity(intent) } catch (_: ActivityNotFoundException) { ctx.openUrl(url) }
    }
)
