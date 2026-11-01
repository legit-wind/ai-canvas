package com.aicanvas.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.aicanvas.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { com.aicanvas.app.phone.RefreshWorker.schedule(applicationContext) }
        // Always a white screen, so always dark status/navigation bar icons.
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        if (savedInstanceState == null) handle(intent)
        setContent { AppRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onPause() {
        super.onPause()
        com.aicanvas.app.phone.Foreground.visible = false
    }

    override fun onResume() {
        super.onResume()
        com.aicanvas.app.phone.Foreground.visible = true
        vm.onResume()
    }

    /** Launcher shortcuts ("Talk", "Type") and text or photos shared from other apps. */
    private fun handle(intent: Intent?) {
        when (intent?.action) {
            "com.aicanvas.app.TALK" -> vm.shortcut(talk = true)
            "com.aicanvas.app.TYPE" -> vm.shortcut(talk = false)
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val image = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
                vm.receiveShare(text, listOfNotNull(image))
            }
        }
    }
}
