package com.dodisrael.clavim

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TELEGRAM_BOT_TOKEN = "8537830827:AAH8qerFzDsN5smHWjb7pAIfj_3lcyoDqFY"
private val TELEGRAM_CHAT_IDS = listOf("382945139", "1306091284")

fun sendTelegramMessage(text: String) {
    for (chatId in TELEGRAM_CHAT_IDS) {
        try {
            val url = URL("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
            }
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }.toString().toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(body) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {}
    }
}
