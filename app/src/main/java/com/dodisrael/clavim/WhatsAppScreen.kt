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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun WhatsAppMenuScreen(
    onBack: () -> Unit,
    onFirstMessageClick: () -> Unit,
    onReminderClick: (Int) -> Unit,
    onHomeworkClick: () -> Unit,
    onDogMessageClick: () -> Unit,
    onTranslationClick: () -> Unit
) {
    val context = LocalContext.current
    val items = remember { buildWhatsAppMenuItems(onFirstMessageClick, onReminderClick, onHomeworkClick, onDogMessageClick, onTranslationClick) }
    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "WhatsApp", subtitle = "Шаблоны сообщений", showBack = true, onBack = onBack)
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

private fun buildWhatsAppMenuItems(
    onFirstMessageClick: () -> Unit,
    onReminderClick: (Int) -> Unit,
    onHomeworkClick: () -> Unit,
    onDogMessageClick: () -> Unit,
    onTranslationClick: () -> Unit
): List<MenuItem> = listOf(
    MenuItem("Первое сообщение\nдля передержки", Icons.AutoMirrored.Filled.Chat, Color(0xFF1565C0)) { _ ->
        onFirstMessageClick()
    },
    MenuItem("Отчёт\nо передержке", Icons.Default.Pets, Color(0xFFFF6F00)) { _ ->
        onDogMessageClick()
    },
    MenuItem("Напоминание\nо передержке 1", Icons.Default.Notifications, Color(0xFF25D366)) { _ ->
        onReminderClick(1)
    },
    MenuItem("Напоминание\nо передержке 2", Icons.Default.Notifications, Color(0xFF128C7E)) { _ ->
        onReminderClick(2)
    },
    MenuItem("Просьба\nоставить отзыв", Icons.Default.StarRate, Color(0xFF2E7D32)) { _ ->
        onReminderClick(5)
    },
    MenuItem("Напоминание о\nпервом занятии", Icons.Default.School, Color(0xFF6A1B9A)) { _ ->
        onReminderClick(3)
    },
    MenuItem("Инструкции\nдля первого занятия", Icons.Default.Description, Color(0xFFE65100)) { _ ->
        onReminderClick(4)
    },
    MenuItem("Задания\nпосле занятия", Icons.Default.Assignment, Color(0xFF00897B)) { _ ->
        onHomeworkClick()
    },
    MenuItem("Переписка\nс переводом", Icons.Default.Translate, Color(0xFF00796B)) { _ ->
        onTranslationClick()
    }
)
