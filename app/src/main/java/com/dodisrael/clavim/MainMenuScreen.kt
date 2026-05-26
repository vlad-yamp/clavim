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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.School
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
    onWhatsAppClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onWebViewClick: (url: String, title: String) -> Unit,
    onBoardingAssistantClick: () -> Unit,
    onFosteringClick: () -> Unit,
    onTrainingClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val items = remember {
        buildMainMenuItems(
            onSheetsClick, onInfoClick, onAdvertisingClick, onWhatsAppClick,
            onTelegramClick, onWebViewClick, onBoardingAssistantClick, onFosteringClick, onTrainingClick
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Clavim",
            subtitle = "Дрессировка и передержка собак · Израиль",
            showBack = false,
            onSettingsClick = onSettingsClick
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
    onWhatsAppClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onWebViewClick: (url: String, title: String) -> Unit,
    onBoardingAssistantClick: () -> Unit,
    onFosteringClick: () -> Unit,
    onTrainingClick: () -> Unit
): List<MenuItem> = listOf(
    MenuItem("Голосовой\nассистент", Icons.Default.Mic,      Color(0xFF5E35B1)) { _ -> onBoardingAssistantClick() },
    MenuItem("Реклама",             Icons.Default.Campaign,  Color(0xFF4285F4)) { _ -> onAdvertisingClick() },
    MenuItem("Передержка",          Icons.Default.Pets,      Color(0xFF6D4C41)) { _ -> onFosteringClick() },
    MenuItem("Дрессировка",         Icons.Default.School,    Color(0xFF00695C)) { _ -> onTrainingClick() },
    MenuItem("WhatsApp",            Icons.Default.Chat,      Color(0xFF25D366)) { _ -> onWhatsAppClick() },
    MenuItem("Телеграм",            Icons.AutoMirrored.Filled.Send, Color(0xFF039BE5)) { _ -> onTelegramClick() },
    MenuItem("Таблицы\nGoogle",     Icons.Default.TableChart, Color(0xFF1A8754)) { _ -> onSheetsClick() },
    MenuItem("Информация",          Icons.Default.Info,      Color(0xFF0277BD)) { _ -> onInfoClick() }
)
