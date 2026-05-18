package com.dodisrael.clavim

import android.content.ActivityNotFoundException
import android.content.Context
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SheetsMenuScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val items = remember { buildSheetsMenuItems() }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Таблицы Google",
            subtitle = "Данные по дрессировке и финансам",
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

private fun buildSheetsMenuItems(): List<MenuItem> = listOf(
    MenuItem("Передержка", Icons.Default.Bed, Color(0xFF00695C)) { ctx ->
        ctx.openInSheets("https://docs.google.com/spreadsheets/d/1P44f7Fdk8_TiB6Rn67YLNbfnVHK7TIThMLsDiN6sgAs/edit?gid=0#gid=0")
    },
    MenuItem("Наличие мест", Icons.Default.EventAvailable, Color(0xFF00796B)) { ctx ->
        ctx.openInSheets("https://docs.google.com/spreadsheets/d/1A1XfeZiXSWQghndLuCrW6bOgenVGEfzuEzvICak1j-E/edit?gid=0#gid=0")
    },
    MenuItem("Доходы–Расходы", Icons.Default.AccountBalanceWallet, Color(0xFF2E7D32)) { ctx ->
        ctx.openInSheets("https://docs.google.com/spreadsheets/d/1dCrUUYrWhvaAyUO4nk3yhSgFGklSd4ZUF9f36PbsaLw/edit?gid=0#gid=0")
    },
    MenuItem("Занятия\nс собаками", Icons.Default.Schedule, Color(0xFF283593)) { ctx ->
        ctx.openInSheets("https://docs.google.com/spreadsheets/d/1k7usk6ZFkPL7x6-CFFfAr87kQvRkI9TBCZZqJFej0X4/edit?gid=0#gid=0")
    },
    MenuItem("Баланс", Icons.Default.AccountBalance, Color(0xFF6A1B9A)) { ctx ->
        ctx.openInSheets("https://docs.google.com/spreadsheets/d/1bF33tKU_BC7a-lSjT2FtCrgWqBFXzQcnDSI4Hts_Pds/edit?gid=951715807#gid=951715807")
    },
    MenuItem("Clavim", Icons.Default.GridOn, Color(0xFFAD1457)) { ctx ->
        ctx.openInSheets("https://docs.google.com/spreadsheets/d/1zoXmop2UWsBx4792N8J42CcpDHXAQAQEtr3WMKV2GBQ/edit?gid=1280945559#gid=1280945559")
    }
)

fun Context.openInSheets(url: String) {
    val urlWithAccount = Uri.parse(url).buildUpon()
        .appendQueryParameter("authuser", "vlad.yamp@gmail.com")
        .build().toString()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlWithAccount)).apply {
        setPackage("com.google.android.apps.docs")
    }
    try { startActivity(intent) } catch (_: ActivityNotFoundException) { openUrl(urlWithAccount) }
}
