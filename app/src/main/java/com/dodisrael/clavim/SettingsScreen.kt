package com.dodisrael.clavim

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("clavim_prefs", Context.MODE_PRIVATE) }

    var apiKey by remember { mutableStateOf(prefs.getString("openai_api_key", "") ?: "") }
    var voiceAutoSpeak by remember { mutableStateOf(prefs.getBoolean("voice_auto_speak", true)) }
    var appsScriptUrl by remember { mutableStateOf(prefs.getString("apps_script_url", "") ?: "") }
    var keySaved by remember { mutableStateOf(false) }
    var scriptUrlSaved by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = "Настройки",
            subtitle = "Параметры приложения",
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
            // API key
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("API ключ ChatGPT (OpenAI)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "Используется для генерации сообщений и голосового ассистента.",
                        fontSize = 13.sp, color = Color(0xFF757575)
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; keySaved = false },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (keyVisible) "Скрыть" else "Показать"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            prefs.edit().putString("openai_api_key", apiKey.trim()).apply()
                            apiKey = apiKey.trim()
                            keySaved = true
                        },
                        enabled = apiKey.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                    ) {
                        Text(if (keySaved) "Сохранено ✓" else "Сохранить", color = Color.White)
                    }
                }
            }

            // Apps Script URL
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("URL Apps Script (запись в таблицу)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "Вставьте ссылку на развёрнутый Google Apps Script Web App для записи на передержку.",
                        fontSize = 13.sp, color = Color(0xFF757575)
                    )
                    OutlinedTextField(
                        value = appsScriptUrl,
                        onValueChange = { appsScriptUrl = it; scriptUrlSaved = false },
                        placeholder = { Text("https://script.google.com/macros/s/…/exec") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            prefs.edit().putString("apps_script_url", appsScriptUrl.trim()).apply()
                            appsScriptUrl = appsScriptUrl.trim()
                            scriptUrlSaved = true
                        },
                        enabled = appsScriptUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                    ) {
                        Text(if (scriptUrlSaved) "Сохранено ✓" else "Сохранить", color = Color.White)
                    }
                }
            }

            // Voice auto-speak
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Голосовое сопровождение", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Озвучивать ответ ассистента автоматически",
                            fontSize = 13.sp, color = Color(0xFF757575)
                        )
                    }
                    Switch(
                        checked = voiceAutoSpeak,
                        onCheckedChange = {
                            voiceAutoSpeak = it
                            prefs.edit().putBoolean("voice_auto_speak", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF5E35B1), checkedTrackColor = Color(0xFFD1C4E9))
                    )
                }
            }
        }
    }
}
