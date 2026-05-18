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
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun InfoMenuScreen(
    onBack: () -> Unit,
    onExchangeRatesClick: () -> Unit,
    onAddressesClick: () -> Unit,
    onDataEntriesClick: () -> Unit,
    onWebViewClick: (url: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val items = remember { buildInfoMenuItems(onExchangeRatesClick, onAddressesClick, onDataEntriesClick, onWebViewClick) }

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
    onAddressesClick: () -> Unit,
    onDataEntriesClick: () -> Unit,
    onWebViewClick: (url: String, title: String) -> Unit
): List<MenuItem> = listOf(
    MenuItem("Курсы\nвалют", Icons.Default.CurrencyExchange, Color(0xFF00695C)) { _ ->
        onExchangeRatesClick()
    },
    MenuItem("Адреса", Icons.Default.LocationOn, Color(0xFFAD1457)) { _ ->
        onAddressesClick()
    },
    MenuItem("Наши\nданные", Icons.Default.Dataset, Color(0xFF1565C0)) { _ ->
        onDataEntriesClick()
    },
    MenuItem("Список\nпород", Icons.Default.Pets, Color(0xFF8D6E63)) { _ ->
        onWebViewClick("https://petstory.ru/knowledge/dogs/dog-breeds/", "Список пород")
    },
    MenuItem("Open AI", Icons.Default.Psychology, Color(0xFF10A37F)) { ctx ->
        ctx.openUrl("https://platform.openai.com/usage")
    }
)

