package com.dodisrael.clavim

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dodisrael.clavim.ui.theme.ClavimTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClavimTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    var screen by remember { mutableStateOf(Screen.MAIN) }
    var reminderType by remember { mutableStateOf(1) }
    var webViewUrl by remember { mutableStateOf("") }
    var webViewReturnScreen by remember { mutableStateOf(Screen.MAIN) }
    var boardingInitialAction by remember { mutableStateOf<String?>(null) }
    var boardingReturnScreen by remember { mutableStateOf(Screen.MAIN) }

    fun openWebView(url: String, title: String, returnTo: Screen) {
        webViewUrl = url
        webViewReturnScreen = returnTo
        screen = Screen.WEB_VIEW
    }

    BackHandler(enabled = screen != Screen.MAIN) {
        screen = when (screen) {
            Screen.EXCHANGE_RATES, Screen.ADDRESSES, Screen.DATA_ENTRIES -> Screen.INFO
            Screen.TELEGRAM_FOSTERING -> Screen.TELEGRAM
            Screen.WHATSAPP_REMINDER, Screen.WHATSAPP_HOMEWORK, Screen.WHATSAPP_DOG_MESSAGE,
            Screen.WHATSAPP_TRANSLATION, Screen.WHATSAPP_PICKUP -> Screen.WHATSAPP
            Screen.WEB_VIEW -> webViewReturnScreen
            Screen.SETTINGS -> Screen.MAIN
            Screen.FOSTERING_MENU -> Screen.MAIN
            Screen.BOARDING_ASSISTANT -> { boardingInitialAction = null; boardingReturnScreen }
            else -> Screen.MAIN
        }
    }

    when (screen) {
        Screen.SHEETS              -> SheetsMenuScreen(onBack = { screen = Screen.MAIN })
        Screen.INFO                -> InfoMenuScreen(
            onBack = { screen = Screen.MAIN },
            onExchangeRatesClick = { screen = Screen.EXCHANGE_RATES },
            onAddressesClick = { screen = Screen.ADDRESSES },
            onDataEntriesClick = { screen = Screen.DATA_ENTRIES },
            onWebViewClick = { url, title -> openWebView(url, title, Screen.INFO) }
        )
        Screen.ADDRESSES           -> AddressesScreen(onBack = { screen = Screen.INFO })
        Screen.DATA_ENTRIES        -> DataEntriesScreen(onBack = { screen = Screen.INFO })
        Screen.EXCHANGE_RATES      -> ExchangeRatesScreen(onBack = { screen = Screen.INFO })
        Screen.TELEGRAM_FOSTERING  -> TelegramFosteringScreen(onBack = { screen = Screen.TELEGRAM })
        Screen.ADVERTISING         -> AdvertisingMenuScreen(
            onBack = { screen = Screen.MAIN },
            onWebViewClick = { url, title -> openWebView(url, title, Screen.ADVERTISING) }
        )
        Screen.WHATSAPP            -> WhatsAppMenuScreen(
            onBack = { screen = Screen.MAIN },
            onFirstMessageClick = { screen = Screen.WHATSAPP_FIRST_MESSAGE },
            onReminderClick = { type -> reminderType = type; screen = Screen.WHATSAPP_REMINDER },
            onHomeworkClick = { screen = Screen.WHATSAPP_HOMEWORK },
            onDogMessageClick = { screen = Screen.WHATSAPP_DOG_MESSAGE },
            onTranslationClick = { screen = Screen.WHATSAPP_TRANSLATION },
            onPickupClick = { screen = Screen.WHATSAPP_PICKUP }
        )
        Screen.WHATSAPP_FIRST_MESSAGE -> WhatsAppFirstMessageScreen(
            onBack = { screen = Screen.WHATSAPP }
        )
        Screen.WHATSAPP_REMINDER   -> WhatsAppReminderScreen(
            reminderType = reminderType,
            onBack = { screen = Screen.WHATSAPP }
        )
        Screen.WHATSAPP_HOMEWORK   -> WhatsAppHomeworkScreen(
            onBack = { screen = Screen.WHATSAPP }
        )
        Screen.WHATSAPP_DOG_MESSAGE -> WhatsAppDogMessageScreen(
            onBack = { screen = Screen.WHATSAPP }
        )
        Screen.WHATSAPP_TRANSLATION -> WhatsAppTranslationScreen(
            onBack = { screen = Screen.WHATSAPP }
        )
        Screen.WHATSAPP_PICKUP     -> WhatsAppPickupScreen(
            onBack = { screen = Screen.WHATSAPP }
        )
        Screen.WEB_VIEW            -> WebViewScreen(
            url = webViewUrl,
            onBack = { screen = webViewReturnScreen }
        )
        Screen.TELEGRAM            -> TelegramMenuScreen(
            onBack = { screen = Screen.MAIN },
            onTelegramFosteringClick = { screen = Screen.TELEGRAM_FOSTERING }
        )
        Screen.SETTINGS            -> SettingsScreen(onBack = { screen = Screen.MAIN })
        Screen.MAIN                -> MainMenuScreen(
            onSheetsClick              = { screen = Screen.SHEETS },
            onInfoClick                = { screen = Screen.INFO },
            onAdvertisingClick         = { screen = Screen.ADVERTISING },
            onWhatsAppClick            = { screen = Screen.WHATSAPP },
            onTelegramClick            = { screen = Screen.TELEGRAM },
            onWebViewClick             = { url, title -> openWebView(url, title, Screen.MAIN) },
            onBoardingAssistantClick   = { boardingReturnScreen = Screen.MAIN; screen = Screen.BOARDING_ASSISTANT },
            onFosteringClick           = { screen = Screen.FOSTERING_MENU },
            onSettingsClick            = { screen = Screen.SETTINGS }
        )
        Screen.FOSTERING_MENU      -> FosteringMenuScreen(
            onBack = { screen = Screen.MAIN },
            onNewDogClick = {
                boardingInitialAction = "new"; boardingReturnScreen = Screen.FOSTERING_MENU
                screen = Screen.BOARDING_ASSISTANT
            },
            onExistingDogClick = {
                boardingInitialAction = "add"; boardingReturnScreen = Screen.FOSTERING_MENU
                screen = Screen.BOARDING_ASSISTANT
            },
            onDeleteDogClick = {
                boardingInitialAction = "delete"; boardingReturnScreen = Screen.FOSTERING_MENU
                screen = Screen.BOARDING_ASSISTANT
            }
        )
        Screen.BOARDING_ASSISTANT  -> BoardingAssistantScreen(
            onBack = { boardingInitialAction = null; screen = boardingReturnScreen },
            onTelegramFosteringClick = { screen = Screen.TELEGRAM_FOSTERING },
            initialFormAction = boardingInitialAction
        )
    }
}
