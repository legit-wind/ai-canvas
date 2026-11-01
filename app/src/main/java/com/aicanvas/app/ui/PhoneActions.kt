package com.aicanvas.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.FileProvider
import java.io.File

// Things a card can do on the phone: share an image, open a safe link.
/** Saves a picture of a card and opens the share sheet (WhatsApp, Instagram, …). */
internal fun shareImage(context: Context, bitmap: Bitmap) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "card.png")
    file.outputStream().use { bitmap.copy(Bitmap.Config.ARGB_8888, false).compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share card").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Buttons inside elements can open things on the phone: tel:, https:, geo:, alarm:HH:mm. Nothing else. */
internal fun openOnPhone(context: Context, uri: String) {
    val link = uri.trim()
    val intent = when (Links.kindOf(link)) {
        Links.Kind.ALARM -> {
            val (h, m) = Links.alarmTime(link) ?: return
            Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
        }
        Links.Kind.TEL -> Intent(Intent.ACTION_DIAL, Uri.parse(link))
        Links.Kind.WEB, Links.Kind.GEO -> Intent(Intent.ACTION_VIEW, Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE)
        null -> {
            Toast.makeText(context, "This card tried to open a link that isn't allowed", Toast.LENGTH_SHORT).show()
            return
        }
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can open that", Toast.LENGTH_SHORT).show()
    }
}

/** Reacts to taps (and swallows them) without becoming focusable or clickable by keyboard. */
internal fun Modifier.onTap(action: () -> Unit): Modifier = pointerInput(Unit) { detectTapGestures { action() } }
