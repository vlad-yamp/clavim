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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun FosteringMenuScreen(
    onBack: () -> Unit,
    onNewDogClick: () -> Unit,
    onExistingDogClick: () -> Unit,
    onDeleteDogClick: () -> Unit,
    onClientsClick: () -> Unit,
    onNotesClick: () -> Unit,
    onStatsClick: () -> Unit
) {
    val context = LocalContext.current
    val items = remember {
        listOf(
            MenuItem("Новая\nсобака", Icons.Default.Add, Color(0xFF388E3C)) { _ -> onNewDogClick() },
            MenuItem("Повторная\nпередержка", Icons.Default.Pets, Color(0xFF1565C0)) { _ -> onExistingDogClick() },
            MenuItem("Удаление\nиз передержки", Icons.Default.Delete, Color(0xFFD32F2F)) { _ -> onDeleteDogClick() },
            MenuItem("Клиенты", Icons.Default.People, Color(0xFF7B1FA2)) { _ -> onClientsClick() },
            MenuItem("Заметки", Icons.Default.Note, Color(0xFF00695C)) { _ -> onNotesClick() },
            MenuItem("Статистика", Icons.Default.BarChart, Color(0xFF0D47A1)) { _ -> onStatsClick() }
        )
    }
    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "Передержка", subtitle = "Управление записями", showBack = true, onBack = onBack)
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
