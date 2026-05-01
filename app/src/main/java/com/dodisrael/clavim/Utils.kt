package com.dodisrael.clavim

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

internal fun Context.tryLaunchApp(packageName: String): Boolean {
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
    startActivity(intent)
    return true
}

internal fun isAppInstalled(context: Context, packageName: String): Boolean =
    try { context.packageManager.getPackageInfo(packageName, 0); true }
    catch (_: PackageManager.NameNotFoundException) { false }

internal fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMMM", Locale("ru")).format(Date(millis))

internal fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

internal suspend fun readContacts(context: Context): List<WhatsAppContact> =
    withContext(Dispatchers.IO) {
        val list = mutableListOf<WhatsAppContact>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        ) ?: return@withContext list
        cursor.use {
            val nameCol  = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneCol = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name  = it.getString(nameCol)?.takeIf { n -> n.isNotBlank() } ?: continue
                val phone = it.getString(phoneCol)?.replace(Regex("[^+\\d]"), "") ?: continue
                if (phone.length >= 7) list.add(WhatsAppContact(name, phone))
            }
        }
        list.distinctBy { it.phone }.sortedBy { it.name }
    }

internal fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Доброе утро"
        hour < 18 -> "Добрый день"
        else      -> "Добрый вечер"
    }
}
