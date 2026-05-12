package com.dodisrael.clavim

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen {
    MAIN, SHEETS, INFO, EXCHANGE_RATES, TELEGRAM_FOSTERING,
    ADVERTISING, WHATSAPP, WHATSAPP_REMINDER, OUR_DATA,
    WHATSAPP_HOMEWORK, WHATSAPP_DOG_MESSAGE, WHATSAPP_FIRST_MESSAGE,
    WHATSAPP_TRANSLATION, WHATSAPP_PICKUP, BOARDING_ASSISTANT, WEB_VIEW, TELEGRAM, SETTINGS
}

data class MenuItem(
    val title: String,
    val icon: ImageVector,
    val iconBgColor: Color,
    val onClick: (Context) -> Unit
)

data class RateHistory(
    val labels: List<String>,
    val usdRub: List<Double>,
    val usdIls: List<Double>,
    val ilsRub: List<Double>
)

data class FosteringPost(val photoUrl: String, val caption: String)
data class WhatsAppContact(val name: String, val phone: String)

data class TranslationMessage(
    val isIncoming: Boolean,
    val originalText: String,
    val translatedText: String
)

sealed class FosteringState {
    object Idle : FosteringState()
    object Loading : FosteringState()
    data class Success(val posts: List<FosteringPost>) : FosteringState()
    data class Error(val message: String) : FosteringState()
}

sealed class RatesState {
    object Loading : RatesState()
    data class Success(
        val usdRub: Double,
        val usdIls: Double,
        val ilsRub: Double,
        val history: RateHistory
    ) : RatesState()
    data class Error(val message: String) : RatesState()
}
